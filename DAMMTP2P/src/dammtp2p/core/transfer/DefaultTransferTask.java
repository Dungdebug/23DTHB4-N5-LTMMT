package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of {@link TransferTask} representing a tracked transfer session.
 */
public class DefaultTransferTask implements TransferTask {

    protected volatile String transferId;
    protected volatile FileMetadata metadata;
    protected volatile TransferState state;
    protected volatile long transferredBytes;
    protected final AtomicBoolean cancelled = new AtomicBoolean(false);
    protected final AtomicBoolean paused = new AtomicBoolean(false);

    public DefaultTransferTask(FileMetadata metadata, TransferState initialState) {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.transferId = metadata.getTransferId();
        this.state = initialState != null ? initialState : TransferState.READY_TO_TRANSFER;
        this.transferredBytes = metadata.getOffset();
    }

    public void setMetadata(FileMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        this.transferId = metadata.getTransferId();
    }

    @Override
    public String getTransferId() {
        return transferId;
    }

    @Override
    public FileMetadata getMetadata() {
        return metadata;
    }

    @Override
    public TransferState getState() {
        return state;
    }

    public void setState(TransferState state) {
        this.state = state;
    }

    @Override
    public long getTransferredBytes() {
        return transferredBytes;
    }

    public void setTransferredBytes(long transferredBytes) {
        this.transferredBytes = transferredBytes;
    }

    @Override
    public long getTotalBytes() {
        return metadata.getFileSize();
    }

    @Override
    public long getRemainingBytes() {
        return Math.max(0L, getTotalBytes() - transferredBytes);
    }

    @Override
    public void pause() {
        paused.set(true);
    }

    @Override
    public void resume() {
        paused.set(false);
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        this.state = TransferState.CANCELED;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isPaused() {
        return paused.get();
    }
}