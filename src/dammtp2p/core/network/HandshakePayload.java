package dammtp2p.core.network;

import dammtp2p.core.protocol.ProtocolConstants;
import dammtp2p.util.Constants;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data payload exchanged during the initial application-level handshake.
 * Confirms protocol compatibility, application identity, and peer ports.
 */
public class HandshakePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern APP_PATTERN = Pattern.compile("\"app\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*(-?\\d+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"displayName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CONTROL_PORT_PATTERN = Pattern.compile("\"controlPort\"\\s*:\\s*(\\d+)");
    private static final Pattern FILE_PORT_PATTERN = Pattern.compile("\"filePort\"\\s*:\\s*(\\d+)");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d+)");

    private final String appName;
    private final int protocolVersion;
    private final String displayName;
    private final int controlPort;
    private final int filePort;
    private final long timestamp;

    public HandshakePayload(String displayName, int controlPort, int filePort) {
        this(Constants.APP_NAME, ProtocolConstants.PROTOCOL_VERSION, displayName, controlPort, filePort, System.currentTimeMillis());
    }

    public HandshakePayload(String appName, int protocolVersion, String displayName,
                            int controlPort, int filePort, long timestamp) {
        this.appName = Objects.requireNonNull(appName, "appName must not be null");
        this.protocolVersion = protocolVersion;
        this.displayName = displayName != null ? displayName.trim() : "Peer";
        this.controlPort = controlPort;
        this.filePort = filePort;
        this.timestamp = timestamp;
    }

    public String getAppName() {
        return appName;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getControlPort() {
        return controlPort;
    }

    public int getFilePort() {
        return filePort;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Validates that the payload matches expected application identity and protocol version.
     *
     * @throws IncompatibleProtocolException if the peer uses an unsupported protocol or unknown app
     */
    public void validate() throws IncompatibleProtocolException {
        if (!Constants.APP_NAME.equalsIgnoreCase(appName)) {
            throw new IncompatibleProtocolException(String.format(
                    "Incompatible application identity: '%s' (expected '%s')", appName, Constants.APP_NAME));
        }
        if (protocolVersion != ProtocolConstants.PROTOCOL_VERSION) {
            throw new IncompatibleProtocolException(String.format(
                    "Unsupported protocol version: %d (expected %d)", protocolVersion, ProtocolConstants.PROTOCOL_VERSION));
        }
    }

    /**
     * Serializes this payload into a lightweight UTF-8 JSON string.
     */
    public String toJson() {
        return String.format(
                "{\"app\":\"%s\",\"version\":%d,\"displayName\":\"%s\",\"controlPort\":%d,\"filePort\":%d,\"timestamp\":%d}",
                escapeJson(appName),
                protocolVersion,
                escapeJson(displayName),
                controlPort,
                filePort,
                timestamp
        );
    }

    /**
     * Parses a lightweight UTF-8 JSON string into a HandshakePayload.
     *
     * @param json JSON string
     * @return Deserialized HandshakePayload
     * @throws IncompatibleProtocolException if parsing fails
     */
    public static HandshakePayload fromJson(String json) throws IncompatibleProtocolException {
        if (json == null || json.trim().isEmpty()) {
            throw new IncompatibleProtocolException("Empty handshake payload received");
        }

        String app = extractString(APP_PATTERN, json, "app");
        int version = extractInt(VERSION_PATTERN, json, "version");
        String name = extractString(NAME_PATTERN, json, "displayName");
        int controlPort = extractInt(CONTROL_PORT_PATTERN, json, "controlPort");
        int filePort = extractInt(FILE_PORT_PATTERN, json, "filePort");
        long ts = extractLong(TIMESTAMP_PATTERN, json, "timestamp");

        return new HandshakePayload(app, version, name, controlPort, filePort, ts);
    }

    private static String extractString(Pattern pattern, String text, String fieldName) throws IncompatibleProtocolException {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        throw new IncompatibleProtocolException("Malformed handshake payload: missing field '" + fieldName + "'");
    }

    private static int extractInt(Pattern pattern, String text, String fieldName) throws IncompatibleProtocolException {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                throw new IncompatibleProtocolException("Invalid numeric value for field '" + fieldName + "'", e);
            }
        }
        throw new IncompatibleProtocolException("Malformed handshake payload: missing field '" + fieldName + "'");
    }

    private static long extractLong(Pattern pattern, String text, String fieldName) throws IncompatibleProtocolException {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                throw new IncompatibleProtocolException("Invalid numeric value for field '" + fieldName + "'", e);
            }
        }
        return System.currentTimeMillis();
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
        return String.format("HandshakePayload[app='%s', v=%d, name='%s', ctrlPort=%d, filePort=%d]",
                appName, protocolVersion, displayName, controlPort, filePort);
    }
}
