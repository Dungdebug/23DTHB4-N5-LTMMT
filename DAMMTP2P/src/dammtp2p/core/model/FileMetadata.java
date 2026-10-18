package dammtp2p.core.model;

import dammtp2p.util.Constants;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata descriptor for a file transfer operation.
 *
 * <p>IMPORTANT ARCHITECTURAL REQUIREMENT:
 * To support transfers of large files up to 20 GiB (21,474,836,480 bytes), all file size
 * and byte offset fields MUST use {@code long} (64-bit integer).
 * Standard {@code int} (32-bit signed) overflows at ~2.14 GB and MUST NEVER
 * be used for file sizes or offsets.
 */
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Validates if the given file size is within the allowed protocol range [0, 20 GiB].
     */
    public static boolean isSizeValid(long size) {
        return size >= 0L && size <= Constants.MAX_FILE_SIZE_BYTES;
    }

    private static final Pattern ID_PATTERN = Pattern.compile("\"transferId\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FILE_PATTERN = Pattern.compile("\"fileName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SIZE_PATTERN = Pattern.compile("\"fileSize\"\\s*:\\s*(\\d+)");
    private static final Pattern SHA_PATTERN = Pattern.compile("\"sha256\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("\"offset\"\\s*:\\s*(\\d+)");
    private static final Pattern SENDER_PATTERN = Pattern.compile("\"sender\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern RECEIVER_PATTERN = Pattern.compile("\"receiver\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d+)");
    private static final Pattern ENCRYPTED_PATTERN = Pattern.compile("\"encrypted\"\\s*:\\s*(true|false)");
    private static final Pattern KEY_PATTERN = Pattern.compile("\"aesKey\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern IV_PATTERN = Pattern.compile("\"baseIv\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private static final String[] WINDOWS_RESERVED_NAMES = {
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    private final String transferId;
    private final String fileName;
    private final long fileSize;   // Explicitly 64-bit long for 20 GiB support
    private final String sha256;    // Hexadecimal SHA-256 digest string
    private final long offset;     // Explicitly 64-bit long for resumable transfers
    private final String sender;
    private final String receiver;
    private final long timestamp;
    private final boolean encrypted;
    private final String aesKeyBase64;
    private final String baseIvBase64;

    public FileMetadata(String transferId, String fileName, long fileSize, String sha256,
                        long offset, String sender, String receiver, long timestamp,
                        boolean encrypted, String aesKeyBase64, String baseIvBase64) {
        if (fileSize < 0L) {
            throw new IllegalArgumentException("fileSize cannot be negative: " + fileSize);
        }
        if (fileSize > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(Constants.OVERSIZED_FILE_ERROR_MESSAGE + " (" + fileSize + " bytes > " + Constants.MAX_FILE_SIZE_BYTES + " bytes)");
        }
        if (offset < 0L) {
            throw new IllegalArgumentException("offset cannot be negative: " + offset);
        }
        if (offset > fileSize) {
            throw new IllegalArgumentException(String.format("offset (%d) cannot exceed fileSize (%d)", offset, fileSize));
        }

        this.transferId = Objects.requireNonNull(transferId, "transferId must not be null");
        this.fileName = sanitizeFileName(Objects.requireNonNull(fileName, "fileName must not be null"));
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.offset = offset;
        this.sender = sender != null ? sender : "Unknown";
        this.receiver = receiver != null ? receiver : "Unknown";
        this.timestamp = timestamp;
        this.encrypted = encrypted;
        this.aesKeyBase64 = aesKeyBase64;
        this.baseIvBase64 = baseIvBase64;
    }

    public FileMetadata(String transferId, String fileName, long fileSize, String sha256,
                        long offset, String sender, String receiver, long timestamp) {
        this(transferId, fileName, fileSize, sha256, offset, sender, receiver, timestamp, false, null, null);
    }

    /**
     * Sanitizes a file name by removing any path traversal sequences, directory separators,
     * forbidden filesystem characters, or Windows reserved device names.
     */
    public static String sanitizeFileName(String inputName) {
        if (inputName == null || inputName.trim().isEmpty()) {
            return "unnamed_file";
        }
        // Extract base name if path was provided
        String clean = inputName.replace('\\', '/');
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
            clean = clean.substring(lastSlash + 1);
        }
        // Repeatedly strip directory traversal sequences
        while (clean.contains("..")) {
            clean = clean.replace("..", "");
        }
        clean = clean.replace("/", "").replace("\\", "").trim();

        // Strip null bytes and replace forbidden filesystem characters
        StringBuilder sb = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c < 32 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        clean = sb.toString().trim();
        if (clean.isEmpty()) {
            return "unnamed_file";
        }

        // Check Windows reserved names
        String baseWithoutExt = clean;
        int dot = clean.indexOf('.');
        if (dot > 0) {
            baseWithoutExt = clean.substring(0, dot);
        }
        for (String reserved : WINDOWS_RESERVED_NAMES) {
            if (reserved.equalsIgnoreCase(baseWithoutExt)) {
                clean = "_" + clean;
                break;
            }
        }

        return clean;
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

    public String getSha256() {
        return sha256;
    }

    public long getOffset() {
        return offset;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getRemainingBytes() {
        return Math.max(0L, fileSize - offset);
    }

    public boolean isComplete() {
        return offset >= fileSize && fileSize > 0L;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public String getAesKeyBase64() {
        return aesKeyBase64;
    }

    public String getBaseIvBase64() {
        return baseIvBase64;
    }

    public FileMetadata withOffset(long newOffset) {
        return new FileMetadata(
                this.transferId,
                this.fileName,
                this.fileSize,
                this.sha256,
                newOffset,
                this.sender,
                this.receiver,
                this.timestamp,
                this.encrypted,
                this.aesKeyBase64,
                this.baseIvBase64
        );
    }

    public FileMetadata withSha256(String newSha256) {
        return new FileMetadata(
                this.transferId,
                this.fileName,
                this.fileSize,
                newSha256,
                this.offset,
                this.sender,
                this.receiver,
                this.timestamp,
                this.encrypted,
                this.aesKeyBase64,
                this.baseIvBase64
        );
    }

    /**
     * Serializes this FileMetadata into a lightweight UTF-8 JSON string.
     */
    public String toJson() {
        return String.format(
                "{\"transferId\":\"%s\",\"fileName\":\"%s\",\"fileSize\":%d,\"sha256\":\"%s\",\"offset\":%d,\"sender\":\"%s\",\"receiver\":\"%s\",\"timestamp\":%d,\"encrypted\":%b,\"aesKey\":\"%s\",\"baseIv\":\"%s\"}",
                escapeJson(transferId),
                escapeJson(fileName),
                fileSize,
                sha256 != null ? escapeJson(sha256) : "",
                offset,
                escapeJson(sender),
                escapeJson(receiver),
                timestamp,
                encrypted,
                aesKeyBase64 != null ? escapeJson(aesKeyBase64) : "",
                baseIvBase64 != null ? escapeJson(baseIvBase64) : ""
        );
    }

    /**
     * Deserializes FileMetadata from a UTF-8 JSON string.
     */
    public static FileMetadata fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty FileMetadata JSON payload");
        }

        String id = extract(ID_PATTERN, json, "transferId", "");
        String file = extract(FILE_PATTERN, json, "fileName", "unnamed");
        long size = extractLong(SIZE_PATTERN, json, 0L);
        String sha = extract(SHA_PATTERN, json, "sha256", null);
        long off = extractLong(OFFSET_PATTERN, json, 0L);
        String snd = extract(SENDER_PATTERN, json, "sender", "Unknown");
        String rcv = extract(RECEIVER_PATTERN, json, "receiver", "Unknown");
        long ts = extractLong(TIMESTAMP_PATTERN, json, System.currentTimeMillis());
        boolean enc = "true".equalsIgnoreCase(extract(ENCRYPTED_PATTERN, json, "encrypted", "false"));
        String key = extract(KEY_PATTERN, json, "aesKey", null);
        String iv = extract(IV_PATTERN, json, "baseIv", null);

        return new FileMetadata(id, file, size, (sha != null && !sha.isEmpty()) ? sha : null, off, snd, rcv, ts, enc, key, iv);
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
        return String.format("FileMetadata[id=%s, file='%s', size=%d bytes, offset=%d, sender='%s']",
                transferId, fileName, fileSize, offset, sender);
    }
}