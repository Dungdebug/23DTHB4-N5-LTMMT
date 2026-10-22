package dammtp2p.core.chat;

import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.network.ConnectionManager;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.core.network.PeerSession;
import dammtp2p.core.protocol.PacketType;
import dammtp2p.core.protocol.ProtocolConstants;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ChatService} handling real-time 1-to-1 UTF-8 text chat
 * over the primary P2P control connection (Port 8888).
 */
public class DefaultChatService implements ChatService {

    private static final Pattern SENDER_PATTERN = Pattern.compile("\"sender\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CONTENT_PATTERN = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ID_PATTERN = Pattern.compile("\"messageId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern TIME_PATTERN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d+)");

    private final DefaultConnectionManager connectionManager;
    private final List<ChatMessageListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultChatService(DefaultConnectionManager connectionManager) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    }

    @Override
    public void sendMessage(String content) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        PeerSession session = connectionManager.getActiveSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("Cannot send message: No peer connected.");
        }

        String senderName = connectionManager.getLocalPeerInfo().getDisplayName();
        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        ChatMessage chatMsg = new ChatMessage(messageId, senderName, content.trim(), timestamp, true);
        String jsonPayload = serializeChatMessage(chatMsg);

        // Transmit framed packet on control connection
        session.sendPacket(PacketType.CHAT_MESSAGE, jsonPayload);

        // Notify local observers of outgoing message
        for (ChatMessageListener listener : listeners) {
            listener.onMessageSent(chatMsg);
        }
    }

    /**
     * Dispatches an incoming CHAT_MESSAGE packet received from the peer.
     */
    public void handleIncomingChatPacket(byte[] payload) {
        String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
        ChatMessage msg = parseChatMessage(json);
        if (msg != null) {
            for (ChatMessageListener listener : listeners) {
                listener.onMessageReceived(msg);
            }
        }
    }

    @Override
    public void addMessageListener(ChatMessageListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeMessageListener(ChatMessageListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public static String serializeChatMessage(ChatMessage msg) {
        return String.format(
                "{\"messageId\":\"%s\",\"sender\":\"%s\",\"content\":\"%s\",\"timestamp\":%d}",
                escapeJson(msg.getMessageId()),
                escapeJson(msg.getSender()),
                escapeJson(msg.getContent()),
                msg.getTimestamp()
        );
    }

    public static ChatMessage parseChatMessage(String json) {
        try {
            String id = extract(ID_PATTERN, json, UUID.randomUUID().toString());
            String sender = extract(SENDER_PATTERN, json, "Peer");
            String content = extract(CONTENT_PATTERN, json, "");
            long ts = extractLong(TIME_PATTERN, json, System.currentTimeMillis());

            return new ChatMessage(id, sender, content, ts, false);
        } catch (Exception e) {
            System.err.println("Warning: Malformed chat packet received: " + e.getMessage());
            return null;
        }
    }

    private static String extract(Pattern pattern, String text, String defVal) {
        Matcher m = pattern.matcher(text);
        return m.find() ? unescapeJson(m.group(1)) : defVal;
    }

    private static long extractLong(Pattern pattern, String text, long defVal) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defVal;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String unescapeJson(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(c); break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }
}
