package dammtp2p.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a chat message (1-to-1 private or group room) exchanged via the server.
 * Contains sender/receiver metadata, room identifier, encryption flags (AES-128-GCM),
 * and message timestamp.
 */
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String messageId;
    private final String sender;
    private final String receiver;
    private final String roomId;
    private final String content;
    private final long timestamp;
    private final boolean outgoing;
    private final boolean encrypted;
    private final String iv;

    public ChatMessage(String messageId, String sender, String receiver, String roomId,
                       String content, long timestamp, boolean outgoing, boolean encrypted, String iv) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.receiver = receiver;
        this.roomId = roomId;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.timestamp = timestamp;
        this.outgoing = outgoing;
        this.encrypted = encrypted;
        this.iv = iv;
    }

    public ChatMessage(String sender, String content, boolean outgoing) {
        this(UUID.randomUUID().toString(), sender, null, null, content, System.currentTimeMillis(), outgoing, false, null);
    }

    public ChatMessage(String messageId, String sender, String content, long timestamp, boolean outgoing) {
        this(messageId, sender, null, null, content, timestamp, outgoing, false, null);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public String getIv() {
        return iv;
    }

    public boolean isRoomMessage() {
        return roomId != null && !roomId.isEmpty();
    }

    public boolean isPrivateMessage() {
        return !isRoomMessage() && receiver != null && !receiver.isEmpty();
    }

    private static final java.util.regex.Pattern ID_PATTERN = java.util.regex.Pattern.compile("\"messageId\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern SENDER_PATTERN = java.util.regex.Pattern.compile("\"sender\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern RECEIVER_PATTERN = java.util.regex.Pattern.compile("\"receiver\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern ROOM_PATTERN = java.util.regex.Pattern.compile("\"roomId\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern CONTENT_PATTERN = java.util.regex.Pattern.compile("\"content\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern TIME_PATTERN = java.util.regex.Pattern.compile("\"timestamp\"\\s*:\\s*(\\d+)");
    private static final java.util.regex.Pattern ENCRYPTED_PATTERN = java.util.regex.Pattern.compile("\"encrypted\"\\s*:\\s*(true|false)");
    private static final java.util.regex.Pattern IV_PATTERN = java.util.regex.Pattern.compile("\"iv\"\\s*:\\s*\"([^\"]*)\"");

    public String toJson() {
        return String.format(
                "{\"messageId\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\",\"roomId\":\"%s\",\"content\":\"%s\",\"timestamp\":%d,\"encrypted\":%b,\"iv\":\"%s\"}",
                escapeJson(messageId),
                escapeJson(sender),
                receiver != null ? escapeJson(receiver) : "",
                roomId != null ? escapeJson(roomId) : "",
                escapeJson(content),
                timestamp,
                encrypted,
                iv != null ? escapeJson(iv) : ""
        );
    }

    public static ChatMessage fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            String id = extract(ID_PATTERN, json, UUID.randomUUID().toString());
            String sender = extract(SENDER_PATTERN, json, "Peer");
            String receiver = extract(RECEIVER_PATTERN, json, null);
            String roomId = extract(ROOM_PATTERN, json, null);
            String content = extract(CONTENT_PATTERN, json, "");
            long ts = extractLong(TIME_PATTERN, json, System.currentTimeMillis());
            boolean enc = "true".equalsIgnoreCase(extract(ENCRYPTED_PATTERN, json, "false"));
            String iv = extract(IV_PATTERN, json, null);

            return new ChatMessage(id, sender, receiver, roomId, content, ts, false, enc, iv);
        } catch (Exception e) {
            System.err.println("Warning: Malformed chat packet received: " + e.getMessage());
            return null;
        }
    }

    private static String extract(java.util.regex.Pattern pattern, String text, String defVal) {
        java.util.regex.Matcher m = pattern.matcher(text);
        return m.find() ? unescapeJson(m.group(1)) : defVal;
    }

    private static long extractLong(java.util.regex.Pattern pattern, String text, long defVal) {
        java.util.regex.Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defVal;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    @Override
    public String toString() {
        return String.format("[%tT] %s%s%s%s: %s",
                timestamp,
                outgoing ? "(You) " : "",
                isRoomMessage() ? "[" + roomId + "] " : "",
                encrypted ? "🔒 " : "",
                sender,
                content);
    }
}
