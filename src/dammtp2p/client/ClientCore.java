package dammtp2p.client;

import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.network.HandshakePayload;
import dammtp2p.core.protocol.MessageSerializer;
import dammtp2p.core.protocol.PacketHeader;
import dammtp2p.core.protocol.PacketType;
import dammtp2p.core.protocol.ProtocolConstants;
import dammtp2p.core.transfer.FileDataHandshake;
import dammtp2p.core.transfer.HashVerifier;
import dammtp2p.security.CryptoUtils;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;

/**
 * Client-side networking and application core communicating with Central Server.
 * Supports:
 * <ul>
 *   <li>TCP Control connection (Port 8888) with message framing.</li>
 *   <li>PBKDF2 login/registration and session presence tracking.</li>
 *   <li>1-to-1 private chat and Group room chat with AES-128-GCM encryption.</li>
 *   <li>Server-relayed file transfers (Port 8889) with chunked AES-GCM and SHA-256 verification.</li>
 * </ul>
 */
public class ClientCore {

    public interface ClientListener {
        void onConnected(String serverIp, int port);
        void onDisconnected(String reason);
        void onLoginSuccess(String username, List<String> onlineUsers, List<String> rooms);
        void onLoginFailed(String reason);
        void onOnlineUsersUpdated(List<String> onlineUsers);
        void onRoomsUpdated(List<String> rooms);
        void onChatMessage(ChatMessage message);
        void onIncomingFileRequest(FileMetadata metadata);
        void onFileTransferProgress(FileMetadata metadata, long transferred, long total, double speedBps);
        void onFileTransferStateChanged(FileMetadata metadata, TransferState state, String message);
        default void onVoiceMembersUpdated(String roomId, List<String> members) {}
        default void onUserSpeaking(String username, boolean isSpeaking) {}
        default void onVoiceStateChanged(String roomId, boolean inVoice) {}
        default void onFriendStatusChanged(String target, boolean success, String message) {}
    }

    private final String clientDisplayName;
    private final List<ClientListener> listeners = new CopyOnWriteArrayList<>();

    private Socket controlSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private final Object writeLock = new Object();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private volatile String authenticatedUser = null;
    private volatile String connectedServerIp = null;
    private volatile int connectedServerPort = Constants.DEFAULT_CONTROL_PORT;
    private volatile int connectedFilePort = Constants.DEFAULT_FILE_TRANSFER_PORT;
    private volatile int connectedVoicePort = Constants.DEFAULT_VOICE_PORT;
    private dammtp2p.voice.VoiceClient voiceClient;

    private final List<String> onlineUsers = new CopyOnWriteArrayList<>();
    private final List<String> availableRooms = new CopyOnWriteArrayList<>();
    private final Map<String, SecretKey> roomKeys = new ConcurrentHashMap<>();
    private final Map<String, SecretKey> privateConversationKeys = new ConcurrentHashMap<>();

