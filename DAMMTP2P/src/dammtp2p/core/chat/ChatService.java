package dammtp2p.core.chat;

import java.io.IOException;

/**
 * Service interface for handling 1-to-1 P2P chat communication.
 */
public interface ChatService {

    /**
     * Sends a text message to the currently connected peer.
     *
     * @param content Text message content
     * @throws IOException if not connected or if socket write fails
     */
    void sendMessage(String content) throws IOException;

    /**
     * Adds a listener for incoming and outgoing chat messages.
     *
     * @param listener ChatMessageListener to add
     */
    void addMessageListener(ChatMessageListener listener);

    /**
     * Removes a registered chat message listener.
     *
     * @param listener ChatMessageListener to remove
     */
    void removeMessageListener(ChatMessageListener listener);
}
