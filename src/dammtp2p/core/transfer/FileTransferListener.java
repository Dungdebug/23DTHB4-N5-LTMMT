package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;

/**
 * Observer interface for the File Request, Accept, and Reject negotiation workflow.
 */
public interface FileTransferListener {

    /**
     * Fired when a remote peer sends an incoming FILE_REQUEST.
     *
     * @param metadata Metadata of the offered file
     */
    void onIncomingFileRequest(FileMetadata metadata);

    /**
     * Fired when the receiver clicks ACCEPT on an incoming file offer.
     *
     * @param metadata Metadata of the accepted file
     */
    void onFileRequestAccepted(FileMetadata metadata);

    /**
     * Fired when the receiver clicks REJECT on an incoming file offer.
     *
     * @param metadata Metadata of the rejected file
     * @param reason   Human-readable reason for rejection
     */
    void onFileRequestRejected(FileMetadata metadata, String reason);

    /**
     * Fired when the lifecycle state of a file transfer session changes.
     *
     * @param metadata      Metadata of the file
     * @param state         New TransferState (PENDING, ACCEPTED, REJECTED, READY_TO_TRANSFER, FAILED)
     * @param statusMessage Descriptive status text for UI display
     */
    void onTransferStateChanged(FileMetadata metadata, TransferState state, String statusMessage);
}
