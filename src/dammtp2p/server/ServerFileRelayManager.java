package dammtp2p.server;

import dammtp2p.core.transfer.FileDataHandshake;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side File Relay Manager listening on Port 8889.
 * Receives file data sockets from Sender and Receiver clients, pairs them by transferId,
 * and streams raw/encrypted binary chunks without buffering the full file in memory.
 */
public class ServerFileRelayManager {

    public static class ActiveTransferInfo {
        private final String transferId;
        private final String sender;
        private final String receiver;
        private final String fileName;
        private final long fileSize;
        private final long startOffset;
        private final AtomicLong transferredBytes = new AtomicLong(0);
        private volatile double speedBps = 0;
        private volatile String status = "Đang chuyển tiếp";
        private final long startTime = System.currentTimeMillis();

        public ActiveTransferInfo(String transferId, String sender, String receiver, String fileName, long fileSize, long startOffset) {
            this.transferId = transferId;
            this.sender = sender;
            this.receiver = receiver;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.startOffset = startOffset;
            this.transferredBytes.set(startOffset);
        }

        public String getTransferId() { return transferId; }
        public String getSender() { return sender; }
        public String getReceiver() { return receiver; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public long getStartOffset() { return startOffset; }
        public long getTransferredBytes() { return transferredBytes.get(); }
        public double getSpeedBps() { return speedBps; }
        public String getStatus() { return status; }
        public double getPercent() {
            return fileSize > 0 ? (double) transferredBytes.get() * 100.0 / fileSize : 0;
        }
    }

    private static class PendingPeer {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        final FileDataHandshake handshake;

        PendingPeer(Socket socket, DataInputStream in, DataOutputStream out, FileDataHandshake handshake) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.handshake = handshake;
        }
    }

    private final ServerCore server;
    private final ExecutorService relayExecutor;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // transferId -> Sockets awaiting their counterpart
    private final Map<String, PendingPeer> pendingSenders = new ConcurrentHashMap<>();
    private final Map<String, PendingPeer> pendingReceivers = new ConcurrentHashMap<>();

    // Multi-receiver group transfers
    private final Set<String> groupTransferIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, List<PendingPeer>> pendingGroupReceivers = new ConcurrentHashMap<>();
    private final Set<String> activeGroupTransfers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Set<String>> groupAcceptedMembers = new ConcurrentHashMap<>();

    // Registered transfer metadata from Port 8888 negotiation: transferId -> ActiveTransferInfo
    private final Map<String, ActiveTransferInfo> registeredTransfers = new ConcurrentHashMap<>();

