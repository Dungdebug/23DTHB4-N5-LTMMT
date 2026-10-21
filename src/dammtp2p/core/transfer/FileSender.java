package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

/**
 * Sender engine for large file transfers (10 GB+) over the dedicated binary channel (Port 8889).
 * Streams data in 64 KB chunks without loading full files into memory, tracking progress,
 * instantaneous throughput, and ETA.
 */
public class FileSender extends DefaultTransferTask implements Runnable {

    private final File localFile;
    private final String targetIp;
    private final int targetPort;
    private final long startingOffset;
    private final TransferProgressListener progressListener;
    private final FileTransferManager transferManager;

    private volatile Socket socket;
    private volatile RandomAccessFile srcFile;

    public FileSender(File localFile,
                      FileMetadata metadata,
                      String targetIp,
                      int targetPort,
                      long startingOffset,
                      TransferProgressListener progressListener,
                      FileTransferManager transferManager) {
        super(metadata, TransferState.READY_TO_TRANSFER);
        this.localFile = Objects.requireNonNull(localFile, "localFile must not be null");
        this.targetIp = Objects.requireNonNull(targetIp, "targetIp must not be null");
        this.targetPort = targetPort;
        this.startingOffset = startingOffset;
        this.progressListener = progressListener;
        this.transferManager = Objects.requireNonNull(transferManager, "transferManager must not be null");
    }

