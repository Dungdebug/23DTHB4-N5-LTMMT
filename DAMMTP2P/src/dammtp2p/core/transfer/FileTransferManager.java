package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.core.network.PeerSession;
import dammtp2p.core.protocol.PacketType;
import dammtp2p.core.protocol.ProtocolConstants;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core manager coordinating P2P File Transfer negotiation (Port 8888) and binary streaming (Port 8889).
 * Implements {@link FileTransferService} and {@link TransferProgressListener}, managing sender validation,
 * data handshake verification, background workers, and observer dispatching.
 */
public class FileTransferManager implements FileTransferService, TransferProgressListener {

    private final DefaultConnectionManager connectionManager;
    private final List<FileTransferListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TransferProgressListener> progressListeners = new CopyOnWriteArrayList<>();

    // Duplicate request protection
    private final Set<String> processedTransferIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Accepted offers waiting for Port 8889 binary data connection
    private final Map<String, FileMetadata> acceptedTransfers = new ConcurrentHashMap<>();

    // Active streaming tasks (FileSender / FileReceiver)
    private final Map<String, TransferTask> activeTransfers = new ConcurrentHashMap<>();

    private final ExecutorService transferExecutor;
    private volatile File destinationDirectory = new File(Constants.DEFAULT_DOWNLOAD_DIR);

    // Active session metadata
    private volatile FileMetadata activeMetadata;
    private volatile TransferState currentState = TransferState.FAILED;
    private volatile File selectedFile;

