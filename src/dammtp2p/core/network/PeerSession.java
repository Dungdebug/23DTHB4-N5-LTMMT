package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.protocol.MessageSerializer;
import dammtp2p.core.protocol.PacketHeader;
import dammtp2p.core.protocol.PacketType;
import dammtp2p.core.protocol.ProtocolConstants;
import dammtp2p.util.Constants;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages an active, bidirectional P2P control connection over a TCP Socket.
 * Responsible for socket configuration, application handshake, packet framing,
 * reader loop dispatching, and graceful disconnection.
 */
public class PeerSession {

    private final Socket socket;
    private final boolean initiator;
    private final PeerInfo localPeerInfo;
    private final PeerSessionListener listener;
    private final ExecutorService executor;

    private final DataInputStream in;
    private final DataOutputStream out;
    private final Object writeLock = new Object();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private volatile PeerInfo remotePeer;

    public PeerSession(Socket socket, boolean initiator, PeerInfo localPeerInfo,
                       PeerSessionListener listener, ExecutorService executor) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.initiator = initiator;
        this.localPeerInfo = Objects.requireNonNull(localPeerInfo, "localPeerInfo must not be null");
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");

        // 1. Socket configuration for low-latency control messages and stability
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
        this.socket.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);

        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /**
     * Executes the bidirectional application handshake.
     * Must be called immediately after TCP socket connection.
     *
     * @throws IOException on handshake failure, timeout, or protocol incompatibility
     */
    public void performHandshake() throws IOException {
        HandshakePayload localPayload = new HandshakePayload(
                localPeerInfo.getDisplayName(),
                localPeerInfo.getControlPort(),
                localPeerInfo.getFilePort()
        );

        String remoteIp = socket.getInetAddress().getHostAddress();

        if (initiator) {
            // Initiator (Client): Send HELLO (HANDSHAKE_REQ), then await HELLO_ACK (HANDSHAKE_ACK)
            try {
                sendPacket(PacketType.HANDSHAKE_REQ, localPayload.toJson());

                PacketHeader ackHeader = MessageSerializer.readHeader(in);
                if (ackHeader.getPacketType() != PacketType.HANDSHAKE_ACK) {
                    throw new IncompatibleProtocolException(String.format(
                            "Handshake failed: expected HANDSHAKE_ACK (0x%02X), received 0x%02X",
                            PacketType.HANDSHAKE_ACK.getCode(), ackHeader.getPacketType().getCode()));
                }

                String ackJson = MessageSerializer.readUtf8Payload(in, ackHeader.getPayloadLength());
                HandshakePayload remotePayload = HandshakePayload.fromJson(ackJson);
                remotePayload.validate();

                this.remotePeer = new PeerInfo(
                        remoteIp,
                        remotePayload.getControlPort(),
                        remotePayload.getFilePort(),
                        remotePayload.getDisplayName()
                );
            } catch (SocketTimeoutException e) {
                throw new SocketTimeoutException("Timed out waiting for handshake response from " + remoteIp);
            }
        } else {
            // Responder (Server/Listener): Await HELLO (HANDSHAKE_REQ), validate, then reply HELLO_ACK
            try {
                PacketHeader reqHeader = MessageSerializer.readHeader(in);
                if (reqHeader.getPacketType() != PacketType.HANDSHAKE_REQ) {
                    throw new IncompatibleProtocolException(String.format(
                            "Handshake failed: expected HANDSHAKE_REQ (0x%02X), received 0x%02X",
                            PacketType.HANDSHAKE_REQ.getCode(), reqHeader.getPacketType().getCode()));
                }

                String reqJson = MessageSerializer.readUtf8Payload(in, reqHeader.getPayloadLength());
                HandshakePayload remotePayload = HandshakePayload.fromJson(reqJson);

                try {
                    remotePayload.validate();
                } catch (IncompatibleProtocolException e) {
                    // Notify peer of rejection before closing
                    try {
                        sendPacket(PacketType.DISCONNECT, "Handshake rejected: " + e.getMessage());
                    } catch (IOException ignored) {}
                    throw e;
                }

                this.remotePeer = new PeerInfo(
                        remoteIp,
                        remotePayload.getControlPort(),
                        remotePayload.getFilePort(),
                        remotePayload.getDisplayName()
                );

                sendPacket(PacketType.HANDSHAKE_ACK, localPayload.toJson());
            } catch (SocketTimeoutException e) {
                throw new SocketTimeoutException("Timed out waiting for handshake request from " + remoteIp);
            }
        }

        // Handshake verified! Remove temporary handshake read timeout
        this.socket.setSoTimeout(0);
        this.connected.set(true);

        listener.onSessionConnected(this, remotePeer);
    }

    /**
     * Starts the background packet reader loop.
     */
    public void startReaderLoop() {
        executor.submit(this::runReaderLoop);
    }

    private void runReaderLoop() {
        String peerDesc = remotePeer != null ? remotePeer.toString() : socket.getRemoteSocketAddress().toString();
        String disconnectReason = "Connection closed normally";

        try {
            while (connected.get() && !socket.isClosed()) {
                PacketHeader header;
                try {
                    header = MessageSerializer.readHeader(in);
                } catch (EOFException | SocketException e) {
                    // Peer dropped or closed socket
                    disconnectReason = "Remote peer disconnected";
                    break;
                }

                byte[] payload = MessageSerializer.readPayload(in, header.getPayloadLength());
                handleIncomingPacket(header, payload);
            }
        } catch (Exception e) {
            if (!closing.get()) {
                disconnectReason = "Network error: " + e.getMessage();
                listener.onSessionError(this, e);
            }
        } finally {
            closeSession(disconnectReason);
        }
    }

    private void handleIncomingPacket(PacketHeader header, byte[] payload) throws IOException {
        PacketType type = header.getPacketType();

        switch (type) {
            case HEARTBEAT:
                // Auto-reply to heartbeat probe
                sendPacket(PacketType.HEARTBEAT_ACK, "ACK");
                break;

            case HEARTBEAT_ACK:
                // Heartbeat ack received, connection is healthy
                break;

            case DISCONNECT:
                String reason = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
                closeSession("Peer disconnected gracefully: " + reason);
                break;

            default:
                // Forward other packets (Chat, File transfer negotiation) to the application listener
                listener.onSessionPacket(this, header, payload);
                break;
        }
    }

    /**
     * Sends a framed packet to the peer with thread safety.
     *
     * @param type        Packet type
     * @param utf8Payload String payload (UTF-8 encoded)
     * @throws IOException on socket write failure
     */
    public void sendPacket(PacketType type, String utf8Payload) throws IOException {
        byte[] payloadBytes = (utf8Payload != null && !utf8Payload.isEmpty())
                ? utf8Payload.getBytes(ProtocolConstants.PROTOCOL_CHARSET)
                : new byte[0];

        PacketHeader header = new PacketHeader(type, payloadBytes.length);

        synchronized (writeLock) {
            if (socket.isClosed()) {
                throw new SocketException("Socket is closed");
            }
            header.writeTo(out);
            if (payloadBytes.length > 0) {
                out.write(payloadBytes);
            }
            out.flush();
        }
    }

    /**
     * Closes the session gracefully, notifying the remote peer if possible.
     *
     * @param reason Reason for disconnection
     */
    public void close(String reason) {
        if (!closing.compareAndSet(false, true)) {
            return;
        }

        // Attempt to send a DISCONNECT packet before closing socket
        if (connected.get() && !socket.isClosed()) {
            try {
                sendPacket(PacketType.DISCONNECT, reason != null ? reason : "Disconnected");
            } catch (Exception ignored) {}
        }

        closeSession(reason);
    }

    private void closeSession(String reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {}

        listener.onSessionDisconnected(this, remotePeer, reason);
    }

    public boolean isConnected() {
        return connected.get() && !socket.isClosed();
    }

    public PeerInfo getRemotePeer() {
        return remotePeer;
    }

    public Socket getSocket() {
        return socket;
    }
}
