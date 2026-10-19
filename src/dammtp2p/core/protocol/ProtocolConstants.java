package dammtp2p.core.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Protocol specifications and wire format constants for the DAMMTP2P framed protocol.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {
        // Prevent instantiation
    }

    /**
     * Magic identifier for DAMMTP2P framed packets: "DMTP" (0x44, 0x4D, 0x54, 0x50).
     * Prevents protocol confusion and drops non-DAMMTP2P traffic.
     */
    public static final int MAGIC_NUMBER = 0x444D5450;

    /**
     * Current protocol major version.
     */
    public static final byte PROTOCOL_VERSION = 1;

    /**
     * Fixed size in bytes of the packet header:
     * 4 bytes (Magic) + 1 byte (Version) + 1 byte (Type Code) + 4 bytes (Payload Length) = 10 bytes.
     */
    public static final int HEADER_SIZE = 10;

    /**
     * Explicit standard character set for all protocol text payloads.
     */
    public static final Charset PROTOCOL_CHARSET = StandardCharsets.UTF_8;

    /**
     * Maximum allowed payload size for a single control/chat packet (1 MB).
     * Binary file transfer chunks are sent via the dedicated data channel and not as control packets.
     */
    public static final int MAX_CONTROL_PAYLOAD_SIZE = 1024 * 1024;

    /**
     * Maximum allowed file transfer size (20 GiB = 21,474,836,480 bytes).
     * Centrally unified with {@link dammtp2p.util.Constants#MAX_FILE_SIZE_BYTES}.
     */
    public static final long MAX_FILE_SIZE_BYTES = dammtp2p.util.Constants.MAX_FILE_SIZE_BYTES;
}
