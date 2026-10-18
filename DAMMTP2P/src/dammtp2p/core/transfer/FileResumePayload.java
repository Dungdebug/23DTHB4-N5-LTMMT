package dammtp2p.core.transfer;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Control channel payload exchanged on Port 8888 for negotiating resumable file transfers.
 * Conveys transfer identity, validated 64-bit byte offsets, and rejection rationales.
 */
public class FileResumePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_REQUEST = "REQUEST";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED_SOURCE_CHANGED = "REJECTED_SOURCE_CHANGED";
    public static final String STATUS_REJECTED_INVALID_OFFSET = "REJECTED_INVALID_OFFSET";
    public static final String STATUS_REJECTED_NOT_FOUND = "REJECTED_NOT_FOUND";
    public static final String STATUS_REJECTED_INTEGRITY_MISMATCH = "REJECTED_INTEGRITY_MISMATCH";
    public static final String STATUS_REJECTED_UNKNOWN = "REJECTED_UNKNOWN";

    private static final Pattern ID_PATTERN = Pattern.compile("\"transferId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FILE_PATTERN = Pattern.compile("\"fileName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SIZE_PATTERN = Pattern.compile("\"fileSize\"\\s*:\\s*(\\d+)");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("\"resumeOffset\"\\s*:\\s*(\\d+)");
    private static final Pattern SENDER_PATTERN = Pattern.compile("\"sender\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern RECEIVER_PATTERN = Pattern.compile("\"receiver\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final String transferId;
    private final String fileName;
    private final long fileSize;
    private final long resumeOffset;
    private final String sender;
    private final String receiver;
    private final String status;
    private final String reason;

    public FileResumePayload(String transferId, String fileName, long fileSize, long resumeOffset,
                             String sender, String receiver, String status, String reason) {
        this.transferId = transferId != null ? transferId : "";
        this.fileName = fileName != null ? fileName : "";
        this.fileSize = fileSize;
        this.resumeOffset = resumeOffset;
        this.sender = sender != null ? sender : "";
        this.receiver = receiver != null ? receiver : "";
        this.status = status != null ? status : STATUS_REQUEST;
        this.reason = reason != null ? reason : "";
    }

    public static FileResumePayload createRequest(String transferId, String fileName, long fileSize,
                                                  String sender, String receiver) {
        return new FileResumePayload(transferId, fileName, fileSize, 0L, sender, receiver,
                STATUS_REQUEST, "Yêu cầu tiếp tục truyền tệp (Resume request)");
    }

    public static FileResumePayload createAck(String transferId, String fileName, long fileSize, long resumeOffset,
                                              String sender, String receiver) {
        return new FileResumePayload(transferId, fileName, fileSize, resumeOffset, sender, receiver,
                STATUS_ACCEPTED, "Chấp nhận tiếp tục truyền từ offset");
    }

    public static FileResumePayload createReject(String transferId, String fileName, long fileSize,
                                                 String status, String reason, String sender, String receiver) {
        return new FileResumePayload(transferId, fileName, fileSize, 0L, sender, receiver,
                status != null ? status : STATUS_REJECTED_UNKNOWN, reason != null ? reason : "Từ chối tiếp tục truyền");
    }

    public String getTransferId() {
        return transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getResumeOffset() {
        return resumeOffset;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAccepted() {
        return STATUS_ACCEPTED.equalsIgnoreCase(status);
    }

    public String toJson() {
        return String.format(
                "{\"transferId\":\"%s\",\"fileName\":\"%s\",\"fileSize\":%d,\"resumeOffset\":%d,\"sender\":\"%s\",\"receiver\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\"}",
                escapeJson(transferId),
                escapeJson(fileName),
                fileSize,
                resumeOffset,
                escapeJson(sender),
                escapeJson(receiver),
                escapeJson(status),
                escapeJson(reason)
        );
    }

    public static FileResumePayload fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty FileResumePayload JSON");
        }

        String id = extract(ID_PATTERN, json, "transferId", "");
        String file = extract(FILE_PATTERN, json, "fileName", "");
        long size = extractLong(SIZE_PATTERN, json, 0L);
        long offset = extractLong(OFFSET_PATTERN, json, 0L);
        String snd = extract(SENDER_PATTERN, json, "sender", "Unknown");
        String rcv = extract(RECEIVER_PATTERN, json, "receiver", "Unknown");
        String st = extract(STATUS_PATTERN, json, "status", STATUS_REQUEST);
        String rsn = extract(REASON_PATTERN, json, "reason", "");

        return new FileResumePayload(id, file, size, offset, snd, rcv, st, rsn);
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

    @Override
    public String toString() {
        return String.format("FileResumePayload[id=%s, file=%s, size=%d, offset=%d, status=%s, reason='%s']",
                transferId, fileName, fileSize, resumeOffset, status, reason);
    }
}