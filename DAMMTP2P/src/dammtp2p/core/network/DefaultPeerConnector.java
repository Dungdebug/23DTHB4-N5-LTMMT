package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import dammtp2p.util.Constants;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Implementation of {@link PeerConnector} for establishing direct outgoing TCP connections
 * to remote peers over ZeroTier, local LAN, or loopback.
 */
public class DefaultPeerConnector implements PeerConnector {

    private final ConnectionManager connectionManager;

    public DefaultPeerConnector(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Creates and connects a raw TCP socket with timeout and options configured.
     *
     * @param remoteIp   Target IP address
     * @param remotePort Target TCP port
     * @return Open, connected Socket
     * @throws IOException on connection failure with actionable diagnostic message
     */
    public static Socket openSocket(String remoteIp, int remotePort) throws IOException {
        if (remoteIp == null || remoteIp.trim().isEmpty()) {
            throw new IllegalArgumentException("Remote IP address cannot be empty");
        }
        if (remotePort <= 0 || remotePort > 65535) {
            throw new IllegalArgumentException("Invalid port number: " + remotePort);
        }

        String targetHost = remoteIp.trim();

        // 1. Validate target host address
        InetAddress targetAddress;
        try {
            targetAddress = InetAddress.getByName(targetHost);
        } catch (UnknownHostException e) {
            throw new UnknownHostException(String.format(
                    "Target host '%s' is unknown or cannot be resolved. Please verify the IP address.", targetHost));
        }

        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);

            // Connect directly to peer with configured timeout
            socket.connect(new InetSocketAddress(targetAddress, remotePort), Constants.DEFAULT_CONNECT_TIMEOUT_MS);
            return socket;
        } catch (ConnectException e) {
            safeClose(socket);
            throw new ConnectException(String.format(
                    "Connection refused by %s:%d. Possible causes: peer application is not running, listener is inactive, or Windows Firewall is blocking the port.",
                    targetHost, remotePort));
        } catch (SocketTimeoutException e) {
            safeClose(socket);
            throw new SocketTimeoutException(String.format(
                    "Connection attempt to %s:%d timed out after %d ms. Possible causes: peer is offline, ZeroTier network is disconnected, or firewall is silently dropping packets.",
                    targetHost, remotePort, Constants.DEFAULT_CONNECT_TIMEOUT_MS));
        } catch (IOException e) {
            safeClose(socket);
            throw new IOException(String.format(
                    "Failed to connect to %s:%d: %s", targetHost, remotePort, e.getMessage()), e);
        }
    }

    @Override
    public void connect(String remoteIp, int remotePort) throws IOException {
        if (connectionManager != null) {
            connectionManager.connectToPeer(remoteIp, remotePort);
        } else {
            Socket s = openSocket(remoteIp, remotePort);
            safeClose(s);
        }
    }

    @Override
    public void disconnect() {
        if (connectionManager != null) {
            connectionManager.disconnectPeer();
        }
    }

    @Override
    public boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    @Override
    public PeerInfo getConnectedPeer() {
        return connectionManager != null ? connectionManager.getRemotePeerInfo() : null;
    }

    private static void safeClose(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
