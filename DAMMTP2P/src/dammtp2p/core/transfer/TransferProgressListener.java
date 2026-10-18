package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;

/**
 * Observer interface for monitoring real-time file transfer progress,
 * throughput, state transitions, and error events.
 *
 * <p>All byte quantities use {@code long} to support files exceeding 10 GB.
 */
public interface TransferProgressListener {

    /**
     * Fired periodically as chunks are sent or received.
     *
     * @param metadata         Metadata of the file being transferred
     * @param transferredBytes Total bytes transferred so far (including resume offset)
     * @param totalBytes       Total size of the file in bytes
     * @param bytesPerSecond   Current instantaneous or rolling transfer speed
     */
    void onProgress(FileMetadata metadata, long transferredBytes, long totalBytes, double bytesPerSecond);

    /**
     * Extended progress callback including estimated remaining time.
     */
    default void onProgress(FileMetadata metadata, long transferredBytes, long totalBytes, double bytesPerSecond, long etaSeconds) {
        onProgress(metadata, transferredBytes, totalBytes, bytesPerSecond);
    }

    /**
     * Fired when binary streaming commences across the data channel.
     */
    default void onTransferStarted(FileMetadata metadata) {}

    /**
     * Fired when the lifecycle state of a transfer changes.
     *
     * @param metadata Metadata of the file
     * @param oldState Previous state
     * @param newState New state
     */
    void onStateChanged(FileMetadata metadata, TransferState oldState, TransferState newState);

    /**
     * Fired when the file transfer and SHA-256 verification have successfully concluded.
     *
     * @param metadata Metadata of the completed file
     */
    void onCompleted(FileMetadata metadata);

    /**
     * Fired when a transfer fails due to an I/O exception, network disconnect, or checksum error.
     *
     * @param metadata     Metadata of the failed file
     * @param errorMessage Descriptive error message
     * @param cause        Root cause exception (may be null)
     */
    void onError(FileMetadata metadata, String errorMessage, Throwable cause);
}