    // Active transfers
    private final Map<String, FileMetadata> activeTransfers = new ConcurrentHashMap<>();
    private final Map<String, File> localSourceFiles = new ConcurrentHashMap<>();
    private final Map<String, Socket> activeDataSockets = new ConcurrentHashMap<>();
    private final Map<String, Boolean> canceledTransfers = new ConcurrentHashMap<>();
    private final Set<String> activeSendingTransferIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile File downloadDirectory = new File(Constants.DEFAULT_DOWNLOAD_DIR);

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-worker");
        t.setDaemon(true);
        return t;
    });

    // Latches for synchronous login/register responses
    private final AtomicReference<CountDownLatch> authLatch = new AtomicReference<>();
    private volatile String lastAuthError = null;
    private volatile boolean lastAuthSuccess = false;

    // Latches for synchronous handshake response
    private final AtomicReference<CountDownLatch> handshakeLatch = new AtomicReference<>();
    private volatile String lastHandshakeError = null;
    private volatile boolean lastHandshakeSuccess = false;

    public ClientCore(String clientDisplayName) {
        this.clientDisplayName = clientDisplayName;
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs();
        }
    }

    public synchronized void connectToServer(String serverIp, int port) throws IOException {
        if (connected.get()) {
            disconnect("Reconnecting");
        }

        try {
            controlSocket = new Socket();
            controlSocket.connect(new InetSocketAddress(serverIp, port), 5000);
            controlSocket.setTcpNoDelay(true);
            controlSocket.setKeepAlive(true);

            in = new DataInputStream(new BufferedInputStream(controlSocket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(controlSocket.getOutputStream()));

            connected.set(true);
            connectedServerIp = serverIp;
            connectedServerPort = port;
            int cfgFilePort = dammtp2p.util.AppConfig.getInstance().getFilePort();
            connectedFilePort = (cfgFilePort > 0) ? cfgFilePort : ((port == Constants.DEFAULT_CONTROL_PORT) ? Constants.DEFAULT_FILE_TRANSFER_PORT : (port + 1));

            CountDownLatch latch = new CountDownLatch(1);
            handshakeLatch.set(latch);
            lastHandshakeSuccess = false;
            lastHandshakeError = null;

            clientExecutor.submit(this::runControlReaderLoop);

            // Send handshake request
            HandshakePayload hs = new HandshakePayload(
                    clientDisplayName != null && !clientDisplayName.trim().isEmpty() ? clientDisplayName : "Client",
                    port,
                    connectedFilePort
            );
            sendPacket(PacketType.HANDSHAKE_REQ, hs.toJson());

            boolean received = latch.await(5000, TimeUnit.MILLISECONDS);
            if (!received) {
                disconnect("Handshake timeout");
                throw new IOException("Máy chủ không phản hồi bắt tay (Handshake timeout sau 5 giây).");
            }
            if (!lastHandshakeSuccess) {
                String errMsg = lastHandshakeError != null ? lastHandshakeError : "Bắt tay với máy chủ thất bại.";
                disconnect("Handshake rejected: " + errMsg);
                throw new IOException("Máy chủ từ chối kết nối: " + errMsg);
            }

            for (ClientListener l : listeners) {
                l.onConnected(serverIp, port);
            }
        } catch (UnknownHostException e) {
            disconnect("Unknown host");
            throw new IOException("Không tìm thấy địa chỉ máy chủ: '" + serverIp + "'. Vui lòng kiểm tra lại địa chỉ IP.", e);
        } catch (ConnectException e) {
            disconnect("Connection refused");
            throw new IOException("Không thể kết nối đến máy chủ tại " + serverIp + ":" + port + 
                    ". Hãy đảm bảo Server đang chạy và cổng " + port + " không bị chặn tường lửa.", e);
        } catch (SocketTimeoutException e) {
            disconnect("Connection timeout");
            throw new IOException("Hết thời gian kết nối (Timeout) tới " + serverIp + ":" + port + 
                    ". Hãy kiểm tra kết nối mạng LAN hoặc ZeroTier.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            disconnect("Interrupted");
            throw new IOException("Quá trình kết nối bị ngắt quãng.", e);
        }
    }

    public void disconnect(String reason) {
        if (!connected.compareAndSet(true, false)) return;
        authenticated.set(false);
        authenticatedUser = null;
        onlineUsers.clear();
        availableRooms.clear();

        if (voiceClient != null) {
            try {
                voiceClient.stop();
            } catch (Exception ignored) {}
            voiceClient = null;
        }

        for (Socket s : activeDataSockets.values()) {
            try { s.close(); } catch (IOException ignored) {}
        }
        activeDataSockets.clear();

        if (controlSocket != null && !controlSocket.isClosed()) {
            try {
                byte[] payloadBytes = "{}".getBytes(StandardCharsets.UTF_8);
                PacketHeader header = new PacketHeader(PacketType.DISCONNECT, payloadBytes.length);
                synchronized (writeLock) {
                    if (out != null) {
                        header.writeTo(out);
                        out.write(payloadBytes);
                        out.flush();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    controlSocket.close();
                } catch (IOException ignored) {}
            }
        }

        for (ClientListener l : listeners) {
            l.onDisconnected(reason);
        }
    }

    public boolean login(String username, String password, long timeoutMs) throws Exception {
        if (!connected.get()) {
            throw new IOException("Chưa kết nối tới máy chủ.");
        }
        CountDownLatch latch = new CountDownLatch(1);
        authLatch.set(latch);
        lastAuthSuccess = false;
        lastAuthError = null;

        String loginJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password));
        sendPacket(PacketType.LOGIN_REQ, loginJson);

        boolean reached = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!reached) {
            throw new IOException("Hết thời gian chờ phản hồi đăng nhập từ máy chủ.");
        }
        if (!lastAuthSuccess) {
            throw new IOException(lastAuthError != null ? lastAuthError : "Đăng nhập thất bại.");
        }
        return true;
    }

    public boolean register(String username, String password, long timeoutMs) throws Exception {
        if (!connected.get()) {
            throw new IOException("Chưa kết nối tới máy chủ.");
        }
        CountDownLatch latch = new CountDownLatch(1);
        authLatch.set(latch);
        lastAuthSuccess = false;
        lastAuthError = null;

        String regJson = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password));
        sendPacket(PacketType.REGISTER_REQ, regJson);

        boolean reached = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!reached) {
            throw new IOException("Hết thời gian chờ phản hồi đăng ký từ máy chủ.");
        }
        if (!lastAuthSuccess) {
            throw new IOException(lastAuthError != null ? lastAuthError : "Đăng ký thất bại.");
        }
        return true;
    }

    private void runControlReaderLoop() {
        try {
            while (connected.get() && controlSocket != null && !controlSocket.isClosed()) {
                PacketHeader header;
                try {
                    header = MessageSerializer.readHeader(in);
                } catch (EOFException | SocketException e) {
                    break;
                }

                byte[] payloadBytes = MessageSerializer.readPayload(in, header.getPayloadLength());
                String payload = new String(payloadBytes, StandardCharsets.UTF_8);

                handleIncomingPacket(header.getPacketType(), payload);
            }
        } catch (Exception e) {
            // Disconnect occurred
        } finally {
            CountDownLatch hsLatch = handshakeLatch.get();
            if (hsLatch != null) hsLatch.countDown();
            CountDownLatch aLatch = authLatch.get();
            if (aLatch != null) aLatch.countDown();
            disconnect("Mất kết nối tới máy chủ");
        }
    }

    private void handleIncomingPacket(PacketType type, String payload) {
        switch (type) {
            case HANDSHAKE_ACK:
                handleHandshakeAck(payload);
                break;
            case LOGIN_RESP:
                handleLoginResponse(payload);
                break;
            case REGISTER_RESP:
                handleRegisterResponse(payload);
                break;
            case USER_LIST_RESP:
            case USER_STATUS_BROADCAST:
                handleUserPresence(payload);
                break;
            case ROOM_LIST_RESP:
                handleRoomList(payload);
                break;
            case ROOM_KEY_PAYLOAD:
                handleRoomKeyPayload(payload);
                break;
            case PRIVATE_CHAT_MESSAGE:
            case ROOM_CHAT_MESSAGE:
            case CHAT_MESSAGE:
                handleChatMessagePacket(payload);
                break;
            case FILE_TRANSFER_REQUEST:
                handleFileOffer(payload);
                break;
            case FILE_TRANSFER_ACCEPT:
                handleFileAcceptByPeer(payload);
                break;
            case FILE_TRANSFER_REJECT:
                handleFileRejectByPeer(payload);
                break;
            case FILE_TRANSFER_COMPLETE:
                handleFileCompleteNotification(payload);
                break;
            case VOICE_MEMBERS_BROADCAST:
                handleVoiceMembersBroadcast(payload);
                break;
            case FRIEND_ADD_RESP:
                handleFriendAddResp(payload);
                break;
            default:
                break;
        }
    }

    private void handleFriendAddResp(String payload) {
        String target = extractJsonValue(payload, "target");
        String message = extractJsonValue(payload, "message");
        String successStr = extractJsonValue(payload, "success");
        boolean ok = "true".equalsIgnoreCase(successStr);
        if (ok && target != null && authenticatedUser != null) {
            dammtp2p.client.FriendsManager.getInstance().addFriend(authenticatedUser, target);
        }
        for (ClientListener l : listeners) {
            l.onFriendStatusChanged(target, ok, message);
        }
    }

    private void handleVoiceMembersBroadcast(String payload) {
        String roomId = extractJsonValue(payload, "roomId");
        List<String> members = parseListFromJson(payload, "members");
        for (ClientListener l : listeners) {
            l.onVoiceMembersUpdated(roomId, members);
        }
    }

    private List<String> parseListFromJson(String json, String arrayKey) {
        List<String> list = new ArrayList<>();
        if (json == null) return list;
        String pattern = "\"" + arrayKey + "\":[";
        int idx = json.indexOf(pattern);
        if (idx == -1) return list;
        int end = json.indexOf("]", idx);
        if (end == -1) return list;
        String arr = json.substring(idx + pattern.length(), end);
        for (String item : arr.split(",")) {
            String val = item.replace("\"", "").trim();
            if (!val.isEmpty()) {
                list.add(val);
            }
        }
        return list;
    }

    private void handleHandshakeAck(String payload) {
        boolean ok = "true".equalsIgnoreCase(extractJsonValue(payload, "success"));
        lastHandshakeSuccess = ok;
        if (ok) {
            lastHandshakeError = null;
            String sFilePort = extractJsonValue(payload, "filePort");
            if (sFilePort != null) {
                try {
                    this.connectedFilePort = Integer.parseInt(sFilePort);
                } catch (NumberFormatException ignored) {}
            }
            String sVoicePort = extractJsonValue(payload, "voicePort");
            if (sVoicePort != null) {
                try {
                    this.connectedVoicePort = Integer.parseInt(sVoicePort);
                } catch (NumberFormatException ignored) {}
            }
        } else {
            lastHandshakeError = extractJsonValue(payload, "message");
        }
        CountDownLatch latch = handshakeLatch.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    private void handleLoginResponse(String payload) {
        boolean ok = "true".equalsIgnoreCase(extractJsonValue(payload, "success"));
        lastAuthSuccess = ok;
        if (ok) {
            authenticatedUser = extractJsonValue(payload, "username");
            authenticated.set(true);

            parseOnlineUsers(payload);
            parseRooms(payload);

            for (ClientListener l : listeners) {
                l.onLoginSuccess(authenticatedUser, new ArrayList<>(onlineUsers), new ArrayList<>(availableRooms));
                l.onOnlineUsersUpdated(new ArrayList<>(onlineUsers));
                l.onRoomsUpdated(new ArrayList<>(availableRooms));
            }
        } else {
            lastAuthError = extractJsonValue(payload, "message");
            for (ClientListener l : listeners) {
                l.onLoginFailed(lastAuthError);
            }
        }
        CountDownLatch latch = authLatch.get();
        if (latch != null) latch.countDown();
    }

    private void handleRegisterResponse(String payload) {
        boolean ok = "true".equalsIgnoreCase(extractJsonValue(payload, "success"));
        lastAuthSuccess = ok;
        if (!ok) {
            lastAuthError = extractJsonValue(payload, "message");
        }
        CountDownLatch latch = authLatch.get();
        if (latch != null) latch.countDown();
    }

    private void handleUserPresence(String payload) {
        // Request updated user list or parse broadcast
        String user = extractJsonValue(payload, "username");
        String status = extractJsonValue(payload, "status");
        if (user != null && status != null) {
            if ("ONLINE".equalsIgnoreCase(status) && !onlineUsers.contains(user)) {
                onlineUsers.add(user);
            } else if ("OFFLINE".equalsIgnoreCase(status)) {
                onlineUsers.remove(user);
            }
            Collections.sort(onlineUsers);
            for (ClientListener l : listeners) {
                l.onOnlineUsersUpdated(new ArrayList<>(onlineUsers));
            }
        } else {
            parseOnlineUsers(payload);
            for (ClientListener l : listeners) {
                l.onOnlineUsersUpdated(new ArrayList<>(onlineUsers));
            }
        }
    }

    private void handleRoomList(String payload) {
        parseRooms(payload);
        for (ClientListener l : listeners) {
            l.onRoomsUpdated(new ArrayList<>(availableRooms));
        }
    }

    private void handleRoomKeyPayload(String payload) {
        String roomId = extractJsonValue(payload, "roomId");
        String keyBase64 = extractJsonValue(payload, "key");
        if (roomId != null && keyBase64 != null) {
            try {
                SecretKey key = CryptoUtils.keyFromBase64(keyBase64);
                roomKeys.put(roomId.toLowerCase(), key);
            } catch (Exception ignored) {}
        }
    }

    private void handleChatMessagePacket(String payload) {
        if (payload == null || payload.contains("\"type\":\"ERROR\"") || payload.contains("\"type\": \"ERROR\"")) {
            return;
        }
        ChatMessage rawMsg = ChatMessage.fromJson(payload);
        if (rawMsg == null || rawMsg.getSender() == null || rawMsg.getContent() == null) return;

        // Check if an incoming key is attached for 1-to-1 conversation
        String keyB64 = extractJsonValue(payload, "key");
        if (keyB64 != null && rawMsg.getSender() != null) {
            try {
                SecretKey incomingKey = CryptoUtils.keyFromBase64(keyB64);
                privateConversationKeys.put(rawMsg.getSender().toLowerCase(), incomingKey);
            } catch (Exception ignored) {}
        }

        ChatMessage finalMsg = rawMsg;
        // Attempt AES-128-GCM decryption if encrypted
        if (rawMsg.isEncrypted()) {
            SecretKey key = null;
            if (rawMsg.isRoomMessage()) {
                key = roomKeys.get(rawMsg.getRoomId().toLowerCase());
            } else {
                key = getOrCreatePrivateConversationKey(rawMsg.getSender());
            }

            if (key != null && rawMsg.getIv() != null) {
                try {
                    String decryptedText = CryptoUtils.decryptString(rawMsg.getIv(), rawMsg.getContent(), key);
                    finalMsg = new ChatMessage(rawMsg.getMessageId(), rawMsg.getSender(), rawMsg.getReceiver(),
                            rawMsg.getRoomId(), decryptedText, rawMsg.getTimestamp(), false, true, rawMsg.getIv());
                } catch (Exception e) {
                    finalMsg = new ChatMessage(rawMsg.getMessageId(), rawMsg.getSender(), rawMsg.getReceiver(),
                            rawMsg.getRoomId(), "[Lỗi giải mã AES: " + e.getMessage() + "]", rawMsg.getTimestamp(), false, true, null);
                }
            }
        }

        for (ClientListener l : listeners) {
            l.onChatMessage(finalMsg);
        }
    }

    public void requestAddFriend(String targetUser) throws IOException {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        sendPacket(PacketType.FRIEND_ADD_REQ, String.format("{\"target\":\"%s\"}", escapeJson(targetUser)));
    }

    public void requestRemoveFriend(String targetUser) throws IOException {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        sendPacket(PacketType.FRIEND_REMOVE_REQ, String.format("{\"target\":\"%s\"}", escapeJson(targetUser)));
    }

    public void sendPrivateMessage(String targetUser, String text) throws Exception {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        SecretKey key = getOrCreatePrivateConversationKey(targetUser);
        CryptoUtils.EncryptedPayload enc = CryptoUtils.encryptString(text, key);

        String msgId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String keyB64 = CryptoUtils.keyToBase64(key);

        String customJson = String.format("{\"messageId\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\",\"content\":\"%s\",\"timestamp\":%d,\"encrypted\":true,\"iv\":\"%s\",\"key\":\"%s\"}",
                msgId, escapeJson(authenticatedUser), escapeJson(targetUser), escapeJson(enc.getCipher()), now, escapeJson(enc.getIv()), keyB64);
        sendPacket(PacketType.PRIVATE_CHAT_MESSAGE, customJson);

        // Notify local listener with plaintext message
        ChatMessage localMsg = new ChatMessage(msgId, authenticatedUser, targetUser, null, text, now, true, true, enc.getIv());
        for (ClientListener l : listeners) {
            l.onChatMessage(localMsg);
        }
    }

    public void sendRoomMessage(String roomId, String text) throws Exception {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        SecretKey key = roomKeys.get(roomId.toLowerCase());
        if (key == null) {
            // Join room to receive key if not yet held
            sendPacket(PacketType.ROOM_JOIN_REQ, String.format("{\"id\":\"%s\"}", escapeJson(roomId)));
            key = CryptoUtils.deriveRoomKey(roomId); // Consistent room key
            roomKeys.put(roomId.toLowerCase(), key);
        }

        CryptoUtils.EncryptedPayload enc = CryptoUtils.encryptString(text, key);
        String msgId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        ChatMessage chatMsg = new ChatMessage(msgId, authenticatedUser, null, roomId, enc.getCipher(), now, true, true, enc.getIv());
        sendPacket(PacketType.ROOM_CHAT_MESSAGE, chatMsg.toJson());

        ChatMessage localMsg = new ChatMessage(msgId, authenticatedUser, null, roomId, text, now, true, true, enc.getIv());
        for (ClientListener l : listeners) {
            l.onChatMessage(localMsg);
        }
    }

    public void createRoom(String roomId, String displayName) throws IOException {
        String json = String.format("{\"id\":\"%s\",\"name\":\"%s\"}",
                escapeJson(roomId), escapeJson(displayName != null && !displayName.trim().isEmpty() ? displayName : roomId));
        sendPacket(PacketType.ROOM_CREATE_REQ, json);
    }

    // =========================================================================
    //  FILE TRANSFER METHODS (PORT 8889 RELAY VIA SERVER)
    // =========================================================================

    public FileMetadata initiateFileSend(String receiver, File file, boolean encrypt, HashVerifier.ProgressCallback hashCallback)
            throws Exception {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        if (!file.exists() || !file.isFile()) throw new IOException("Tệp không tồn tại: " + file.getPath());

        long size = file.length();
        if (size > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(Constants.OVERSIZED_FILE_ERROR_MESSAGE);
        }

        if (receiver != null) {
            String trimmed = receiver.trim();
            boolean isRoom = trimmed.startsWith("room:") || trimmed.startsWith("[Nhóm]")
                    || availableRooms.stream().anyMatch(r -> r.equalsIgnoreCase(trimmed));
            if (isRoom) {
                String roomId = trimmed.replace("[Nhóm] # ", "").replace("[Nhóm]", "").replace("room:", "").trim();
                return initiateGroupFileSend(roomId, file, encrypt, hashCallback);
            }
        }

        String tid = UUID.randomUUID().toString();
        String sha256 = HashVerifier.calculateSha256(file, hashCallback);

        SecretKey aesKey = encrypt ? CryptoUtils.generateAes128Key() : null;
        byte[] baseIv = encrypt ? CryptoUtils.generateGcmIv() : null;

        FileMetadata meta = new FileMetadata(
                tid,
                file.getName(),
                size,
                sha256,
                0L,
                authenticatedUser,
                receiver,
                System.currentTimeMillis(),
                encrypt,
                aesKey != null ? CryptoUtils.keyToBase64(aesKey) : null,
                baseIv != null ? java.util.Base64.getEncoder().encodeToString(baseIv) : null
        );

        activeTransfers.put(tid, meta);
        localSourceFiles.put(tid, file);

        sendPacket(PacketType.FILE_TRANSFER_REQUEST, meta.toJson());
        notifyStateChanged(meta, TransferState.PENDING, "Đã gửi yêu cầu tới " + receiver);
        return meta;
    }

    private void handleFileOffer(String payload) {
        try {
            FileMetadata meta = FileMetadata.fromJson(payload);
            if (meta.getFileSize() < 0L || meta.getFileSize() > Constants.MAX_FILE_SIZE_BYTES) {
                rejectIncomingFile(meta, Constants.OVERSIZED_FILE_ERROR_MESSAGE);
                notifyStateChanged(meta, TransferState.REJECTED, "Tự động từ chối: " + Constants.OVERSIZED_FILE_ERROR_MESSAGE);
                return;
            }
            activeTransfers.put(meta.getTransferId(), meta);

            for (ClientListener l : listeners) {
                l.onIncomingFileRequest(meta);
            }
        } catch (IllegalArgumentException | IOException ex) {
            // Malformed or oversized metadata in payload - safely reject to remote peer
            String tid = extractJsonValue(payload, "transferId");
            String sender = extractJsonValue(payload, "sender");
            if (tid != null && sender != null) {
                try {
                    String rejectJson = String.format("{\"transferId\":\"%s\",\"sender\":\"%s\",\"reason\":\"%s\"}",
                            escapeJson(tid), escapeJson(sender), escapeJson(Constants.OVERSIZED_FILE_ERROR_MESSAGE));
                    sendPacket(PacketType.FILE_TRANSFER_REJECT, rejectJson);
                } catch (IOException ignored) {}
            }
        }
    }

    public void acceptIncomingFile(FileMetadata meta, File targetDir) throws IOException {
        if (meta.getFileSize() < 0L || meta.getFileSize() > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(Constants.OVERSIZED_FILE_ERROR_MESSAGE);
        }
        String tid = meta.getTransferId();
        File saveDir = (targetDir != null) ? targetDir : downloadDirectory;
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        File destFile = new File(saveDir, meta.getFileName());
        File partFile = new File(saveDir, meta.getFileName() + ".part");

        long existingOffset = 0;
        if (partFile.exists() && partFile.length() < meta.getFileSize()) {
            // Align to 64KB chunk boundary
            existingOffset = (partFile.length() / Constants.DEFAULT_BUFFER_SIZE) * Constants.DEFAULT_BUFFER_SIZE;
        }

        FileMetadata updated = meta.withOffset(existingOffset);
        activeTransfers.put(tid, updated);

        // Send accept packet to server
        String acceptJson = String.format("{\"transferId\":\"%s\",\"sender\":\"%s\",\"receiver\":\"%s\",\"fileName\":\"%s\",\"fileSize\":%d,\"offset\":%d}",
                tid, meta.getSender(), authenticatedUser, meta.getFileName(), meta.getFileSize(), existingOffset);
        sendPacket(PacketType.FILE_TRANSFER_ACCEPT, acceptJson);

        // Launch background receiver connecting to Server Port 8889
        clientExecutor.submit(() -> runFileReceiverWorker(updated, partFile, destFile));
        notifyStateChanged(updated, existingOffset > 0 ? TransferState.RESUMING : TransferState.READY_TO_TRANSFER,
                existingOffset > 0 ? ("Tiếp tục nhận từ " + FormatUtils.formatFileSize(existingOffset) + "...") : "Đang kết nối cổng dữ liệu...");
    }

    public void acceptIncomingFile(FileMetadata meta) throws IOException {
        acceptIncomingFile(meta, downloadDirectory);
    }

    public void rejectIncomingFile(FileMetadata meta, String reason) throws IOException {
        String tid = meta.getTransferId();
        activeTransfers.remove(tid);

        String rejectJson = String.format("{\"transferId\":\"%s\",\"sender\":\"%s\",\"reason\":\"%s\"}",
                tid, meta.getSender(), escapeJson(reason));
        sendPacket(PacketType.FILE_TRANSFER_REJECT, rejectJson);
    }

    private void handleFileAcceptByPeer(String payload) {
        String tid = extractJsonValue(payload, "transferId");
        long offset = Long.parseLong(extractJsonValue(payload, "offset"));

        FileMetadata meta = activeTransfers.get(tid);
        File localFile = localSourceFiles.get(tid);
        if (meta != null && localFile != null) {
            // Deduplicate: In group file transfers, multiple recipients accept the same offer.
            // Only spawn one background sender worker to server data port.
            if (!activeSendingTransferIds.add(tid)) {
                return;
            }

            FileMetadata updated = meta.withOffset(offset);
            activeTransfers.put(tid, updated);
            if (offset > 0) {
                notifyStateChanged(updated, TransferState.RESUMING, "Tiếp tục gửi từ vị trí " + FormatUtils.formatFileSize(offset) + "...");
            }

            // Launch background sender connecting to Server Port 8889
            clientExecutor.submit(() -> {
                try {
                    runFileSenderWorker(updated, localFile);
                } finally {
                    activeSendingTransferIds.remove(tid);
                }
            });
        }
    }

    private void handleFileRejectByPeer(String payload) {
        String tid = extractJsonValue(payload, "transferId");
        String reason = extractJsonValue(payload, "reason");
        FileMetadata meta = activeTransfers.remove(tid);
        if (meta != null) {
            notifyStateChanged(meta, TransferState.REJECTED, "Bên nhận từ chối: " + reason);
        } else {
            FileMetadata placeholder = new FileMetadata(
                    tid != null ? tid : "unknown",
                    "rejected_file",
                    0L,
                    null,
                    0L,
                    authenticatedUser,
                    "Server",
                    System.currentTimeMillis()
            );
            notifyStateChanged(placeholder, TransferState.REJECTED, reason != null ? reason : Constants.OVERSIZED_FILE_ERROR_MESSAGE);
        }
    }

    private void handleFileCompleteNotification(String payload) {
        String tid = extractJsonValue(payload, "transferId");
        FileMetadata meta = activeTransfers.get(tid);
        if (meta != null) {
            notifyStateChanged(meta, TransferState.COMPLETED, "Hoàn tất & Đã xác thực!");
        }
    }

    // =========================================================================
    //  FILE WORKERS (PORT 8889 TCP SOCKETS)
    // =========================================================================

    private void runFileSenderWorker(FileMetadata meta, File localFile) {
        notifyStateChanged(meta, TransferState.TRANSFERRING, "Đang gửi tệp...");
        long totalBytes = meta.getFileSize();
        long startOffset = meta.getOffset();
        long bytesToSend = totalBytes - startOffset;

        SecretKey aesKey = meta.isEncrypted() && meta.getAesKeyBase64() != null
                ? CryptoUtils.keyFromBase64(meta.getAesKeyBase64()) : null;
        byte[] baseIv = meta.isEncrypted() && meta.getBaseIvBase64() != null
                ? java.util.Base64.getDecoder().decode(meta.getBaseIvBase64()) : null;

        Socket dataSocket = new Socket();
        activeDataSockets.put(meta.getTransferId(), dataSocket);

        try (Socket ds = dataSocket;
             RandomAccessFile raf = new RandomAccessFile(localFile, "r")) {

            ds.connect(new InetSocketAddress(connectedServerIp, connectedFilePort), Constants.DEFAULT_CONNECT_TIMEOUT_MS);
            ds.setTcpNoDelay(true);
            ds.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);

            DataInputStream dis = new DataInputStream(new BufferedInputStream(ds.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(ds.getOutputStream()));

            // Handshake as ROLE_SENDER
            FileDataHandshake handshake = new FileDataHandshake(meta.getTransferId(), totalBytes, startOffset, FileDataHandshake.ROLE_SENDER);
            handshake.writeTo(dos);

            // Wait for Server Relay STATUS_OK
            FileDataHandshake.Response resp = FileDataHandshake.readResponse(dis);
            if (!resp.isOk()) {
                throw new IOException("Server relay rejected sender: " + resp.getMessage());
            }

            raf.seek(startOffset);
            byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
            long transferred = 0;
            long chunkIndex = startOffset / Constants.DEFAULT_BUFFER_SIZE;
            long lastReport = System.currentTimeMillis();
            long bytesSinceLast = 0;

            while (transferred < bytesToSend) {
                if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                    break;
                }

                int toRead = (int) Math.min((long) buffer.length, bytesToSend - transferred);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) break;

                if (aesKey != null && baseIv != null) {
                    byte[] encChunk = CryptoUtils.encryptChunk(buffer, 0, read, aesKey, baseIv, chunkIndex);
                    dos.writeInt(encChunk.length);
                    dos.writeLong(chunkIndex);
                    dos.write(encChunk);
                } else {
                    dos.write(buffer, 0, read);
                }

                transferred += read;
                bytesSinceLast += read;
                chunkIndex++;

                long now = System.currentTimeMillis();
                if (now - lastReport >= 500 || transferred == bytesToSend) {
                    double dur = (now - lastReport) / 1000.0;
                    double speed = dur > 0 ? bytesSinceLast / dur : 0;
                    for (ClientListener l : listeners) {
                        l.onFileTransferProgress(meta, startOffset + transferred, totalBytes, speed);
                    }
                    lastReport = now;
                    bytesSinceLast = 0;
                }
            }
            dos.flush();

            if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                notifyStateChanged(meta, TransferState.CANCELED, "Đã hủy gửi tệp.");
                return;
            }

            // Signal EOF on TCP output stream so server forward relay completes cleanly
            try {
                ds.shutdownOutput();
            } catch (IOException ignored) {}

            // Set generous timeout (180s = 3 minutes) while waiting for receiver to verify SHA-256 and respond
            ds.setSoTimeout(180_000);

            // Await final status from receiver via server
            FileDataHandshake.Response finalResp = FileDataHandshake.readResponse(dis);
            if (finalResp.isComplete()) {
                notifyStateChanged(meta, TransferState.COMPLETED, "Hoàn tất & Xác thực SHA-256 thành công!");
            } else {
                notifyStateChanged(meta, TransferState.FAILED, "Lỗi xác thực: " + finalResp.getMessage());
            }

        } catch (Exception e) {
            if (connected.get() && !(e instanceof SocketException)) {
                System.err.println("[SENDER EXCEPTION] " + e.getMessage());
                e.printStackTrace();
            }
            if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                notifyStateChanged(meta, TransferState.CANCELED, "Đã hủy gửi tệp.");
            } else {
                notifyStateChanged(meta, TransferState.INTERRUPTED, "Gián đoạn: " + e.getMessage());
            }
        } finally {
            activeDataSockets.remove(meta.getTransferId());
            canceledTransfers.remove(meta.getTransferId());
        }
    }

    private void runFileReceiverWorker(FileMetadata meta, File partFile, File destFile) {
        notifyStateChanged(meta, TransferState.TRANSFERRING, "Đang nhận tệp...");
        long totalBytes = meta.getFileSize();
        long startOffset = meta.getOffset();
        long bytesToReceive = totalBytes - startOffset;

        SecretKey aesKey = meta.isEncrypted() && meta.getAesKeyBase64() != null
                ? CryptoUtils.keyFromBase64(meta.getAesKeyBase64()) : null;
        byte[] baseIv = meta.isEncrypted() && meta.getBaseIvBase64() != null
                ? java.util.Base64.getDecoder().decode(meta.getBaseIvBase64()) : null;

        Socket dataSocket = new Socket();
        activeDataSockets.put(meta.getTransferId(), dataSocket);

        try (Socket ds = dataSocket;
             RandomAccessFile raf = new RandomAccessFile(partFile, "rw")) {

            ds.connect(new InetSocketAddress(connectedServerIp, connectedFilePort), Constants.DEFAULT_CONNECT_TIMEOUT_MS);
            ds.setTcpNoDelay(true);
            ds.setSoTimeout(Constants.DEFAULT_SO_TIMEOUT_MS);

            DataInputStream dis = new DataInputStream(new BufferedInputStream(ds.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(ds.getOutputStream()));

            // Handshake as ROLE_RECEIVER
            FileDataHandshake handshake = new FileDataHandshake(meta.getTransferId(), totalBytes, startOffset, FileDataHandshake.ROLE_RECEIVER);
            handshake.writeTo(dos);

            // Wait for Server Relay STATUS_OK
            FileDataHandshake.Response resp = FileDataHandshake.readResponse(dis);
            if (!resp.isOk()) {
                throw new IOException("Server relay rejected receiver: " + resp.getMessage());
            }

            raf.seek(startOffset);
            byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
            long received = 0;
            long chunkIndex = startOffset / Constants.DEFAULT_BUFFER_SIZE;
            long lastReport = System.currentTimeMillis();
            long bytesSinceLast = 0;

            while (received < bytesToReceive) {
                if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                    break;
                }

                if (aesKey != null && baseIv != null) {
                    int encLen = dis.readInt();
                    long idx = dis.readLong();
                    byte[] encChunk = new byte[encLen];
                    dis.readFully(encChunk);

                    byte[] plainChunk = CryptoUtils.decryptChunk(encChunk, aesKey, baseIv, idx);
                    raf.write(plainChunk);
                    received += plainChunk.length;
                    bytesSinceLast += plainChunk.length;
                } else {
                    int toRead = (int) Math.min((long) buffer.length, bytesToReceive - received);
                    int read = dis.read(buffer, 0, toRead);
                    if (read == -1) break;
                    raf.write(buffer, 0, read);
                    received += read;
                    bytesSinceLast += read;
                }

                chunkIndex++;
                long now = System.currentTimeMillis();
                if (now - lastReport >= 500 || received == bytesToReceive) {
                    double dur = (now - lastReport) / 1000.0;
                    double speed = dur > 0 ? bytesSinceLast / dur : 0;
                    for (ClientListener l : listeners) {
                        l.onFileTransferProgress(meta, startOffset + received, totalBytes, speed);
                    }
                    lastReport = now;
                    bytesSinceLast = 0;
                }
            }

            raf.close();

            if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                notifyStateChanged(meta, TransferState.CANCELED, "Đã hủy nhận tệp (đã giữ lại file .part để tiếp tục sau).");
                return;
            }

            // 1. Explicitly verify physical file length on disk using 64-bit long
            long actualDiskLength = partFile.length();
            if (actualDiskLength != totalBytes) {
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, "File Size Incomplete");
                notifyStateChanged(meta, TransferState.FAILED, String.format("Kích thước tệp trên đĩa không khớp: %d / %d bytes!", actualDiskLength, totalBytes));
                return;
            }

            // 2. Verify final SHA-256 integrity against actual bytes on disk
            notifyStateChanged(meta, TransferState.VERIFYING, "Đang xác thực mã băm SHA-256...");
            String actualSha = HashVerifier.calculateSha256(partFile, null);
            if (meta.getSha256() != null && !meta.getSha256().equalsIgnoreCase(actualSha)) {
                FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_REJECTED, "SHA-256 Hash Mismatch");
                notifyStateChanged(meta, TransferState.FAILED, "Sai khớp mã băm SHA-256!");
                return;
            }

            // Rename .part to final destination file
            if (destFile.exists()) {
                destFile.delete();
            }
            try {
                java.nio.file.Files.move(partFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                if (!partFile.renameTo(destFile)) {
                    java.nio.file.Files.copy(partFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    partFile.delete();
                }
            }

            FileDataHandshake.sendResponse(dos, FileDataHandshake.STATUS_COMPLETE, "Verified");
            notifyStateChanged(meta, TransferState.COMPLETED, "Hoàn tất! Tệp đã lưu tại: " + destFile.getName());

            // Notify server via control channel
            sendPacket(PacketType.FILE_TRANSFER_COMPLETE, String.format("{\"transferId\":\"%s\",\"targetUser\":\"%s\"}",
                    meta.getTransferId(), meta.getSender()));

        } catch (Exception e) {
            System.err.println("[RECEIVER EXCEPTION] " + e.getMessage());
            e.printStackTrace();
            if (Boolean.TRUE.equals(canceledTransfers.get(meta.getTransferId()))) {
                notifyStateChanged(meta, TransferState.CANCELED, "Đã hủy nhận tệp.");
            } else {
                notifyStateChanged(meta, TransferState.INTERRUPTED, "Gián đoạn: " + e.getMessage());
            }
        } finally {
            activeDataSockets.remove(meta.getTransferId());
            canceledTransfers.remove(meta.getTransferId());
        }
    }

    public void cancelTransfer(String transferId) {
        if (transferId == null) return;
        activeSendingTransferIds.remove(transferId);
        canceledTransfers.put(transferId, true);
        Socket s = activeDataSockets.get(transferId);
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {}
        }
        FileMetadata meta = activeTransfers.get(transferId);
        if (meta != null) {
            notifyStateChanged(meta, TransferState.CANCELED, "Đã hủy bởi người dùng.");
        }
    }

    private void notifyStateChanged(FileMetadata meta, TransferState state, String msg) {
        for (ClientListener l : listeners) {
            l.onFileTransferStateChanged(meta, state, msg);
        }
    }

    private SecretKey getOrCreatePrivateConversationKey(String targetUser) {
        return privateConversationKeys.computeIfAbsent(targetUser.toLowerCase(), u -> CryptoUtils.generateAes128Key());
    }

    public void sendPacket(PacketType type, String jsonPayload) throws IOException {
        if (!connected.get() || controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("Socket không kết nối");
        }
        byte[] payloadBytes = jsonPayload != null ? jsonPayload.getBytes(StandardCharsets.UTF_8) : new byte[0];
        PacketHeader header = new PacketHeader(type, payloadBytes.length);
        synchronized (writeLock) {
            header.writeTo(out);
            if (payloadBytes.length > 0) {
                out.write(payloadBytes);
            }
            out.flush();
        }
    }

    private void parseOnlineUsers(String payload) {
        int idx = payload.indexOf("\"onlineUsers\":[");
        if (idx == -1) return;
        int end = payload.indexOf("]", idx);
        if (end == -1) return;
        String arr = payload.substring(idx + 15, end);
        onlineUsers.clear();
        for (String item : arr.split(",")) {
            String u = item.replace("\"", "").trim();
            if (!u.isEmpty() && !onlineUsers.contains(u)) {
                onlineUsers.add(u);
            }
        }
        if (authenticatedUser != null && !authenticatedUser.isEmpty() && !onlineUsers.contains(authenticatedUser)) {
            onlineUsers.add(authenticatedUser);
        }
        Collections.sort(onlineUsers);
    }

    private void parseRooms(String payload) {
        int idx = payload.indexOf("\"rooms\":[");
        if (idx == -1) return;
        int end = payload.indexOf("]", idx);
        if (end == -1) return;
        String arr = payload.substring(idx + 9, end);
        availableRooms.clear();
        for (String chunk : arr.split("\\},\\{")) {
            String id = extractJsonValue(chunk, "id");
            if (id != null && !id.isEmpty() && !availableRooms.contains(id)) {
                availableRooms.add(id);
            }
        }
        if (!availableRooms.contains("Lobby")) {
            availableRooms.add(0, "Lobby");
        }
    }

    // =========================================================================
    //  GROUP FILE TRANSFER METHODS
    // =========================================================================

    public FileMetadata initiateGroupFileSend(String roomId, File file, boolean encrypt, HashVerifier.ProgressCallback hashCallback)
            throws Exception {
        if (!authenticated.get()) throw new IOException("Chưa đăng nhập.");
        if (!file.exists() || !file.isFile()) throw new IOException("Tệp không tồn tại: " + file.getPath());

        long size = file.length();
        if (size > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(Constants.OVERSIZED_FILE_ERROR_MESSAGE);
        }

        String tid = UUID.randomUUID().toString();
        String sha256 = HashVerifier.calculateSha256(file, hashCallback);

        SecretKey aesKey = encrypt ? CryptoUtils.generateAes128Key() : null;
        byte[] baseIv = encrypt ? CryptoUtils.generateGcmIv() : null;

        FileMetadata meta = new FileMetadata(
                tid,
                file.getName(),
                size,
                sha256,
                0L,
                authenticatedUser,
                "room:" + roomId,
                System.currentTimeMillis(),
                encrypt,
                aesKey != null ? CryptoUtils.keyToBase64(aesKey) : null,
                baseIv != null ? java.util.Base64.getEncoder().encodeToString(baseIv) : null
        );

        activeTransfers.put(tid, meta);
        localSourceFiles.put(tid, file);

        String reqJson = String.format("{\"transferId\":\"%s\",\"fileName\":\"%s\",\"fileSize\":%d,\"sha256\":\"%s\",\"offset\":0,\"sender\":\"%s\",\"receiver\":\"room:%s\",\"roomId\":\"%s\",\"isGroup\":true,\"encrypted\":%b,\"aesKey\":\"%s\",\"baseIv\":\"%s\"}",
                tid, escapeJson(file.getName()), size, sha256, escapeJson(authenticatedUser),
                escapeJson(roomId), escapeJson(roomId), encrypt,
                aesKey != null ? CryptoUtils.keyToBase64(aesKey) : "",
                baseIv != null ? java.util.Base64.getEncoder().encodeToString(baseIv) : "");

        sendPacket(PacketType.FILE_TRANSFER_REQUEST, reqJson);
        notifyStateChanged(meta, TransferState.PENDING, "Đã gửi đề nghị tệp tới nhóm " + roomId);
        return meta;
    }

    // =========================================================================
    //  VOICE CHAT CONTROL METHODS (UDP PORT 8890 RELAY)
    // =========================================================================

    public synchronized void joinVoiceRoom(String roomId) {
        if (!authenticated.get() || roomId == null || roomId.trim().isEmpty()) return;
        try {
            if (voiceClient == null) {
                java.net.InetAddress addr = java.net.InetAddress.getByName(connectedServerIp);
                voiceClient = new dammtp2p.voice.VoiceClient(authenticatedUser, addr, connectedVoicePort);
                voiceClient.addListener(new dammtp2p.voice.VoiceClient.VoiceListener() {
                    @Override
                    public void onVoiceConnected(String r) {
                        for (ClientListener l : listeners) l.onVoiceStateChanged(r, true);
                    }

                    @Override
                    public void onVoiceDisconnected() {
                        for (ClientListener l : listeners) l.onVoiceStateChanged(null, false);
                    }

                    @Override
                    public void onLocalSpeaking(boolean isSpeaking) {
                        for (ClientListener l : listeners) l.onUserSpeaking(authenticatedUser, isSpeaking);
                    }

                    @Override
                    public void onRemoteSpeaking(String user, boolean isSpeaking) {
                        for (ClientListener l : listeners) l.onUserSpeaking(user, isSpeaking);
                    }

                    @Override
                    public void onVoiceError(String message) {}
                });
                voiceClient.start();
            }
            voiceClient.joinVoiceRoom(roomId);
            sendPacket(PacketType.VOICE_JOIN_REQ, String.format("{\"roomId\":\"%s\"}", escapeJson(roomId)));
        } catch (Exception e) {
            System.err.println("[VOICE ERROR] " + e.getMessage());
        }
    }

    public synchronized void leaveVoiceRoom() {
        if (voiceClient != null && voiceClient.isInVoice()) {
            String oldRoom = voiceClient.getCurrentRoomId();
            voiceClient.leaveVoiceRoom();
            try {
                sendPacket(PacketType.VOICE_LEAVE_REQ, String.format("{\"roomId\":\"%s\"}", escapeJson(oldRoom)));
            } catch (Exception ignored) {}
        }
    }

    public void setVoiceMuted(boolean muted) {
        if (voiceClient != null) {
            voiceClient.setMuted(muted);
        }
    }

    public boolean isVoiceMuted() {
        return voiceClient != null && voiceClient.isMuted();
    }

    public void setVoiceDeafened(boolean deafened) {
        if (voiceClient != null) {
            voiceClient.setDeafened(deafened);
        }
    }

    public boolean isVoiceDeafened() {
        return voiceClient != null && voiceClient.isDeafened();
    }

    public void setVoiceVolume(double gain) {
        if (voiceClient != null) {
            voiceClient.setVolume(gain);
        }
    }

    public double getVoiceVolume() {
        return voiceClient != null ? voiceClient.getVolume() : 1.0;
    }

    public boolean isInVoice() {
        return voiceClient != null && voiceClient.isInVoice();
    }

    public String getCurrentVoiceRoom() {
        return voiceClient != null ? voiceClient.getCurrentRoomId() : null;
    }

    public void addListener(ClientListener l) { listeners.add(l); }
    public void removeListener(ClientListener l) { listeners.remove(l); }

    public boolean isConnected() { return connected.get(); }
    public boolean isAuthenticated() { return authenticated.get(); }
    public String getAuthenticatedUser() { return authenticatedUser; }
    public String getConnectedServerIp() { return connectedServerIp; }
    public int getConnectedServerPort() { return connectedServerPort; }
    public List<String> getOnlineUsers() { return new ArrayList<>(onlineUsers); }
    public List<String> getAvailableRooms() { return new ArrayList<>(availableRooms); }

    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '\r' && json.charAt(end) != '\n') {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
