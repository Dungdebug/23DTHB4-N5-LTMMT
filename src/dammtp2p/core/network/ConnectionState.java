package dammtp2p.core.network;

/**
 * Lifecycle states of the direct P2P connection session.
 */
public enum ConnectionState {
    /** No peer connection is active; listener may be running */
    DISCONNECTED,

    /** Active outgoing TCP connection attempt in progress */
    CONNECTING,

    /** Incoming connection accepted, awaiting handshake */
    INCOMING,

    /** TCP socket established; bidirectional application handshake in progress */
    HANDSHAKING,

    /** Handshake verified; peer session fully established and ready for communication */
    CONNECTED,

    /** Session termination in progress */
    DISCONNECTING,

    /** Outgoing or incoming connection attempt failed */
    FAILED
}
