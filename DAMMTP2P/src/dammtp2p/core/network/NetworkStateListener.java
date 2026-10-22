package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;

/**
 * Observer interface for reacting to P2P network state transitions.
 * Implementations can update UI components or log connection status without
 * embedding any GUI or presentation logic into the network engine.
 */
public interface NetworkStateListener {

    /**
     * Fired when the local peer listener begins accepting incoming TCP connections.
     *
     * @param boundPort The TCP port on which the listener is bound (e.g. 8888)
     */
    void onListeningStarted(int boundPort);

    /**
     * Fired when the local peer listener stops accepting connections.
     */
    void onListeningStopped();

    /**
     * Fired when an outgoing connection attempt to a remote peer is initiated.
     *
     * @param remoteIp Target IP address
     * @param port     Target TCP control port
     */
    default void onConnecting(String remoteIp, int port) {}

    /**
     * Fired when a direct TCP connection with a remote peer is established
     * and the application handshake is successfully completed.
     *
     * @param peer Metadata of the connected peer
     */
    void onConnected(PeerInfo peer);

    /**
     * Fired when an outgoing or incoming connection attempt fails.
     *
     * @param remoteIp Target IP address
     * @param port     Target TCP control port
     * @param reason   Human-readable explanation of why the connection failed
     */
    default void onConnectionFailed(String remoteIp, int port, String reason) {}

    /**
     * Fired when connection with the remote peer is terminated.
     *
     * @param peer   Remote peer that disconnected (may be null if unhandshaked)
     * @param reason Human-readable explanation of the disconnection
     */
    void onDisconnected(PeerInfo peer, String reason);

    /**
     * Fired when an unexpected network or socket exception occurs.
     *
     * @param cause Throwable representing the error
     */
    void onError(Throwable cause);
}
