package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import java.io.File;
import java.io.IOException;

/**
 * Service contract for the peer-to-peer file transfer engine.
 * Designed specifically to handle large files (>= 10 GB) with streaming,
 * chunking, resume support, and progress tracking.
 */
public interface FileTransferService {

    /**
     * Initiates an offer to send a file to the connected peer.
     *
     * @param localFile File to send
     * @return FileMetadata containing generated transferId, size (long), and calculated SHA-256
     * @throws IOException if the file does not exist or cannot be read
     */
    FileMetadata offerFile(File localFile) throws IOException;

    /**
     * Accepts an incoming file transfer offer from the remote peer.
     *
     * @param transferId           ID of the pending transfer
     * @param destinationDirectory Local directory where the received file will be saved
     * @param resumeOffset         Byte offset to resume from (0 for fresh start, &gt;0 to resume)
     * @throws IOException on I/O initialization error
     */
    void acceptTransfer(String transferId, File destinationDirectory, long resumeOffset) throws IOException;

    /**
     * Rejects an incoming file transfer offer.
     *
     * @param transferId ID of the pending transfer
     * @param reason     Reason for rejection
     * @throws IOException on network communication failure
     */
    void rejectTransfer(String transferId, String reason) throws IOException;

    /**
     * Cancels an active file transfer.
     *
     * @param transferId ID of the transfer to cancel
     */
    void cancelTransfer(String transferId);

    /**
     * Pauses an active file transfer.
     *
     * @param transferId ID of the transfer to pause
     */
    void pauseTransfer(String transferId);

    /**
     * Resumes a paused or interrupted transfer.
     *
     * @param transferId ID of the transfer to resume
     */
    void resumeTransfer(String transferId);

    /**
     * Adds an observer for file transfer events.
     */
    void addProgressListener(TransferProgressListener listener);

    /**
     * Removes an observer for file transfer events.
     */
    void removeProgressListener(TransferProgressListener listener);
}