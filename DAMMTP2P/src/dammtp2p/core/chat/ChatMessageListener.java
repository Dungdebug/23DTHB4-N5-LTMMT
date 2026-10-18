package dammtp2p.core.chat;

import dammtp2p.core.model.ChatMessage;

/**
 * Listener interface for receiving chat message notifications.
 */
public interface ChatMessageListener {

    /**
     * Fired when a chat message is received from the connected peer.
     *
     * @param message Received ChatMessage
     */
    void onMessageReceived(ChatMessage message);

    /**
     * Fired when an outgoing chat message is successfully transmitted.
     *
     * @param message Sent ChatMessage
     */
    void onMessageSent(ChatMessage message);
}
