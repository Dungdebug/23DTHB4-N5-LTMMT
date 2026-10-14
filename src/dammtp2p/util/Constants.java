package dammtp2p.util;

/**
 * Global constants and configuration defaults for the DAMMTP2P application.
 * All configurable ports, buffer sizes, and limits are centralized here to
 * prevent magic numbers in the implementation.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    /** Application metadata */
    public static final String APP_NAME = "DAMMTP2P";
    public static final String APP_VERSION = "1.0.0";

    /**
     * Mandatory maximum file transfer size: 20 GiB (exact limit: 21,474,836,480 bytes).
     * All file size variables, counters, and offsets MUST use 64-bit long.
     */
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024L * 1024L * 1024L; // 21,474,836,480 bytes
    public static final String MAX_FILE_SIZE_LABEL = "20 GiB";
    public static final String OVERSIZED_FILE_ERROR_MESSAGE = "Dung lượng tệp vượt quá giới hạn 20 GiB. Vui lòng chọn tệp nhỏ hơn.";

    /**
     * Default TCP port for control messages, handshakes, and 1-to-1 chat.
     */
    public static final int DEFAULT_CONTROL_PORT = 8888;

    /**
     * Default TCP port for high-speed binary file transfer channel.
     * Separated from the control channel to avoid blocking chat during multi-GB transfers.
     */
    public static final int DEFAULT_FILE_TRANSFER_PORT = 8889;

    /**
     * Default UDP port for real-time voice audio streaming.
     */
    public static final int DEFAULT_VOICE_PORT = 8890;

    /**
     * Default streaming buffer size for file transfer chunks (64 KB).
     */
    public static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    /**
     * Maximum streaming buffer size (256 KB) for high-throughput LAN transfers.
     */
    public static final int MAX_BUFFER_SIZE = 256 * 1024;

    /**
     * Default socket connection timeout in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;

    /**
     * Default socket read timeout for control channel handshakes in milliseconds.
     */
    public static final int DEFAULT_SO_TIMEOUT_MS = 30_000;

    /**
     * Default local directory for storing received files.
     */
    public static final String DEFAULT_DOWNLOAD_DIR = "downloads";

    /**
     * Maximum number of concurrent transfer workers in the thread pool.
     */
    public static final int MAX_TRANSFER_WORKERS = 4;
}