    @Override
    public void run() {
        long totalBytes = metadata.getFileSize();
        long transferred = startingOffset;
        long remaining = totalBytes - startingOffset;
        this.transferredBytes = transferred;

        setState(TransferState.TRANSFERRING);
        transferManager.notifyStateChanged(metadata, TransferState.TRANSFERRING,
                String.format("Đang kết nối cổng dữ liệu %s:%d...", targetIp, targetPort));

        long startTime = System.currentTimeMillis();
        long lastReportTime = startTime;
        long lastReportBytes = transferred;

        try {
            // 1. Pre-flight source validation
            if (!localFile.exists() || !localFile.isFile()) {
                throw new FileNotFoundException("Tệp nguồn không tồn tại: " + localFile.getAbsolutePath());
            }
            if (!localFile.canRead()) {
                throw new IOException("Không có quyền đọc tệp: " + localFile.getName());
            }
            if (localFile.length() != totalBytes) {
                throw new IOException(String.format("Kích thước tệp đã thay đổi (thực tế: %d, thỏa thuận: %d)",
                        localFile.length(), totalBytes));
            }

            // 2. Open dedicated TCP connection to peer's File Data Port
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(Constants.MAX_BUFFER_SIZE);
            socket.connect(new InetSocketAddress(targetIp, targetPort), Constants.DEFAULT_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);

            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), Constants.DEFAULT_BUFFER_SIZE));
            DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream(), Constants.DEFAULT_BUFFER_SIZE));

            // 3. Send binary file data handshake
            FileDataHandshake handshake = new FileDataHandshake(metadata.getTransferId(), totalBytes, startingOffset);
            handshake.writeTo(dos);
            System.out.printf("[INFO] File data connection started to %s:%d [ID: %s]%n",
                    targetIp, targetPort, metadata.getTransferId());

            // 4. Await receiver validation response
            FileDataHandshake.Response resp = FileDataHandshake.readResponse(dis);
            if (!resp.isOk()) {
                throw new IOException("Người nhận từ chối kết nối tệp: " + resp.getMessage());
            }
            System.out.printf("[INFO] File data handshake accepted by peer: %s%n", resp.getMessage());

            // 5. Open local file with 64-bit random access
            srcFile = new RandomAccessFile(localFile, "r");
            if (startingOffset > 0L) {
                srcFile.seek(startingOffset);
            }

            // 6. Notify transfer start
            transferManager.notifyStateChanged(metadata, TransferState.TRANSFERRING,
                    "Đang gửi tệp: " + metadata.getFileName());
            if (progressListener != null) {
                progressListener.onTransferStarted(metadata);
            }

            // 7. Binary streaming loop (64 KB chunks)
            byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
            while (remaining > 0L && !isCancelled()) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = srcFile.read(buffer, 0, toRead);
                if (read == -1) {
                    throw new EOFException("Đạt cuối tệp bất ngờ, còn thiếu " + remaining + " bytes");
                }

                dos.write(buffer, 0, read);
                transferred += read;
                remaining -= read;
                this.transferredBytes = transferred;

                long now = System.currentTimeMillis();
                long elapsedSinceReport = now - lastReportTime;
                if (elapsedSinceReport >= 250 || remaining == 0L) {
                    double speed = elapsedSinceReport > 0
                            ? (double) (transferred - lastReportBytes) / (elapsedSinceReport / 1000.0)
                            : 0.0;
                    long eta = speed > 0 ? (long) (remaining / speed) : -1L;

                    if (progressListener != null) {
                        progressListener.onProgress(metadata, transferred, totalBytes, speed, eta);
                    }
                    lastReportTime = now;
                    lastReportBytes = transferred;
                }
            }
            dos.flush();

            // 8. Handle outcome
            if (isCancelled()) {
                setState(TransferState.CANCELED);
                transferManager.notifyStateChanged(metadata, TransferState.CANCELED, "Đã hủy gửi tệp.");
                if (progressListener != null) {
                    progressListener.onError(metadata, "Người dùng đã hủy chuyển tệp.", null);
                }
                return;
            }

            // 9. Transition to VERIFYING while receiver calculates hash and confirms
            setState(TransferState.VERIFYING);
            transferManager.notifyStateChanged(metadata, TransferState.VERIFYING,
                    "Đang chờ người nhận kiểm tra tính toàn vẹn SHA-256 (Verifying SHA-256)...");

            // Disable socket timeout while awaiting SHA-256 verification on large files
            socket.setSoTimeout(0);

            // 10. Await receiver completion confirmation
            FileDataHandshake.Response finalAck = FileDataHandshake.readResponse(dis);
            if (finalAck.isComplete()) {
                setState(TransferState.COMPLETED);
                long sessionElapsed = Math.max(1, System.currentTimeMillis() - startTime);
                long sessionBytes = transferred - startingOffset;
                double avgSpeed = (double) sessionBytes / (sessionElapsed / 1000.0);

                System.out.printf("[INFO] File transfer completed & verified: %s (%s) in %s (Avg speed: %s)%n",
                        metadata.getFileName(),
                        FormatUtils.formatFileSize(totalBytes),
                        FormatUtils.formatDuration(sessionElapsed),
                        FormatUtils.formatSpeed(avgSpeed));

                transferManager.notifyStateChanged(metadata, TransferState.COMPLETED,
                        "✅ Truyền tệp hoàn tất và đã xác minh SHA-256: " + metadata.getFileName());
                if (progressListener != null) {
                    progressListener.onCompleted(metadata);
                }
            } else {
                throw new IOException("Người nhận từ chối hoàn tất (Xác minh SHA-256 thất bại): " + finalAck.getMessage());
            }

        } catch (Exception e) {
            if (!isCancelled()) {
                boolean isNetworkInterruption = isNetworkInterrupted(e);
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

                if (isNetworkInterruption && transferred < totalBytes) {
                    setState(TransferState.INTERRUPTED);
                    System.out.printf("[INFO] File transfer interrupted (%s) at %d / %d bytes (%.1f%%): %s%n",
                            metadata.getFileName(), transferred, totalBytes,
                            FormatUtils.calculatePercentage(transferred, totalBytes), errMsg);
                    transferManager.notifyTransferInterrupted(metadata, transferred,
                            "Mất kết nối mạng. Sẵn sàng tiếp tục (Resume).");
                } else {
                    setState(TransferState.FAILED);
                    System.err.printf("[ERROR] File transfer failed (%s): %s%n", metadata.getFileName(), errMsg);
                    transferManager.notifyStateChanged(metadata, TransferState.FAILED, "❌ Truyền tệp thất bại: " + errMsg);
                    if (progressListener != null) {
                        progressListener.onError(metadata, errMsg, e);
                    }
                }
            }
        } finally {
            closeQuietly();
            transferManager.unregisterActiveTransfer(transferId);
        }
    }

    private boolean isNetworkInterrupted(Exception e) {
        if (e instanceof java.net.SocketException
                || e instanceof java.net.SocketTimeoutException
                || e instanceof EOFException) {
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
        if (srcFile != null) {
            try {
                srcFile.close();
            } catch (IOException ignored) {}
            srcFile = null;
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            socket = null;
        }
    }
}
