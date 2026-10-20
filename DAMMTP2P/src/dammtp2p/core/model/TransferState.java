package dammtp2p.core.model;

/**
 * Lifecycle states of a 1-to-1 file transfer operation.
 */
public enum TransferState {
    /** File transfer requested and awaiting receiver decision */
    PENDING,

    /** Receiver clicked ACCEPT */
    ACCEPTED,

    /** Receiver clicked REJECT */
    REJECTED,

    /** Both peers confirmed; ready for binary transfer channel initialization */
    READY_TO_TRANSFER,

    /** Active streaming of file chunks across the data channel (Phase 5) */
    TRANSFERRING,

    /** Calculating and verifying SHA-256 integrity of completed file (Phase 7) */
    VERIFYING,

    /** SHA-256 checksum verified matching expected source hash (Phase 7) */
    VERIFIED,

    /** Transfer interrupted due to network disconnect or peer loss (Phase 6 Resumable) */
    INTERRUPTED,

    /** Resume negotiation in progress over control channel (Phase 6) */
    WAITING_FOR_RESUME,

    /** Connecting and resuming file streaming from agreed byte offset (Phase 6) */
    RESUMING,

    /** Transfer paused by sender or receiver */
    PAUSED,

    /** Transfer finished successfully and byte count verified */
    COMPLETED,

    /** Transfer canceled intentionally by either peer (Phase 6) */
    CANCELED,

    /** Transfer failed due to unrecoverable I/O error, source change, or protocol error */
    FAILED;

    /**
     * Human-friendly Vietnamese display label for UI rendering.
     */
    public String getDisplayName() {
        switch (this) {
            case PENDING: return "Chờ chấp nhận";
            case ACCEPTED: return "Đã chấp nhận";
            case REJECTED: return "Bị từ chối";
            case READY_TO_TRANSFER: return "Sẵn sàng truyền";
            case TRANSFERRING: return "Đang truyền tải";
            case VERIFYING: return "Đang kiểm tra SHA-256";
            case VERIFIED: return "Đã xác thực SHA-256";
            case INTERRUPTED: return "Bị gián đoạn";
            case WAITING_FOR_RESUME: return "Chờ nối lại";
            case RESUMING: return "Đang nối lại";
            case PAUSED: return "Tạm dừng";
            case COMPLETED: return "Hoàn tất";
            case CANCELED: return "Đã hủy";
            case FAILED: return "Thất bại";
            default: return name();
        }
    }

    /**
     * Checks if this state represents a terminal lifecycle state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED || this == FAILED || this == REJECTED;
    }

    /**
     * Checks if transitioning from this state to {@code next} is allowed.
     */
    public boolean canTransitionTo(TransferState next) {
        if (this == next) return true;
        switch (this) {
            case PENDING:
                return next == ACCEPTED || next == READY_TO_TRANSFER || next == REJECTED
                        || next == CANCELED || next == FAILED;
            case ACCEPTED:
                return next == READY_TO_TRANSFER || next == CANCELED || next == FAILED;
            case READY_TO_TRANSFER:
                return next == TRANSFERRING || next == CANCELED || next == FAILED || next == INTERRUPTED;
            case TRANSFERRING:
                return next == VERIFYING || next == COMPLETED || next == INTERRUPTED || next == CANCELED || next == FAILED || next == PAUSED;
            case VERIFYING:
                return next == VERIFIED || next == COMPLETED || next == FAILED || next == CANCELED;
            case VERIFIED:
                return next == COMPLETED || next == FAILED || next == CANCELED;
            case INTERRUPTED:
                return next == WAITING_FOR_RESUME || next == RESUMING || next == CANCELED || next == FAILED;
            case WAITING_FOR_RESUME:
                return next == RESUMING || next == TRANSFERRING || next == CANCELED || next == FAILED || next == INTERRUPTED;
            case RESUMING:
                return next == TRANSFERRING || next == VERIFYING || next == COMPLETED || next == INTERRUPTED || next == CANCELED || next == FAILED;
            case PAUSED:
                return next == TRANSFERRING || next == RESUMING || next == CANCELED || next == FAILED;
            case COMPLETED:
            case CANCELED:
            case FAILED:
            case REJECTED:
                // Terminal states allow starting a new PENDING transfer
                return next == PENDING;
            default:
                return false;
        }
    }
}