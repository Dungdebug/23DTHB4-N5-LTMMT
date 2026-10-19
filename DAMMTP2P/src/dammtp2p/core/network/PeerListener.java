package dammtp2p.core.network;

import java.io.IOException;

/**
 * Contract for the P2P listener (server role) that accepts incoming TCP connections
 * from remote peers over ZeroTier / LAN.
 */
public interface PeerListener {

    /**
     * Starts listening for incoming peer connections on the specified port.
     *
     * @param port TCP port to bind (e.g. 8888)
     * @throws IOException if the port cannot be bound
     */
    void startListening(int port) throws IOException;

    /**
     * Stops the listener and closes the underlying ServerSocket.
     */
    void stopListening();

    /**
     * Checks if the listener is currently active and accepting connections.
     *
     * @return true if listening, false otherwise
     */
    boolean isListening();

    /**
     * Returns the port the listener is bound to.
     *
     * @return Port number, or -1 if not listening
     */
    int getBoundPort();
}