    public ServerFileRelayManager(ServerCore server) {
        this.server = server;
        this.relayExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "server-file-relay-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start(int port) throws IOException {
        if (running.get()) return;

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port), 50);
        running.set(true);

        relayExecutor.submit(this::acceptLoop);
        server.logEvent("[FILE RELAY] Data channel listener started on 0.0.0.0:" + port);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        server.logEvent("[FILE RELAY] Data channel listener stopped.");
    }

    public void registerTransferNegotiation(String transferId, String sender, String receiver, String fileName, long fileSize, long startOffset) {
        if (fileSize < 0L || fileSize > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(Constants.OVERSIZED_FILE_ERROR_MESSAGE);
        }
        if (startOffset < 0L || startOffset > fileSize) {
            throw new IllegalArgumentException("Invalid offset: " + startOffset + " for fileSize: " + fileSize);
        }
        long effectiveOffset = startOffset;
        if (effectiveOffset == 0L) {
            StorageEngine.FileTransferRecord stored = StorageEngine.getInstance().getFileTransfer(transferId);
            if (stored != null && stored.getOffsetBytes() > 0 && stored.getOffsetBytes() < fileSize) {
                effectiveOffset = stored.getOffsetBytes();
                server.logEvent(String.format("[FILE RESUME] Restored stored resume offset %d bytes for '%s'",
                        effectiveOffset, fileName));
            }
        }
        registeredTransfers.put(transferId, new ActiveTransferInfo(transferId, sender, receiver, fileName, fileSize, effectiveOffset));
        StorageEngine.getInstance().recordFileTransfer(transferId, fileName, fileSize, null, effectiveOffset, sender, receiver, null, false, "NEGOTIATED");
    }

    public void registerGroupTransfer(String transferId, String sender, String roomId, String fileName, long fileSize) {
        groupTransferIds.add(transferId);
        registerTransferNegotiation(transferId, sender, "Nhóm: " + roomId, fileName, fileSize, 0L);
    }

    public void registerGroupAccept(String transferId, String username) {
        groupAcceptedMembers.computeIfAbsent(transferId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(username);
    }

    public int getExpectedGroupReceiversCount(String transferId) {
        Set<String> set = groupAcceptedMembers.get(transferId);
        return (set != null && !set.isEmpty()) ? set.size() : 1;
    }

    public boolean isGroupTransfer(String transferId) {
        return transferId != null && groupTransferIds.contains(transferId);
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket dataSocket = serverSocket.accept();
                dataSocket.setTcpNoDelay(true);
                relayExecutor.submit(() -> handleIncomingDataSocket(dataSocket));
            } catch (SocketException e) {
                if (!running.get()) break;
            } catch (IOException e) {
                if (!running.get()) break;
            }
        }
    }

    private void handleIncomingDataSocket(Socket socket) {
        try {
            socket.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            FileDataHandshake handshake = FileDataHandshake.readFrom(in);
            String transferId = handshake.getTransferId();
            byte role = handshake.getRole();

            PendingPeer currentPeer = new PendingPeer(socket, in, out, handshake);

            if (groupTransferIds.contains(transferId)) {
                // Group Multi-Receiver Transfer
                if (role == FileDataHandshake.ROLE_RECEIVER) {
                    server.logEvent(String.format("[GROUP RELAY] Receiver connected for transfer '%s' from %s",
                            transferId, socket.getRemoteSocketAddress()));
                    pendingGroupReceivers.computeIfAbsent(transferId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(currentPeer);
                } else {
                    server.logEvent(String.format("[GROUP RELAY] Sender connected for transfer '%s' from %s",
                            transferId, socket.getRemoteSocketAddress()));
                    if (!activeGroupTransfers.add(transferId)) {
                        FileDataHandshake.sendResponse(out, FileDataHandshake.STATUS_REJECTED, "Group relay sender already active");
                        socket.close();
                        return;
                    }
                    relayExecutor.submit(() -> runGroupRelay(transferId, currentPeer));
                }
            } else {
                // Standard 1-to-1 Transfer
                if (role == FileDataHandshake.ROLE_SENDER) {
                    server.logEvent(String.format("[FILE RELAY] Sender connected for transfer '%s' from %s",
                            transferId, socket.getRemoteSocketAddress()));
                    PendingPeer receiver = pendingReceivers.remove(transferId);
                    if (receiver != null) {
                        relayExecutor.submit(() -> runRelayPair(transferId, currentPeer, receiver));
                    } else {
                        pendingSenders.put(transferId, currentPeer);
                    }
                } else {
                    server.logEvent(String.format("[FILE RELAY] Receiver connected for transfer '%s' from %s",
                            transferId, socket.getRemoteSocketAddress()));
                    PendingPeer sender = pendingSenders.remove(transferId);
                    if (sender != null) {
                        relayExecutor.submit(() -> runRelayPair(transferId, sender, currentPeer));
                    } else {
                        pendingReceivers.put(transferId, currentPeer);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FILE RELAY EXCEPTION] " + e.getMessage());
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void runGroupRelay(String transferId, PendingPeer sender) {
        ActiveTransferInfo info = registeredTransfers.get(transferId);
        String fileDesc = info != null ? info.getFileName() : transferId;
        server.logEvent(String.format("[GROUP RELAY] Starting group streaming for '%s'...", fileDesc));

        List<PendingPeer> receivers = pendingGroupReceivers.computeIfAbsent(transferId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        int expectedCount = getExpectedGroupReceiversCount(transferId);
        long waitStart = System.currentTimeMillis();
        while (receivers.size() < expectedCount && System.currentTimeMillis() - waitStart < 15000) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        if (receivers.isEmpty()) {
            try {
                FileDataHandshake.sendResponse(sender.out, FileDataHandshake.STATUS_REJECTED, "Chưa có thành viên nào trong nhóm kết nối nhận");
                sender.socket.close();
            } catch (IOException ignored) {}
            return;
        }

        try {
            // Signal OK to sender and all ready receivers
            FileDataHandshake.sendResponse(sender.out, FileDataHandshake.STATUS_OK, "Group Relay Ready");
            for (PendingPeer rec : receivers) {
                try {
                    FileDataHandshake.sendResponse(rec.out, FileDataHandshake.STATUS_OK, "Group Relay Ready");
                } catch (IOException ignored) {}
            }

            byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
            long lastSpeedCalcTime = System.currentTimeMillis();
            long bytesSinceLastSpeedCalc = 0;

            int read;
            while (running.get() && (read = sender.in.read(buffer)) != -1) {
                // Fan-out chunk to all connected receivers
                for (PendingPeer rec : receivers) {
                    try {
                        rec.out.write(buffer, 0, read);
                        rec.out.flush();
                    } catch (IOException e) {
                        server.logEvent(String.format("[GROUP RELAY] Receiver %s disconnected during '%s'. Other receivers continue.",
                                rec.socket.getRemoteSocketAddress(), fileDesc));
                        try { rec.socket.close(); } catch (IOException ignored) {}
                        receivers.remove(rec);
                    }
                }

                bytesSinceLastSpeedCalc += read;
                if (info != null) {
                    info.transferredBytes.addAndGet(read);
                }

                long now = System.currentTimeMillis();
                if (now - lastSpeedCalcTime >= 1000) {
                    double durationSec = (now - lastSpeedCalcTime) / 1000.0;
                    double bps = durationSec > 0 ? (bytesSinceLastSpeedCalc / durationSec) : 0;
                    if (info != null) info.speedBps = bps;
                    bytesSinceLastSpeedCalc = 0;
                    lastSpeedCalcTime = now;
                }
            }

            // Await verification status from all remaining active receivers
            boolean allOk = true;
            for (PendingPeer rec : receivers) {
                try {
                    rec.socket.setSoTimeout(180_000);
                    FileDataHandshake.Response recResp = FileDataHandshake.readResponse(rec.in);
                    if (!recResp.isComplete()) allOk = false;
                } catch (Exception e) {
                    allOk = false;
                }
            }

            FileDataHandshake.sendResponse(sender.out, allOk ? FileDataHandshake.STATUS_COMPLETE : FileDataHandshake.STATUS_OK,
                    allOk ? "Phát nhóm hoàn tất & đã xác minh băm SHA-256" : "Phát nhóm hoàn tất");

            if (info != null) {
                info.status = allOk ? "Hoàn tất nhóm & Đã băm" : "Hoàn tất nhóm";
            }
            server.logEvent(String.format("[GROUP RELAY] Group transfer '%s' finished successfully to %d recipients",
                    fileDesc, receivers.size()));

        } catch (Exception e) {
            if (info != null) info.status = "Bị gián đoạn / Hủy";
            server.logEvent(String.format("[GROUP RELAY] Error during group relay '%s': %s", fileDesc, e.getMessage()));
        } finally {
            try { sender.socket.close(); } catch (IOException ignored) {}
            for (PendingPeer rec : receivers) {
                try { rec.socket.close(); } catch (IOException ignored) {}
            }
            groupTransferIds.remove(transferId);
            pendingGroupReceivers.remove(transferId);
            activeGroupTransfers.remove(transferId);
            groupAcceptedMembers.remove(transferId);
        }
    }

    private void runRelayPair(String transferId, PendingPeer sender, PendingPeer receiver) {
        ActiveTransferInfo info = registeredTransfers.get(transferId);
        String fileDesc = info != null ? info.getFileName() : transferId;
        server.logEvent(String.format("[FILE RELAY] Pairing established for '%s'! Streaming data bytes...", fileDesc));

        try {
            // Signal OK to both sender and receiver
            FileDataHandshake.sendResponse(sender.out, FileDataHandshake.STATUS_OK, "Relay Ready");
            FileDataHandshake.sendResponse(receiver.out, FileDataHandshake.STATUS_OK, "Relay Ready");

            java.util.concurrent.atomic.AtomicBoolean receiverFailed = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<String> receiverErrorMsg = new java.util.concurrent.atomic.AtomicReference<>(null);

            // Task 1: Relay data stream from Sender to Receiver in background thread
            Future<?> forwardTask = relayExecutor.submit(() -> {
                byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
                long lastSpeedCalcTime = System.currentTimeMillis();
                long bytesSinceLastSpeedCalc = 0;
                try {
                    // Set timeout so an idle sender doesn't hold the relay indefinitely
                    sender.socket.setSoTimeout(60_000);
                    int read;
                    while (running.get() && (read = sender.in.read(buffer)) != -1) {
                        try {
                            receiver.out.write(buffer, 0, read);
                            receiver.out.flush();
                        } catch (IOException writeEx) {
                            receiverFailed.set(true);
                            receiverErrorMsg.set("Lỗi ghi dữ liệu tới bên nhận: " + writeEx.getMessage());
                            break;
                        }

                        bytesSinceLastSpeedCalc += read;
                        if (info != null) {
                            info.transferredBytes.addAndGet(read);
                        }

                        long now = System.currentTimeMillis();
                        if (now - lastSpeedCalcTime >= 1000) {
                            double durationSec = (now - lastSpeedCalcTime) / 1000.0;
                            double bps = durationSec > 0 ? (bytesSinceLastSpeedCalc / durationSec) : 0;
                            if (info != null) {
                                info.speedBps = bps;
                                StorageEngine.getInstance().updateTransferOffset(transferId, info.getTransferredBytes(), "TRANSFERRING");
                            }
                            bytesSinceLastSpeedCalc = 0;
                            lastSpeedCalcTime = now;
                        }
                    }
                } catch (IOException e) {
                    if (!receiverFailed.get()) {
                        server.logEvent(String.format("[FILE RELAY] Sender read interrupted for '%s': %s", fileDesc, e.getMessage()));
                    }
                }
            });

            // Wait for forwardTask to complete streaming (sender sent all data or receiver disconnected)
            try {
                forwardTask.get();
            } catch (Exception ex) {
                server.logEvent("[FILE RELAY] Forward task interrupted: " + ex.getMessage());
            }

            if (receiverFailed.get()) {
                try {
                    FileDataHandshake.sendResponse(sender.out, FileDataHandshake.STATUS_REJECTED, receiverErrorMsg.get());
                } catch (IOException ignored) {}
                if (info != null) {
                    info.status = "Bên nhận ngắt kết nối";
                    StorageEngine.getInstance().updateTransferOffset(transferId, info.getTransferredBytes(), "INTERRUPTED");
                }
                return;
            }

            // Task 2: Now that ALL data is forwarded, await receiver's final verification status!
            // Give receiver plenty of time (180s = 3 minutes) for disk flush and SHA-256 calculation
            try {
                receiver.socket.setSoTimeout(180_000);
                FileDataHandshake.Response recResp = FileDataHandshake.readResponse(receiver.in);
                FileDataHandshake.sendResponse(sender.out, recResp.getStatus(), recResp.getMessage());

                if (info != null) {
                    info.status = recResp.isComplete() ? "Hoàn tất & Đã băm" : "Lỗi: " + recResp.getMessage();
                    StorageEngine.getInstance().updateTransferOffset(transferId, info.getTransferredBytes(), recResp.isComplete() ? "COMPLETED" : "FAILED");
                }
                server.logEvent(String.format("[FILE RELAY] Transfer '%s' finished: status=0x%02X (%s)",
                        fileDesc, recResp.getStatus(), recResp.getMessage()));
            } catch (Exception e) {
                if (info != null) {
                    info.status = "Bị gián đoạn / Hủy";
                    StorageEngine.getInstance().updateTransferOffset(transferId, info.getTransferredBytes(), "INTERRUPTED");
                }
                server.logEvent(String.format("[FILE RELAY] Relay response error for '%s': %s", fileDesc, e.getMessage()));
                try {
                    FileDataHandshake.sendResponse(sender.out, FileDataHandshake.STATUS_REJECTED, "Bên nhận không phản hồi xác thực: " + e.getMessage());
                } catch (IOException ignored) {}
            }

        } catch (Exception e) {
            if (info != null) {
                info.status = "Bị gián đoạn / Hủy";
                StorageEngine.getInstance().updateTransferOffset(transferId, info.getTransferredBytes(), "INTERRUPTED");
            }
            server.logEvent(String.format("[FILE RELAY] Error during relay '%s': %s", fileDesc, e.getMessage()));
        } finally {
            try { sender.socket.close(); } catch (IOException ignored) {}
            try { receiver.socket.close(); } catch (IOException ignored) {}
            pendingSenders.remove(transferId);
            pendingReceivers.remove(transferId);
        }
    }

    public List<ActiveTransferInfo> getActiveTransfers() {
        return new ArrayList<>(registeredTransfers.values());
    }

    public ActiveTransferInfo getTransferInfo(String transferId) {
        if (transferId == null) return null;
        return registeredTransfers.get(transferId);
    }

    public int getActiveTransferCount() {
        int count = 0;
        for (ActiveTransferInfo info : registeredTransfers.values()) {
            if ("Đang chuyển tiếp".equals(info.getStatus())) count++;
        }
        return count;
    }
}
