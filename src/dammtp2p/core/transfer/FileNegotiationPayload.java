package dammtp2p.core.transfer;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload exchanged during file transfer acceptance or rejection.
 */
public class FileNegotiationPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern ID_PATTERN = Pattern.compile("\"transferId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SENDER_PATTERN = Pattern.compile("\"sender\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern RECEIVER_PATTERN = Pattern.compile("\"receiver\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FILE_PATTERN = Pattern.compile("\"fileName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SIZE_PATTERN = Pattern.compile("\"fileSize\"\\s*:\\s*(\\d+)");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final String transferId;
    private final String sender;
    private final String receiver;
    private final String fileName;
    private final long fileSize;
    private final String reason;

    public FileNegotiationPayload(String transferId, String sender, String receiver,
                                  String fileName, long fileSize, String reason) {
        this.transferId = transferId != null ? transferId : "";
        this.sender = sender != null ? sender : "Unknown";
        this.receiver = receiver != null ? receiver : "Unknown";
        this.fileName = fileName != null ? fileName : "";
        this.fileSize = fileSize;
        this.reason = reason != null ? reason : "";
    }

    public static FileNegotiationPayload createAccept(String transferId, String sender, String receiver,
                                                      String fileName, long fileSize) {
        return new FileNegotiationPayload(transferId, sender, receiver, fileName, fileSize, "Accepted");
    }

    public static FileNegotiationPayload createReject(String transferId, String sender, String receiver, String reason) {
        return new FileNegotiationPayload(transferId, sender, receiver, "", 0L, reason != null ? reason : "Rejected by receiver");
    }

    public static FileNegotiationPayload createCancel(String transferId, String sender, String receiver, String reason) {
        return new FileNegotiationPayload(transferId, sender, receiver, "", 0L, reason != null ? reason : "Canceled by user");
    }

    public String getTransferId() {
        return transferId;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getReason() {
        return reason;
    }

    public String toJson() {
        return String.format(
                "{\"transferId\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\",\"fileName\":\"%s\",\"fileSize\":%d,\"reason\":\"%s\"}",
                escapeJson(transferId),
                escapeJson(sender),
                escapeJson(receiver),
                escapeJson(fileName),
                fileSize,
                escapeJson(reason)
        );
    }

    public static FileNegotiationPayload fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty FileNegotiationPayload JSON");
        }

        String id = extract(ID_PATTERN, json, "transferId", "");
        String snd = extract(SENDER_PATTERN, json, "sender", "Unknown");
        String rcv = extract(RECEIVER_PATTERN, json, "receiver", "Unknown");
        String file = extract(FILE_PATTERN, json, "fileName", "");
        long size = extractLong(SIZE_PATTERN, json, 0L);
        String rsn = extract(REASON_PATTERN, json, "reason", "");

        return new FileNegotiationPayload(id, snd, rcv, file, size, rsn);
    }

    private static String extract(Pattern pattern, String text, String field, String defVal) {
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
