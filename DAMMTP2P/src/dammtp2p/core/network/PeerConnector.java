package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import java.io.IOException;

/**
 * Contract for initiating an outgoing direct TCP connection to a remote peer (client role)
 * using their ZeroTier or local IP address.
 */
public interface PeerConnector {

    /**
     * Establishes a direct TCP connection to a remote peer.
     *
     * @param remoteIp   Target IP address (e.g. ZeroTier IP "10.x.x.x")
     * @param remotePort Target control port (e.g. 8888)
     * @throws IOException on connection failure or timeout
     */
    void connect(String remoteIp, int remotePort) throws IOException;

    /**
     * Gracefully disconnects the current active peer session.
     */
    void disconnect();

    /**
     * Checks if actively connected to a remote peer.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Retrieves the connected remote peer's information.
     *
     * @return PeerInfo or null if not connected
     */
    PeerInfo getConnectedPeer();
}
