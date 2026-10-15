package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;

/**
 * Handle to an active, paused, or queued file transfer task.
 * Enables query and control of large file transfers (10 GB+).
 */
public interface TransferTask {

    /**
     * Unique identifier for this transfer session.
     */
    String getTransferId();

    /**
     * Metadata describing the file (name, size, hash, offset).
     */
    FileMetadata getMetadata();

    /**
     * Current lifecycle state of the transfer.
     */
    TransferState getState();

    /**
     * Cumulative bytes transferred so far (represented as {@code long}).
     */
    long getTransferredBytes();

    /**
     * Total size of the file in bytes (represented as {@code long}).
     */
    long getTotalBytes();

    /**
     * Remaining bytes to be transferred (represented as {@code long}).
     */
    long getRemainingBytes();

    /**
     * Pauses the active transfer task cooperatively.
     */
    void pause();

    /**
     * Resumes the paused transfer task from the current byte offset.
     */
    void resume();

    /**
     * Cancels the transfer task immediately and cleans up open file handles.
     */
    void cancel();
}