    public FileTransferManager(DefaultConnectionManager connectionManager) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
        this.transferExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "file-transfer-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        // Register binary socket consumer with FileDataListener on Port 8889
        if (connectionManager.getFileDataListener() != null) {
            connectionManager.getFileDataListener().setConnectionHandler(this::handleIncomingDataSocket);
        }
    }

    /**
     * Validates a selected local file and initiates a FILE_REQUEST offer to the connected peer.
     * Computes the source file's SHA-256 checksum incrementally via streaming chunks.
     *
     * @param file Local file selected by user
     * @return Created FileMetadata with SHA-256
     * @throws IOException on validation error or network failure
     */
    public FileMetadata sendFileRequest(File file) throws IOException {
        return sendFileRequest(file, null);
    }

    /**
     * Validates a selected local file, computes SHA-256 with a progress callback, and sends FILE_REQUEST.
     *
     * @param file         Local file selected by user
     * @param hashCallback Optional progress callback for hash calculation (may be null)
     * @return Created FileMetadata with SHA-256
     * @throws IOException on validation error or network failure
     */
    public FileMetadata sendFileRequest(File file, HashVerifier.ProgressCallback hashCallback) throws IOException {
        Objects.requireNonNull(file, "File must not be null");

        // 1. Validate connection
        PeerSession session = connectionManager.getActiveSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("Không thể gửi tệp: Chưa kết nối tới người nhận.");
        }

        // 2. Validate file existence and readability
        if (!file.exists()) {
            throw new FileNotFoundException("Tệp tin không tồn tại: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Đường dẫn đã chọn không phải là tệp hợp lệ (có thể là thư mục).");
        }
        if (!file.canRead()) {
            throw new IOException("Không có quyền đọc tệp tin: " + file.getName());
        }

        long fileSize = file.length(); // 64-bit long for 10 GB+ support
        if (fileSize < 0L) {
            throw new IOException("Kích thước tệp tin không hợp lệ: " + fileSize);
        }

        String fileName = FileMetadata.sanitizeFileName(file.getName());
        String transferId = UUID.randomUUID().toString();
        String senderName = connectionManager.getLocalPeerInfo().getDisplayName();
        PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
        String receiverName = remotePeer != null ? remotePeer.getDisplayName() : "Peer";

        // Phase 7: Calculate SHA-256 incrementally via streaming
        System.out.println("[INFO] SHA-256 calculation started");
        String sha256 = HashVerifier.computeSha256(file, hashCallback);
        System.out.println("[INFO] SHA-256 calculation completed");
        System.out.printf("[INFO] Expected SHA-256: %s%n", sha256);

        FileMetadata metadata = new FileMetadata(
                transferId,
                fileName,
                fileSize,
                sha256,
                0L,
                senderName,
                receiverName,
                System.currentTimeMillis()
        );

        this.selectedFile = file;
        this.activeMetadata = metadata;
        this.processedTransferIds.add(transferId);

        // 3. Serialize and send FILE_TRANSFER_REQUEST on the control connection
        session.sendPacket(PacketType.FILE_TRANSFER_REQUEST, metadata.toJson());
        System.out.printf("[INFO] File request created: %s (%s) [ID: %s, SHA-256: %s]%n",
                fileName, FormatUtils.formatFileSize(fileSize), transferId, sha256);
        System.out.println("[INFO] FILE_REQUEST sent to peer");

        transitionState(metadata, TransferState.PENDING, "Đang chờ người nhận phản hồi...");
        return metadata;
    }

    /**
     * Asynchronously computes source file SHA-256 in a background worker and sends FILE_REQUEST,
     * ensuring the Swing EDT is never blocked.
     *
     * @param file         Local file to send
     * @param hashCallback Optional progress callback for hash calculation
     * @param callback     Optional completion/error callback
     */
    public void sendFileRequestAsync(File file, HashVerifier.ProgressCallback hashCallback, TransferInitiationCallback callback) {
        transferExecutor.submit(() -> {
            try {
                FileMetadata meta = sendFileRequest(file, hashCallback);
                if (callback != null) {
                    callback.onSuccess(meta);
                }
            } catch (Exception ex) {
                if (callback != null) {
                    callback.onError(ex);
                }
            }
        });
    }

    /**
     * Callback interface for asynchronous file request preparation.
     */
    public interface TransferInitiationCallback {
        void onSuccess(FileMetadata metadata);
        void onError(Exception error);
    }

    /**
     * Accepts an incoming file request.
     *
     * @param transferId Unique ID of the transfer
     * @throws IOException on network send error
     */
    public void acceptFileRequest(String transferId) throws IOException {
        PeerSession session = connectionManager.getActiveSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("Không thể phản hồi: Đã mất kết nối tới người gửi.");
        }

        FileMetadata meta = activeMetadata;
        if (meta == null || !meta.getTransferId().equals(transferId)) {
            throw new IllegalStateException("Không tìm thấy yêu cầu tệp phù hợp: " + transferId);
        }

        // Register offer so incoming binary connection on Port 8889 can be validated
        acceptedTransfers.put(transferId, meta);

        FileNegotiationPayload payload = FileNegotiationPayload.createAccept(
                transferId,
                meta.getSender(),
                meta.getReceiver(),
                meta.getFileName(),
                meta.getFileSize()
        );

        session.sendPacket(PacketType.FILE_TRANSFER_ACCEPT, payload.toJson());
        System.out.printf("[INFO] File accepted: %s [ID: %s]%n", meta.getFileName(), transferId);
        System.out.println("[INFO] Transfer ready (Awaiting binary data connection on Port 8889)");

        transitionState(meta, TransferState.READY_TO_TRANSFER, "Đã chấp nhận tệp. Đang chờ kết nối dữ liệu...");
    }

    /**
     * Rejects an incoming file request.
     *
     * @param transferId Unique ID of the transfer
     * @param reason     Human-readable reason for rejection
     * @throws IOException on network send error
     */
    public void rejectFileRequest(String transferId, String reason) throws IOException {
        PeerSession session = connectionManager.getActiveSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("Không thể phản hồi: Đã mất kết nối tới người gửi.");
        }

        FileMetadata meta = activeMetadata;
        if (meta == null || !meta.getTransferId().equals(transferId)) {
            return;
        }

        acceptedTransfers.remove(transferId);

        FileNegotiationPayload payload = FileNegotiationPayload.createReject(
                transferId,
                meta.getSender(),
                meta.getReceiver(),
                reason != null ? reason : "Bị từ chối bởi người nhận"
        );

        session.sendPacket(PacketType.FILE_TRANSFER_REJECT, payload.toJson());
        System.out.printf("[INFO] File rejected: %s [Reason: %s]%n", meta.getFileName(), payload.getReason());

        transitionState(meta, TransferState.REJECTED, "Đã từ chối tệp.");
    }

    public void acceptFileRequest(FileMetadata meta) throws IOException {
        if (meta != null) {
            acceptFileRequest(meta.getTransferId());
        }
    }

    public void rejectFileRequest(FileMetadata meta, String reason) throws IOException {
        if (meta != null) {
            rejectFileRequest(meta.getTransferId(), reason);
        }
    }

    /**
     * Dispatches an incoming FILE_TRANSFER_REQUEST packet received by the control connection reader.
     */
    public void handleIncomingFileRequest(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileMetadata metadata = FileMetadata.fromJson(json);

            // Duplicate request protection
            if (processedTransferIds.contains(metadata.getTransferId())) {
                System.out.printf("[WARN] Duplicate FILE_REQUEST received for ID %s. Ignored.%n", metadata.getTransferId());
                return;
            }
            processedTransferIds.add(metadata.getTransferId());

            this.activeMetadata = metadata;
            System.out.printf("[INFO] FILE_REQUEST received: %s (%s) from %s%n",
                    metadata.getFileName(), FormatUtils.formatFileSize(metadata.getFileSize()), metadata.getSender());

            transitionState(metadata, TransferState.PENDING, "Nhận được yêu cầu gửi tệp từ: " + metadata.getSender());

            for (FileTransferListener listener : listeners) {
                listener.onIncomingFileRequest(metadata);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to parse incoming FILE_REQUEST: " + e.getMessage());
        }
    }

    /**
     * Dispatches an incoming FILE_TRANSFER_ACCEPT packet received by the control connection reader.
     * Automatically initiates binary streaming on Port 8889 if the local file is present.
     */
    public void handleIncomingFileAccept(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileNegotiationPayload decision = FileNegotiationPayload.fromJson(json);

            FileMetadata meta = activeMetadata;
            if (meta != null && meta.getTransferId().equals(decision.getTransferId())) {
                System.out.printf("[INFO] FILE_ACCEPT received for %s. Peer accepted the file.%n", meta.getFileName());
                System.out.println("[INFO] Transfer ready -> Initiating binary streaming engine on Port 8889...");

                transitionState(meta, TransferState.READY_TO_TRANSFER, "Người nhận đã chấp nhận tệp. Khởi động truyền...");
                for (FileTransferListener listener : listeners) {
                    listener.onFileRequestAccepted(meta);
                }

                // Phase 5: Automatically launch binary streaming to peer's Port 8889
                PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
                File file = selectedFile;
                if (remotePeer != null && file != null && file.exists()) {
                    startSendingFile(meta, file, remotePeer.getIpAddress(), remotePeer.getFilePort(), 0L);
                } else {
                    System.err.println("[ERROR] Cannot start streaming: remote peer or local file unavailable.");
                }
            } else {
                System.err.println("[WARN] FILE_ACCEPT received for unknown or expired transfer: " + decision.getTransferId());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to parse FILE_ACCEPT: " + e.getMessage());
        }
    }

    /**
     * Dispatches an incoming FILE_TRANSFER_REJECT packet received by the control connection reader.
     */
    public void handleIncomingFileReject(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileNegotiationPayload decision = FileNegotiationPayload.fromJson(json);

            FileMetadata meta = activeMetadata;
            if (meta != null && meta.getTransferId().equals(decision.getTransferId())) {
                System.out.printf("[INFO] FILE_REJECT received for %s. Peer rejected: %s%n",
                        meta.getFileName(), decision.getReason());

                transitionState(meta, TransferState.REJECTED, "Người nhận đã từ chối tệp.");
                for (FileTransferListener listener : listeners) {
                    listener.onFileRequestRejected(meta, decision.getReason());
                }
            } else {
                System.err.println("[WARN] FILE_REJECT received for unknown transfer: " + decision.getTransferId());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to parse FILE_REJECT: " + e.getMessage());
        }
    }

    /**
     * Spawns a background {@link FileSender} worker to stream binary data to the remote peer.
     */
    public void startSendingFile(FileMetadata meta, File file, String targetIp, int targetPort, long startingOffset) {
        FileSender sender = new FileSender(file, meta, targetIp, targetPort, startingOffset, this, this);
        activeTransfers.put(meta.getTransferId(), sender);
        transferExecutor.submit(sender);
    }

    /**
     * Callback dispatched by {@link dammtp2p.core.network.FileDataListener} when an incoming
     * binary socket connects on Port 8889.
     */
    public void handleIncomingDataSocket(Socket socket) {
        FileReceiver receiver = new FileReceiver(socket, getDestinationDirectory(), this, this);
        transferExecutor.submit(receiver);
    }

    /**
     * Handles peer disconnection: cancels all active transfer tasks and transitions state cleanly.
     * In Phase 6, active streaming or resuming transfers transition to INTERRUPTED (preserving .part files).
     */
    public void handlePeerDisconnected() {
        boolean wasStreaming = (currentState == TransferState.TRANSFERRING || currentState == TransferState.RESUMING
                || currentState == TransferState.WAITING_FOR_RESUME);

        for (TransferTask task : activeTransfers.values()) {
            task.cancel();
        }
        activeTransfers.clear();
        acceptedTransfers.clear();

        if (wasStreaming && activeMetadata != null) {
            transitionState(activeMetadata, TransferState.INTERRUPTED,
                    "Mất kết nối bạn bè. Tệp tạm .part đã được lưu. Sẵn sàng tiếp tục (Resume) khi kết nối lại.");
        } else if (currentState == TransferState.PENDING || currentState == TransferState.READY_TO_TRANSFER) {
            transitionState(activeMetadata, TransferState.INTERRUPTED, "Mất kết nối trước khi truyền tệp.");
        }
    }

    public void cancelAllActiveTransfers(String reason) {
        for (TransferTask task : activeTransfers.values()) {
            task.cancel();
        }
        activeTransfers.clear();
        acceptedTransfers.clear();
        if (activeMetadata != null && !currentState.isTerminal()) {
            cancelTransfer(activeMetadata.getTransferId());
        }
    }

    // === FileTransferService Implementation ===

    @Override
    public FileMetadata offerFile(File localFile) throws IOException {
        return sendFileRequest(localFile);
    }

    @Override
    public void acceptTransfer(String transferId, File destinationDirectory, long resumeOffset) throws IOException {
        if (destinationDirectory != null) {
            this.destinationDirectory = destinationDirectory;
        }
        acceptFileRequest(transferId);
    }

    @Override
    public void rejectTransfer(String transferId, String reason) throws IOException {
        rejectFileRequest(transferId, reason);
    }

    @Override
    public void cancelTransfer(String transferId) {
        TransferTask task = activeTransfers.get(transferId);
        if (task != null) {
            task.cancel();
            activeTransfers.remove(transferId);
        }
        acceptedTransfers.remove(transferId);

        // Send FILE_TRANSFER_CANCEL over control channel if connection is active
        PeerSession session = connectionManager.getActiveSession();
        if (session != null && session.isConnected()) {
            try {
                String senderName = connectionManager.getLocalPeerInfo().getDisplayName();
                PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
                String receiverName = remotePeer != null ? remotePeer.getDisplayName() : "Peer";
                FileNegotiationPayload payload = FileNegotiationPayload.createCancel(
                        transferId, senderName, receiverName, "Hủy bởi người dùng"
                );
                session.sendPacket(PacketType.FILE_TRANSFER_CANCEL, payload.toJson());
                System.out.printf("[INFO] Sent FILE_TRANSFER_CANCEL for %s%n", transferId);
            } catch (Exception e) {
                System.err.println("[WARN] Failed to send FILE_TRANSFER_CANCEL packet: " + e.getMessage());
            }
        }

        FileMetadata meta = activeMetadata;
        if (meta != null && meta.getTransferId().equals(transferId)) {
            transitionState(meta, TransferState.CANCELED, "Đã hủy chuyển tệp.");
        }
    }

    public void handleIncomingFileCancel(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileNegotiationPayload cancelInfo = FileNegotiationPayload.fromJson(json);
            String tid = cancelInfo.getTransferId();

            TransferTask task = activeTransfers.get(tid);
            if (task != null) {
                task.cancel();
                activeTransfers.remove(tid);
            }
            acceptedTransfers.remove(tid);

            FileMetadata meta = activeMetadata;
            if (meta != null && meta.getTransferId().equals(tid)) {
                System.out.printf("[INFO] Received FILE_TRANSFER_CANCEL for %s: %s%n",
                        meta.getFileName(), cancelInfo.getReason());
                transitionState(meta, TransferState.CANCELED, "Đối phương đã hủy chuyển tệp: " + cancelInfo.getReason());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to handle incoming FILE_TRANSFER_CANCEL: " + e.getMessage());
        }
    }

    public void notifyTransferInterrupted(FileMetadata metadata, long transferredBytes, String message) {
        FileMetadata updated = metadata;
        if (metadata != null) {
            updated = metadata.withOffset(transferredBytes);
            if (activeMetadata != null && activeMetadata.getTransferId().equals(metadata.getTransferId())) {
                activeMetadata = activeMetadata.withOffset(transferredBytes);
                updated = activeMetadata;
            }
        }
        transitionState(updated, TransferState.INTERRUPTED, message);
    }

    public void cancelTransfer() {
        if (activeMetadata != null) {
            cancelTransfer(activeMetadata.getTransferId());
        }
    }

    public void requestResume() throws IOException {
        if (activeMetadata != null) {
            requestResume(activeMetadata.getTransferId());
        } else {
            throw new IOException("Không có phiên truyền tệp nào đang chờ tiếp tục.");
        }
    }

    /**
     * User initiates resume negotiation over the control channel (Port 8888).
     */
    public void requestResume(String transferId) throws IOException {
        PeerSession session = connectionManager.getActiveSession();
        if (session == null || !session.isConnected()) {
            throw new IOException("Không thể tiếp tục truyền: Chưa kết nối lại tới người dùng.");
        }

        FileMetadata meta = activeMetadata;
        if (meta == null || !meta.getTransferId().equals(transferId)) {
            throw new IllegalStateException("Không tìm thấy phiên truyền để tiếp tục: " + transferId);
        }

        if (currentState != TransferState.INTERRUPTED && currentState != TransferState.WAITING_FOR_RESUME) {
            throw new IllegalStateException("Trạng thái hiện tại không thể tiếp tục (Resume): " + currentState);
        }

        String localName = connectionManager.getLocalPeerInfo().getDisplayName();
        PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
        String remoteName = remotePeer != null ? remotePeer.getDisplayName() : "Peer";

        if (selectedFile != null && selectedFile.exists()) {
            // Source file validation on resume
            if (!selectedFile.isFile() || !selectedFile.canRead()) {
                transitionState(meta, TransferState.FAILED, "Tệp nguồn không còn khả dụng hoặc không có quyền đọc.");
                throw new IOException("Tệp nguồn không thể đọc: " + selectedFile.getAbsolutePath());
            }
            if (selectedFile.length() != meta.getFileSize()) {
                String errMsg = String.format("Tệp nguồn đã bị thay đổi kích thước sau khi bắt đầu truyền (hiện tại: %d, ban đầu: %d).",
                        selectedFile.length(), meta.getFileSize());
                transitionState(meta, TransferState.FAILED, "❌ " + errMsg);
                throw new IOException(errMsg);
            }

            // Phase 7: Validate source file content integrity before resume
            if (meta.getSha256() != null && !meta.getSha256().isEmpty()) {
                String currentHash = HashVerifier.computeSha256(selectedFile);
                if (!currentHash.equalsIgnoreCase(meta.getSha256())) {
                    String errMsg = "Tệp nguồn đã bị thay đổi nội dung (sai mã SHA-256) trước khi tiếp tục truyền!";
                    System.err.println("[WARN] Resume rejected: " + errMsg);
                    transitionState(meta, TransferState.FAILED, "❌ " + errMsg);
                    throw new IOException(errMsg);
                }
            }

            transitionState(meta, TransferState.WAITING_FOR_RESUME, "Đang gửi yêu cầu tiếp tục truyền tệp...");
            FileResumePayload req = FileResumePayload.createRequest(transferId, meta.getFileName(), meta.getFileSize(), localName, remoteName);
            session.sendPacket(PacketType.FILE_TRANSFER_RESUME_REQ, req.toJson());
            System.out.printf("[INFO] Resume requested for %s [ID: %s]%n", meta.getFileName(), transferId);

        } else {
            // Receiver initiates resume
            String safeName = FileMetadata.sanitizeFileName(meta.getFileName());
            File part = new File(getDestinationDirectory(), safeName + ".part");
            long offset = part.exists() ? part.length() : 0L;

            if (offset > meta.getFileSize()) {
                transitionState(meta, TransferState.FAILED, "Tệp tạm .part lớn hơn kích thước tệp nguồn dự kiến.");
                throw new IOException("Tệp tạm .part không hợp lệ (" + offset + " > " + meta.getFileSize() + ")");
            }

            transitionState(meta, TransferState.WAITING_FOR_RESUME,
                    "Đang gửi yêu cầu tiếp tục từ phía người nhận (offset: " + FormatUtils.formatFileSize(offset) + ")...");
            FileResumePayload req = FileResumePayload.createAck(transferId, meta.getFileName(), meta.getFileSize(), offset, localName, remoteName);
            session.sendPacket(PacketType.FILE_TRANSFER_RESUME_REQ, req.toJson());
            System.out.printf("[INFO] Receiver resume requested for %s [Offset: %d]%n", meta.getFileName(), offset);
        }
    }

    public void handleIncomingResumeRequest(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileResumePayload req = FileResumePayload.fromJson(json);
            String tid = req.getTransferId();

            PeerSession session = connectionManager.getActiveSession();
            if (session == null || !session.isConnected()) {
                System.err.println("[WARN] Cannot handle resume request: no active session.");
                return;
            }

            FileMetadata meta = activeMetadata;
            if (meta == null || !meta.getTransferId().equals(tid)) {
                FileResumePayload reject = FileResumePayload.createReject(tid, req.getFileName(), req.getFileSize(),
                        FileResumePayload.STATUS_REJECTED_NOT_FOUND, "Không tìm thấy phiên truyền tương ứng",
                        req.getReceiver(), req.getSender());
                session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                return;
            }

            String localName = connectionManager.getLocalPeerInfo().getDisplayName();
            PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
            String remoteName = remotePeer != null ? remotePeer.getDisplayName() : "Peer";

            if (selectedFile != null && selectedFile.exists()) {
                // Sender validates source file
                if (!selectedFile.isFile() || !selectedFile.canRead() || selectedFile.length() != meta.getFileSize()) {
                    String reason = "Tệp nguồn đã bị thay đổi kích thước hoặc không thể đọc";
                    System.err.println("[WARN] Resume rejected: " + reason);
                    FileResumePayload reject = FileResumePayload.createReject(tid, meta.getFileName(), meta.getFileSize(),
                            FileResumePayload.STATUS_REJECTED_SOURCE_CHANGED, reason, localName, remoteName);
                    session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                    transitionState(meta, TransferState.FAILED, "❌ " + reason);
                    return;
                }

                // Phase 7: Verify source file content before resuming stream
                if (meta.getSha256() != null && !meta.getSha256().isEmpty()) {
                    String currentHash = HashVerifier.computeSha256(selectedFile);
                    if (!currentHash.equalsIgnoreCase(meta.getSha256())) {
                        String reason = "Tệp nguồn đã bị thay đổi nội dung (sai lệch SHA-256)";
                        System.err.println("[WARN] Resume rejected: " + reason);
                        FileResumePayload reject = FileResumePayload.createReject(tid, meta.getFileName(), meta.getFileSize(),
                                FileResumePayload.STATUS_REJECTED_SOURCE_CHANGED, reason, localName, remoteName);
                        session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                        transitionState(meta, TransferState.FAILED, "❌ " + reason);
                        return;
                    }
                }

                long agreedOffset = req.getResumeOffset();
                if (agreedOffset < 0L || agreedOffset > meta.getFileSize()) {
                    String reason = "Vị trí resumeOffset không hợp lệ: " + agreedOffset;
                    System.err.println("[WARN] Resume rejected: " + reason);
                    FileResumePayload reject = FileResumePayload.createReject(tid, meta.getFileName(), meta.getFileSize(),
                            FileResumePayload.STATUS_REJECTED_INVALID_OFFSET, reason, localName, remoteName);
                    session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                    transitionState(meta, TransferState.FAILED, "❌ " + reason);
                    return;
                }

                FileResumePayload ack = FileResumePayload.createAck(tid, meta.getFileName(), meta.getFileSize(),
                        agreedOffset, localName, remoteName);
                session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, ack.toJson());

                if (agreedOffset == meta.getFileSize()) {
                    transitionState(meta, TransferState.COMPLETED, "✅ Tệp đã được truyền hoàn tất.");
                    return;
                }

                transitionState(meta, TransferState.RESUMING,
                        "Đã xác nhận tiếp tục truyền từ: " + FormatUtils.formatFileSize(agreedOffset));
                System.out.printf("[INFO] Resume offset accepted: %d bytes. Launching sender stream.%n", agreedOffset);

                if (remotePeer != null) {
                    startSendingFile(meta, selectedFile, remotePeer.getIpAddress(), remotePeer.getFilePort(), agreedOffset);
                }

            } else {
                // Receiver validates partial file
                String safeName = FileMetadata.sanitizeFileName(meta.getFileName());
                File part = new File(getDestinationDirectory(), safeName + ".part");
                long offset = part.exists() ? part.length() : 0L;

                if (offset < 0L || offset > meta.getFileSize()) {
                    String reason = "Tệp tạm .part không hợp lệ (" + offset + " bytes)";
                    System.err.println("[WARN] Resume rejected: " + reason);
                    FileResumePayload reject = FileResumePayload.createReject(tid, meta.getFileName(), meta.getFileSize(),
                            FileResumePayload.STATUS_REJECTED_INVALID_OFFSET, reason, localName, remoteName);
                    session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                    transitionState(meta, TransferState.FAILED, "❌ " + reason);
                    return;
                }

                if (offset == meta.getFileSize()) {
                    // Receiver MUST verify SHA-256 before renaming!
                    String expectedSha256 = meta.getSha256();
                    if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                        System.out.println("[INFO] Received file SHA-256 calculation started");
                        String receiverHash = HashVerifier.computeSha256(part);
                        System.out.printf("[INFO] Received file SHA-256 calculation completed: %s%n", receiverHash);
                        System.out.printf("[INFO] Expected SHA-256: %s%n", expectedSha256);

                        if (!receiverHash.equalsIgnoreCase(expectedSha256)) {
                            System.err.println("[WARNING] SHA-256 verification failed");
                            System.err.println("[WARNING] File integrity mismatch");
                            String reason = "Tệp tạm đã đủ kích thước nhưng sai mã SHA-256 (bị hỏng)";
                            FileResumePayload reject = FileResumePayload.createReject(tid, meta.getFileName(), meta.getFileSize(),
                                    FileResumePayload.STATUS_REJECTED_INTEGRITY_MISMATCH, reason, localName, remoteName);
                            session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, reject.toJson());
                            transitionState(meta, TransferState.FAILED, "❌ " + reason);
                            return; // DO NOT rename .part
                        }
                        System.out.println("[INFO] SHA-256 verification successful");
                    }

                    File uniqueFinalFile = HashVerifier.resolveUniqueDestinationFile(getDestinationDirectory(), safeName);
                    boolean renamed = part.renameTo(uniqueFinalFile);
                    if (!renamed) {
                        try {
                            java.nio.file.Files.move(part.toPath(), uniqueFinalFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            System.err.println("[ERROR] Failed to rename .part to final destination: " + ex.getMessage());
                        }
                    }

                    FileResumePayload ack = FileResumePayload.createAck(tid, meta.getFileName(), meta.getFileSize(),
                            offset, localName, remoteName);
                    session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, ack.toJson());
                    transitionState(meta, TransferState.COMPLETED, "✅ Tệp đã được nhận hoàn tất và xác minh SHA-256: " + uniqueFinalFile.getName());
                    return;
                }

                acceptedTransfers.put(tid, meta);

                FileResumePayload ack = FileResumePayload.createAck(tid, meta.getFileName(), meta.getFileSize(),
                        offset, localName, remoteName);
                session.sendPacket(PacketType.FILE_TRANSFER_RESUME_ACK, ack.toJson());

                transitionState(meta, TransferState.RESUMING,
                        "Đã chấp thuận tiếp tục từ " + FormatUtils.formatFileSize(offset) + ". Đang chờ kết nối dữ liệu...");
                System.out.printf("[INFO] Resume offset accepted: %d bytes for %s%n", offset, meta.getFileName());
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to handle incoming FILE_TRANSFER_RESUME_REQ: " + e.getMessage());
        }
    }

    public void handleIncomingResumeAck(byte[] payload) {
        try {
            String json = new String(payload, ProtocolConstants.PROTOCOL_CHARSET);
            FileResumePayload ack = FileResumePayload.fromJson(json);
            String tid = ack.getTransferId();

            FileMetadata meta = activeMetadata;
            if (meta == null || !meta.getTransferId().equals(tid)) {
                System.err.println("[WARN] Received resume ACK for unknown transfer: " + tid);
                return;
            }

            if (!ack.isAccepted()) {
                System.err.println("[WARN] Resume request rejected by peer: " + ack.getReason());
                transitionState(meta, TransferState.FAILED, "❌ Yêu cầu tiếp tục bị từ chối: " + ack.getReason());
                return;
            }

            long offset = ack.getResumeOffset();
            if (offset < 0L || offset > meta.getFileSize()) {
                System.err.println("[ERROR] Peer returned invalid resume offset: " + offset);
                transitionState(meta, TransferState.FAILED, "❌ Lỗi giao thức: Offset không hợp lệ (" + offset + ")");
                return;
            }

            if (selectedFile != null && selectedFile.exists()) {
                // Sender validation
                if (selectedFile.length() != meta.getFileSize()) {
                    String reason = "Tệp nguồn đã bị thay đổi kích thước sau khi bắt đầu truyền";
                    System.err.println("[ERROR] " + reason);
                    cancelTransfer(tid);
                    transitionState(meta, TransferState.FAILED, "❌ " + reason);
                    return;
                }

                // Phase 7: Verify source file content before resuming stream
                if (meta.getSha256() != null && !meta.getSha256().isEmpty()) {
                    String currentHash = HashVerifier.computeSha256(selectedFile);
                    if (!currentHash.equalsIgnoreCase(meta.getSha256())) {
                        String reason = "Tệp nguồn đã bị thay đổi nội dung trước khi tiếp tục truyền";
                        System.err.println("[ERROR] " + reason);
                        cancelTransfer(tid);
                        transitionState(meta, TransferState.FAILED, "❌ " + reason);
                        return;
                    }
                }

                if (offset == meta.getFileSize()) {
                    transitionState(meta, TransferState.COMPLETED, "✅ Tệp đã được truyền hoàn tất.");
                    return;
                }

                System.out.printf("[INFO] Peer confirmed resume from offset: %d (%s). Connecting data channel...%n",
                        offset, FormatUtils.formatFileSize(offset));
                transitionState(meta, TransferState.RESUMING,
                        "Đang tiếp tục truyền từ: " + FormatUtils.formatFileSize(offset));

                PeerInfo remotePeer = connectionManager.getRemotePeerInfo();
                if (remotePeer != null) {
                    startSendingFile(meta, selectedFile, remotePeer.getIpAddress(), remotePeer.getFilePort(), offset);
                }

            } else {
                // Receiver awaiting connection
                acceptedTransfers.put(tid, meta);
                transitionState(meta, TransferState.RESUMING,
                        "Đã xác nhận tiếp tục nhận từ " + FormatUtils.formatFileSize(offset) + ". Đang chờ dữ liệu...");
                System.out.printf("[INFO] Receiver awaiting resumed data on Port 8889 from offset %d%n", offset);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to handle incoming FILE_TRANSFER_RESUME_ACK: " + e.getMessage());
        }
    }

    @Override
    public void pauseTransfer(String transferId) {
        TransferTask task = activeTransfers.get(transferId);
        if (task != null) {
            task.pause();
        }
    }

    @Override
    public void resumeTransfer(String transferId) {
        try {
            requestResume(transferId);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to resume transfer: " + e.getMessage());
        }
    }

    @Override
    public void addProgressListener(TransferProgressListener listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    @Override
    public void removeProgressListener(TransferProgressListener listener) {
        if (listener != null) {
            progressListeners.remove(listener);
        }
    }

    // === TransferProgressListener Forwarding ===

    @Override
    public void onProgress(FileMetadata metadata, long transferredBytes, long totalBytes, double bytesPerSecond) {
        for (TransferProgressListener l : progressListeners) {
            l.onProgress(metadata, transferredBytes, totalBytes, bytesPerSecond);
        }
    }

    @Override
    public void onProgress(FileMetadata metadata, long transferredBytes, long totalBytes, double bytesPerSecond, long etaSeconds) {
        for (TransferProgressListener l : progressListeners) {
            l.onProgress(metadata, transferredBytes, totalBytes, bytesPerSecond, etaSeconds);
        }
    }

    @Override
    public void onTransferStarted(FileMetadata metadata) {
        for (TransferProgressListener l : progressListeners) {
            l.onTransferStarted(metadata);
        }
    }

    @Override
    public void onStateChanged(FileMetadata metadata, TransferState oldState, TransferState newState) {
        for (TransferProgressListener l : progressListeners) {
            l.onStateChanged(metadata, oldState, newState);
        }
    }

    @Override
    public void onCompleted(FileMetadata metadata) {
        for (TransferProgressListener l : progressListeners) {
            l.onCompleted(metadata);
        }
    }

    @Override
    public void onError(FileMetadata metadata, String errorMessage, Throwable cause) {
        for (TransferProgressListener l : progressListeners) {
            l.onError(metadata, errorMessage, cause);
        }
    }

    public void notifyStateChanged(FileMetadata metadata, TransferState newState, String message) {
        transitionState(metadata, newState, message);
    }

    private void transitionState(FileMetadata meta, TransferState newState, String message) {
        TransferState oldState = this.currentState;
        if (!oldState.canTransitionTo(newState) && !newState.isTerminal() && newState != TransferState.INTERRUPTED) {
            System.err.printf("[WARN] Invalid state transition attempted: %s -> %s for %s%n",
                    oldState, newState, meta != null ? meta.getFileName() : "Unknown");
            return;
        }

        this.currentState = newState;

        for (FileTransferListener listener : listeners) {
            listener.onTransferStateChanged(meta, newState, message);
        }
        for (TransferProgressListener listener : progressListeners) {
            listener.onStateChanged(meta, oldState, newState);
        }
    }

    // === Registry and Getters ===

    public void registerAcceptedTransfer(FileMetadata meta) {
        if (meta != null) {
            acceptedTransfers.put(meta.getTransferId(), meta);
        }
    }

    public FileMetadata getAcceptedTransfer(String transferId) {
        return acceptedTransfers.get(transferId);
    }

    public void removeAcceptedTransfer(String transferId) {
        acceptedTransfers.remove(transferId);
    }

    public void registerActiveTransfer(String transferId, TransferTask task) {
        activeTransfers.put(transferId, task);
    }

    public void unregisterActiveTransfer(String transferId) {
        activeTransfers.remove(transferId);
    }

    public TransferTask getActiveTransfer(String transferId) {
        return activeTransfers.get(transferId);
    }

    public TransferState getCurrentState() {
        return currentState;
    }

    public FileMetadata getActiveMetadata() {
        return activeMetadata;
    }

    public File getSelectedFile() {
        return selectedFile;
    }

    public File getDestinationDirectory() {
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdirs();
        }
        return destinationDirectory;
    }

    public void setDestinationDirectory(File destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
    }

    public void addTransferListener(FileTransferListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeTransferListener(FileTransferListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
