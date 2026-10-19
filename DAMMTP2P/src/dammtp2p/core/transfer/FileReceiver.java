package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Receiver engine for large file transfers (10 GB+) over the dedicated binary channel (Port 8889).
 * Validates the data handshake against accepted control offers, streams binary chunks directly
 * to a temporary {@code .part} file, verifies exact byte count, and atomically renames the file upon success.
 */
public class FileReceiver extends DefaultTransferTask implements Runnable {

    private final Socket socket;
    private final File destinationDirectory;
    private final TransferProgressListener progressListener;
    private final FileTransferManager transferManager;

    private volatile RandomAccessFile dstFile;
    private volatile File partFile;
    private volatile File finalFile;

    public FileReceiver(Socket socket,
                        File destinationDirectory,
                        TransferProgressListener progressListener,
                        FileTransferManager transferManager) {
        super(new FileMetadata("", "", 0L, null, 0L, "", "", System.currentTimeMillis()),
                TransferState.READY_TO_TRANSFER);
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.destinationDirectory = Objects.requireNonNull(destinationDirectory, "destinationDirectory must not be null");
        this.progressListener = progressListener;
        this.transferManager = Objects.requireNonNull(transferManager, "transferManager must not be null");
    }

    @Override
    public void run() {
        DataInputStream dis = null;
        DataOutputStream dos = null;
        FileMetadata expectedMeta = null;

        try {
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(Constants.MAX_BUFFER_SIZE);
            socket.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);

            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream(), Constants.DEFAULT_BUFFER_SIZE));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), Constants.DEFAULT_BUFFER_SIZE));

            // 1. Read binary handshake on port 8889
            FileDataHandshake handshake = FileDataHandshake.readFrom(dis);
            String tid = handshake.getTransferId();

            // 2. Validate against accepted transfer metadata from Port 8888 negotiation
            expectedMeta = transferManager.getAcceptedTransfer(tid);
            if (expectedMeta == null) {
                String errMsg = "Yêu cầu tệp không tồn tại hoặc chưa được chấp nhận: " + tid;
                System.err.println("[WARN] File data connection rejected: " + errMsg);
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, errMsg);
                return;
            }

            if (expectedMeta.getFileSize() != handshake.getFileSize()) {
                String errMsg = String.format("Kích thước tệp không khớp (nhận %d, dự kiến %d)",
                        handshake.getFileSize(), expectedMeta.getFileSize());
                System.err.println("[WARN] File data connection rejected: " + errMsg);
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, errMsg);
                return;
            }

            // Check starting offset
            long startingOffset = handshake.getStartingOffset();
            if (startingOffset < 0L || startingOffset > expectedMeta.getFileSize()) {
                String errMsg = String.format("Vị trí offset không hợp lệ: %d (Kích thước tệp: %d)",
                        startingOffset, expectedMeta.getFileSize());
                System.err.println("[WARN] File data connection rejected: " + errMsg);
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, errMsg);
                return;
            }

            // 3. Ensure destination directory exists and is writable
            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs();
            }
            if (!destinationDirectory.canWrite()) {
                String errMsg = "Thư mục đích không có quyền ghi: " + destinationDirectory.getAbsolutePath();
                System.err.println("[ERROR] " + errMsg);
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, errMsg);
                return;
            }

            // 4. Update task metadata and register as active transfer
            setMetadata(expectedMeta);
            transferManager.registerActiveTransfer(tid, this);

            // 5. Send ACK to sender
            FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_OK, "Sẵn sàng nhận dữ liệu");
            System.out.printf("[INFO] Handshake accepted for %s (%s) from offset %d. Starting binary reception.%n",
                    expectedMeta.getFileName(), FormatUtils.formatFileSize(expectedMeta.getFileSize()), startingOffset);

            setState(TransferState.TRANSFERRING);
            transferManager.notifyStateChanged(expectedMeta, TransferState.TRANSFERRING,
                    "Đang nhận tệp: " + expectedMeta.getFileName());
            if (progressListener != null) {
                progressListener.onTransferStarted(expectedMeta);
            }

            // 6. Prepare .part file (never write directly to final filename until complete)
            String safeFileName = FileMetadata.sanitizeFileName(expectedMeta.getFileName());
            this.partFile = new File(destinationDirectory, safeFileName + ".part");
            this.finalFile = new File(destinationDirectory, safeFileName);

            dstFile = new RandomAccessFile(partFile, "rw");
            if (startingOffset > 0L) {
                dstFile.seek(startingOffset);
            }

            long totalBytes = expectedMeta.getFileSize();
            long received = startingOffset;
            long remaining = totalBytes - startingOffset;
            this.transferredBytes = received;

            long startTime = System.currentTimeMillis();
            long lastReportTime = startTime;
            long lastReportBytes = received;

            // 7. Binary streaming loop (64 KB chunks)
            byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
            while (remaining > 0L && !isCancelled()) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = dis.read(buffer, 0, toRead);
                if (read == -1) {
                    break; // Premature EOF from sender (network drop)
                }

                dstFile.write(buffer, 0, read);
                received += read;
                remaining -= read;
                this.transferredBytes = received;

                long now = System.currentTimeMillis();
                long elapsedSinceReport = now - lastReportTime;
                if (elapsedSinceReport >= 250 || remaining == 0L) {
                    double speed = elapsedSinceReport > 0
                            ? (double) (received - lastReportBytes) / (elapsedSinceReport / 1000.0)
                            : 0.0;
                    long eta = speed > 0 ? (long) (remaining / speed) : -1L;

                    if (progressListener != null) {
                        progressListener.onProgress(expectedMeta, received, totalBytes, speed, eta);
                    }
                    lastReportTime = now;
                    lastReportBytes = received;
                }
            }

            // 8. Close file handle before verification / renaming
            if (dstFile != null) {
                try {
                    dstFile.close();
                } catch (IOException ignored) {}
                dstFile = null;
            }

            // 9. Exact byte count verification
            if (isCancelled()) {
                setState(TransferState.CANCELED);
                transferManager.notifyStateChanged(expectedMeta, TransferState.CANCELED, "Đã hủy nhận tệp.");
                if (progressListener != null) {
                    progressListener.onError(expectedMeta, "Người dùng đã hủy nhận tệp.", null);
                }
                return;
            }

            if (received == totalBytes) {
                // 1. Ensure file handle is completely closed before calculating hash
                if (dstFile != null) {
                    try { dstFile.close(); } catch (IOException ignored) {}
                    dstFile = null;
                }

                String expectedSha256 = expectedMeta.getSha256();
                String receiverHash = null;

                // 2. Perform streaming SHA-256 verification if expected hash is provided
                if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                    setState(TransferState.VERIFYING);
                    transferManager.notifyStateChanged(expectedMeta, TransferState.VERIFYING,
                            "Đang kiểm tra tính toàn vẹn SHA-256 (Verifying SHA-256)...");
                    System.out.println("[INFO] Received file SHA-256 calculation started");

                    final FileMetadata metaToVerify = expectedMeta;
                    receiverHash = HashVerifier.computeSha256(partFile, (hashed, total) -> {
                        if (progressListener != null) {
                            progressListener.onProgress(metaToVerify, hashed, total, 0.0, -1L);
                        }
                    });

                    System.out.printf("[INFO] Received file SHA-256 calculation completed: %s%n", receiverHash);
                    System.out.printf("[INFO] Expected SHA-256: %s%n", expectedSha256);

                    if (receiverHash.equalsIgnoreCase(expectedSha256.trim())) {
                        System.out.println("[INFO] SHA-256 verification successful");
                        setState(TransferState.VERIFIED);
                        transferManager.notifyStateChanged(expectedMeta, TransferState.VERIFIED,
                                "✅ Đã xác minh tính toàn vẹn SHA-256 thành công.");
                    } else {
                        System.err.printf("[WARNING] SHA-256 verification failed (expected: %s, actual: %s)%n",
                                expectedSha256, receiverHash);
                        System.err.println("[WARNING] File integrity mismatch");
                        setState(TransferState.FAILED);
                        String failMsg = "Xác minh SHA-256 thất bại: Tệp tin bị sai lệch nội dung!";
                        transferManager.notifyStateChanged(expectedMeta, TransferState.FAILED, "❌ " + failMsg);
                        FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, failMsg);
                        if (progressListener != null) {
                            progressListener.onError(expectedMeta, failMsg, null);
                        }
                        return; // CRITICAL: DO NOT rename .part to final file!
                    }
                }

                // 3. Verification SUCCESS: Resolve filename collision safely (e.g. report (1).pdf)
                File uniqueFinalFile = HashVerifier.resolveUniqueDestinationFile(destinationDirectory, safeFileName);

                boolean renamed = partFile.renameTo(uniqueFinalFile);
                if (!renamed) {
                    Files.move(partFile.toPath(), uniqueFinalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                // 4. Send completion ACK back to sender on the data channel
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_COMPLETE,
                        "Chuyển tệp hoàn tất và đã xác minh SHA-256: " + (receiverHash != null ? receiverHash : "OK"));

                setState(TransferState.COMPLETED);
                long sessionElapsed = Math.max(1, System.currentTimeMillis() - startTime);
                long sessionBytes = received - startingOffset;
                double avgSpeed = (double) sessionBytes / (sessionElapsed / 1000.0);

                System.out.printf("[INFO] File transfer completed: %s (%s) in %s -> %s (Avg speed: %s)%n",
                        uniqueFinalFile.getName(),
                        FormatUtils.formatFileSize(totalBytes),
                        FormatUtils.formatDuration(sessionElapsed),
                        uniqueFinalFile.getAbsolutePath(),
                        FormatUtils.formatSpeed(avgSpeed));

                transferManager.notifyStateChanged(expectedMeta, TransferState.COMPLETED,
                        "✅ Nhận tệp thành công: " + uniqueFinalFile.getName() + " (Đã xác minh SHA-256)");
                if (progressListener != null) {
                    progressListener.onCompleted(expectedMeta);
                }

            } else if (received < totalBytes) {
                // Incomplete transfer due to network drop: keep .part file on disk for Phase 6 resume!
                setState(TransferState.INTERRUPTED);
                String msg = String.format("Mất kết nối mạng. Đã bảo tồn tệp tạm .part (%s / %s). Sẵn sàng tiếp tục (Resume).",
                        FormatUtils.formatFileSize(received), FormatUtils.formatFileSize(totalBytes));
                System.out.printf("[INFO] File transfer interrupted: %s. Preserved .part: %d bytes (%.1f%%)%n",
                        safeFileName, received, FormatUtils.calculatePercentage(received, totalBytes));
                transferManager.notifyTransferInterrupted(expectedMeta, received, msg);
            } else {
                // Protocol error: byte count exceeded expected size
                setState(TransferState.FAILED);
                String msg = String.format("Lỗi giao thức: Nhận %d bytes vượt quá kích thước %d bytes", received, totalBytes);
                System.err.println("[ERROR] " + msg);
                transferManager.notifyStateChanged(expectedMeta, TransferState.FAILED, "❌ " + msg);
                if (progressListener != null) {
                    progressListener.onError(expectedMeta, msg, null);
                }
            }

        } catch (Exception e) {
            if (!isCancelled()) {
                boolean isNetworkInterruption = isNetworkInterrupted(e);
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                FileMetadata meta = metadata != null && !metadata.getFileName().isEmpty() ? metadata : expectedMeta;

                if (isNetworkInterruption && transferredBytes < (meta != null ? meta.getFileSize() : Long.MAX_VALUE)) {
                    setState(TransferState.INTERRUPTED);
                    System.out.printf("[INFO] File reception interrupted (%s) at %d bytes: %s%n",
                            meta != null ? meta.getFileName() : "Unknown", transferredBytes, errMsg);
                    transferManager.notifyTransferInterrupted(meta, transferredBytes,
                            "Mất kết nối mạng. Đã bảo tồn tệp tạm .part. Sẵn sàng tiếp tục (Resume).");
                } else {
                    setState(TransferState.FAILED);
                    System.err.printf("[ERROR] File reception failed (%s): %s%n",
                            meta != null ? meta.getFileName() : "Unknown", errMsg);
                    transferManager.notifyStateChanged(meta, TransferState.FAILED, "❌ Nhận tệp thất bại: " + errMsg);
                    if (progressListener != null) {
                        progressListener.onError(meta, errMsg, e);
                    }
                }
            }
        } finally {
            closeQuietly();
            String activeTid = metadata != null ? metadata.getTransferId() : (expectedMeta != null ? expectedMeta.getTransferId() : null);
            if (activeTid != null) {
                transferManager.unregisterActiveTransfer(activeTid);
                transferManager.removeAcceptedTransfer(activeTid);
            }
        }
    }

    private boolean isNetworkInterrupted(Exception e) {
        if (e instanceof java.net.SocketException
                || e instanceof java.net.SocketTimeoutException
                || e instanceof java.io.EOFException) {
            return true;
        }
        String msg = e.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            return lower.contains("connection") || lower.contains("socket")
                    || lower.contains("reset") || lower.contains("broken pipe")
                    || lower.contains("closed");
        }
        return false;
    }

    @Override
    public void cancel() {
        super.cancel();
        closeQuietly();
    }

    private void closeQuietly() {
        if (dstFile != null) {
            try {
                dstFile.close();
            } catch (IOException ignored) {}
            dstFile = null;
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public File getDestinationFile() {
        return finalFile;
    }
}
