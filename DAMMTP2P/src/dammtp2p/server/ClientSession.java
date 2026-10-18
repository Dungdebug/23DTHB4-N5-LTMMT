package dammtp2p.server;

import dammtp2p.core.protocol.MessageSerializer;
import dammtp2p.core.protocol.PacketHeader;
import dammtp2p.core.protocol.PacketType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-side representation of an individual connected client TCP control session (Port 8888).
 * Handles message framing, thread-safe writing, and reader loop execution.
 */
public class ClientSession {

    private final String sessionId;
    private final Socket socket;
    private final ServerCore server;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Object writeLock = new Object();

    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean handshakeCompleted = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String username = null;
    private volatile String clientDisplayName = "Unknown";
    private final long connectedTime = System.currentTimeMillis();

    private final Set<String> joinedRooms = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ClientSession(String sessionId, Socket socket, ServerCore server) throws IOException {
        this.sessionId = sessionId;
        this.socket = socket;
        this.server = server;
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);

        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void runReaderLoop() {
        try {
            while (!closed.get() && !socket.isClosed()) {
                PacketHeader header;
                try {
                    header = MessageSerializer.readHeader(in);
                } catch (EOFException | SocketException e) {
                    break; // Client dropped connection
                }

                byte[] payloadBytes = MessageSerializer.readPayload(in, header.getPayloadLength());
                String payload = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);

                server.handleClientPacket(this, header.getPacketType(), payload);
            }
        } catch (Exception e) {
            if (!closed.get()) {
                server.logEvent(String.format("[SESSION ERROR] %s: %s", getClientDesc(), e.getMessage()));
            }
        } finally {
            close();
        }
    }

    public void sendPacket(PacketType type, String jsonPayload) throws IOException {
        if (closed.get() || socket.isClosed()) {
            return;
        }
        byte[] payloadBytes = jsonPayload != null ? jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        PacketHeader header = new PacketHeader(type, payloadBytes.length);
        synchronized (writeLock) {
            header.writeTo(out);
            if (payloadBytes.length > 0) {
                out.write(payloadBytes);
            }
            out.flush();
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {}
        server.getSessionManager().removeSession(this);
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public void setAuthenticated(String username) {
        this.username = username;
        this.authenticated.set(true);
    }

    public String getUsername() {
        return username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Socket getSocket() {
        return socket;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public Set<String> getJoinedRooms() {
        return joinedRooms;
    }

    public void addRoom(String roomId) {
        joinedRooms.add(roomId);
    }

    public void removeRoom(String roomId) {
        joinedRooms.remove(roomId);
    }

    public boolean isHandshakeCompleted() {
        return handshakeCompleted.get();
    }

    public void setHandshakeCompleted(boolean completed) {
        this.handshakeCompleted.set(completed);
    }

    public String getClientDisplayName() {
        return clientDisplayName;
    }

    public void setClientDisplayName(String clientDisplayName) {
        this.clientDisplayName = (clientDisplayName != null && !clientDisplayName.trim().isEmpty())
                ? clientDisplayName.trim()
                : "Client";
    }

    public String getClientDesc() {
        if (username != null) {
            return "@" + username + " (" + socket.getRemoteSocketAddress() + ")";
        }
        if (clientDisplayName != null && !clientDisplayName.equals("Unknown")) {
            return clientDisplayName + " (" + socket.getRemoteSocketAddress() + ")";
        }
        return "Anon-" + sessionId.substring(0, Math.min(8, sessionId.length())) + " (" + socket.getRemoteSocketAddress() + ")";
    }
}