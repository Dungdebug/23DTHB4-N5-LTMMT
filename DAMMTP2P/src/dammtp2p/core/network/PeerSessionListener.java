package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.protocol.PacketHeader;

/**
 * Internal listener interface for handling events emitted by an active PeerSession.
 */
public interface PeerSessionListener {

    /**
     * Fired when the session completes the application handshake successfully.
     */
    void onSessionConnected(PeerSession session, PeerInfo remotePeer);

    /**
     * Fired when the session terminates.
     */
    void onSessionDisconnected(PeerSession session, PeerInfo remotePeer, String reason);

    /**
     * Fired when an application-level packet (chat, file negotiation, etc.) is received.
     */
    void onSessionPacket(PeerSession session, PacketHeader header, byte[] payload);

    /**
     * Fired when a communication or I/O error occurs within the session.
     */
    void onSessionError(PeerSession session, Throwable cause);
}
