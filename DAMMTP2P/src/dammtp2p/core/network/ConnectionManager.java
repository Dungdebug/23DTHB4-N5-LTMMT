package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import java.io.IOException;

/**
 * Unified coordination interface for the P2P networking layer.
 * Manages the dual roles (concurrent PeerListener and PeerConnector),
 * handles connection state, and dispatches network events to registered listeners.
 */
public interface ConnectionManager {

    /**
     * Initializes and binds the local ServerSocket listener.
     *
     * @param port TCP port to bind (default: 8888)
     * @throws IOException if binding fails
     */
    void startListener(int port) throws IOException;

    /**
     * Stops the local ServerSocket listener.
     */
    void stopListener();

    /**
     * Connects directly to a remote peer via ZeroTier or LAN.
     *
     * @param remoteIp   Target IP address
     * @param remotePort Target control port
     * @throws IOException on connection failure
     */
    void connectToPeer(String remoteIp, int remotePort) throws IOException;

    /**
     * Disconnects the currently active peer connection.
     */
    void disconnectPeer();

    /**
     * Returns true if a connection to a remote peer is active and authenticated.
     */
    boolean isConnected();

    /**
     * Returns true if the local listener is active and accepting connections.
     */
    boolean isListening();

    /**
     * Information about the local peer node (IP, ports, display name).
     */
    PeerInfo getLocalPeerInfo();

    /**
     * Information about the connected remote peer node, or null if disconnected.
     */
    PeerInfo getRemotePeerInfo();

    /**
     * Registers a listener for network state changes.
     */
    void addNetworkStateListener(NetworkStateListener listener);

    /**
     * Unregisters a listener for network state changes.
     */
    void removeNetworkStateListener(NetworkStateListener listener);
}
