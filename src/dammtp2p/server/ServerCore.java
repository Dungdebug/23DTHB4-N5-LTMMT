package dammtp2p.server;

import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.network.HandshakePayload;
import dammtp2p.core.network.IncompatibleProtocolException;
import dammtp2p.core.protocol.PacketType;
import dammtp2p.core.protocol.ProtocolConstants;
import dammtp2p.util.Constants;
import dammtp2p.util.NetworkUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central Multi-Client TCP Server coordinating:
 * <ul>
 *   <li>Control channel listener (Port 8888) with dynamic thread pool.</li>
 *   <li>File data relay listener (Port 8889).</li>
 *   <li>Authentication, session presence, private chat routing, room broadcast.</li>
 *   <li>ZeroTier virtual LAN network adapter discovery.</li>
 * </ul>
 */
public class ServerCore {

    public interface ServerEventListener {
        void onEvent(String message);
        void onStateChanged(boolean running);
        void onUsersChanged(int count);
        void onTransfersChanged(int count);
    }

    private final int controlPort;
    private final int filePort;
    private final int voicePort;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final ServerFileRelayManager fileRelayManager;
    private final dammtp2p.voice.VoiceServerRelay voiceRelay;

    private ServerSocket controlServerSocket;
    private final ExecutorService clientExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> recentEvents = new CopyOnWriteArrayList<>();

    public ServerCore(int controlPort, int filePort, int voicePort) {
        this.controlPort = controlPort;
        this.filePort = filePort;
        this.voicePort = voicePort;
        this.authService = new AuthService();
        this.sessionManager = new SessionManager(this);
        this.roomManager = new RoomManager(this);
        this.fileRelayManager = new ServerFileRelayManager(this);
        this.voiceRelay = new dammtp2p.voice.VoiceServerRelay(voicePort);

        this.clientExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "server-client-handler");
            t.setDaemon(true);
            return t;
        });
    }

    public ServerCore(int controlPort, int filePort) {
        this(controlPort, filePort, Constants.DEFAULT_VOICE_PORT);
    }

    public ServerCore() {
        this(Constants.DEFAULT_CONTROL_PORT, Constants.DEFAULT_FILE_TRANSFER_PORT, Constants.DEFAULT_VOICE_PORT);
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;

        controlServerSocket = new ServerSocket();
        controlServerSocket.setReuseAddress(true);
        controlServerSocket.bind(new InetSocketAddress("0.0.0.0", controlPort), 100);
        running.set(true);

        fileRelayManager.start(filePort);
        try {
            voiceRelay.start();
            logEvent("[VOICE RELAY] UDP voice listener started on 0.0.0.0:" + voicePort);
        } catch (Exception e) {
            logEvent("[WARN] Could not bind UDP voice port " + voicePort + ": " + e.getMessage());
        }

        clientExecutor.submit(this::controlAcceptLoop);

        logEvent("[SERVER] Control channel listening on 0.0.0.0:" + controlPort);
        notifyStateChanged(true);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (controlServerSocket != null && !controlServerSocket.isClosed()) {
            try {
                controlServerSocket.close();
            } catch (IOException ignored) {}
        }

        // Gracefully close all active client sessions
        for (ClientSession session : sessionManager.getAllSessions()) {
            session.close();
        }

        voiceRelay.stop();
        fileRelayManager.stop();
        logEvent("[SERVER] Server stopped.");
        notifyStateChanged(false);
    }

    private void controlAcceptLoop() {
        while (running.get() && controlServerSocket != null && !controlServerSocket.isClosed()) {
            try {
                Socket clientSocket = controlServerSocket.accept();
                String sessionId = UUID.randomUUID().toString();
                ClientSession session = new ClientSession(sessionId, clientSocket, this);
                sessionManager.registerSession(session);

                logEvent(String.format("[CONNECT] New TCP connection from %s", clientSocket.getRemoteSocketAddress()));
                clientExecutor.submit(session::runReaderLoop);
            } catch (SocketException e) {
                if (!running.get()) break;
            } catch (IOException e) {
                if (!running.get()) break;
            }
        }
    }

    /**
     * Authoritative packet dispatcher for all incoming client control packets.
     */
    public void handleClientPacket(ClientSession session, PacketType type, String payload) {
        try {
            switch (type) {
                case HANDSHAKE_REQ:
                    handleHandshakeReq(session, payload);
                    break;
                case LOGIN_REQ:
                    handleLogin(session, payload);
                    break;
                case REGISTER_REQ:
                    handleRegister(session, payload);
                    break;
                case USER_LIST_REQ:
                    handleUserListReq(session);
                    break;
                case ROOM_LIST_REQ:
                    handleRoomListReq(session);
                    break;
                case ROOM_CREATE_REQ:
                    handleRoomCreate(session, payload);
                    break;
                case ROOM_JOIN_REQ:
                    handleRoomJoin(session, payload);
                    break;
                case ROOM_LEAVE_REQ:
                    handleRoomLeave(session, payload);
                    break;
                case ROOM_CHAT_MESSAGE:
                    handleRoomChatMessage(session, payload);
                    break;
                case PRIVATE_CHAT_MESSAGE:
                case CHAT_MESSAGE:
                    handlePrivateChatMessage(session, payload);
                    break;
                case FILE_TRANSFER_REQUEST:
                    handleFileTransferRequest(session, payload);
                    break;
                case FILE_TRANSFER_ACCEPT:
                    handleFileTransferAccept(session, payload);
                    break;
                case FILE_TRANSFER_REJECT:
                    handleFileTransferReject(session, payload);
                    break;
                case FILE_TRANSFER_RESUME_REQ:
                case FILE_TRANSFER_RESUME_ACK:
                case FILE_TRANSFER_CANCEL:
                case FILE_TRANSFER_COMPLETE:
                case FILE_TRANSFER_VERIFY_FAILED:
                    routeFileNegotiationPacket(session, type, payload);
                    break;
                case VOICE_JOIN_REQ:
                    handleVoiceJoin(session, payload);
                    break;
                case VOICE_LEAVE_REQ:
                    handleVoiceLeave(session, payload);
                    break;
                case VOICE_CALL_REQUEST:
                case VOICE_CALL_ACCEPT:
                case VOICE_CALL_REJECT:
                case VOICE_CALL_END:
                    routeVoiceCallPacket(session, type, payload);
                    break;
                case HEARTBEAT:
                    session.sendPacket(PacketType.HEARTBEAT_ACK, "{\"status\":\"PONG\"}");
                    break;
                case FRIEND_ADD_REQ:
                    handleFriendAddReq(session, payload);
                    break;
                case FRIEND_REMOVE_REQ:
                    handleFriendRemoveReq(session, payload);
                    break;
                case DISCONNECT:
                    session.close();
                    break;
                default:
                    logEvent("[WARN] Unknown packet type from " + session.getClientDesc() + ": " + type);
            }
        } catch (Exception e) {
            logEvent("[ERROR] Error handling " + type + " from " + session.getClientDesc() + ": " + e.getMessage());
        }
    }

    private void handleHandshakeReq(ClientSession session, String payload) throws IOException {
        try {
            HandshakePayload hs = HandshakePayload.fromJson(payload);
            hs.validate();

            session.setHandshakeCompleted(true);
            session.setClientDisplayName(hs.getDisplayName());

            String ackJson = String.format(
                    "{\"success\":true,\"serverName\":\"%s\",\"version\":%d,\"controlPort\":%d,\"filePort\":%d,\"voicePort\":%d,\"timestamp\":%d}",
                    Constants.APP_NAME + "-Central-Server",
                    ProtocolConstants.PROTOCOL_VERSION,
                    controlPort,
                    filePort,
                    voicePort,
                    System.currentTimeMillis()
            );
            session.sendPacket(PacketType.HANDSHAKE_ACK, ackJson);
            logEvent(String.format("[HANDSHAKE] Verified connection from %s [Client: '%s', Protocol: v%d]",
                    session.getSocket().getRemoteSocketAddress(), hs.getDisplayName(), hs.getProtocolVersion()));
        } catch (IncompatibleProtocolException e) {
            String rejectJson = String.format("{\"success\":false,\"message\":\"%s\"}", escapeJson(e.getMessage()));
            session.sendPacket(PacketType.HANDSHAKE_ACK, rejectJson);
            logEvent(String.format("[HANDSHAKE REJECTED] %s: %s", session.getSocket().getRemoteSocketAddress(), e.getMessage()));
            session.close();
        } catch (Exception e) {
            String rejectJson = String.format("{\"success\":false,\"message\":\"Malformed handshake: %s\"}", escapeJson(e.getMessage()));
            session.sendPacket(PacketType.HANDSHAKE_ACK, rejectJson);
            logEvent(String.format("[HANDSHAKE ERROR] %s: %s", session.getSocket().getRemoteSocketAddress(), e.getMessage()));
            session.close();
        }
    }

    private void handleLogin(ClientSession session, String payload) throws IOException {
        String user = extractJsonValue(payload, "username");
        String pass = extractJsonValue(payload, "password");

        if (user == null || pass == null) {
            session.sendPacket(PacketType.LOGIN_RESP, "{\"success\":false,\"message\":\"Thiếu tên đăng nhập hoặc mật khẩu\"}");
            return;
        }

        boolean ok = authService.authenticate(user, pass.toCharArray());
        if (!ok) {
            session.sendPacket(PacketType.LOGIN_RESP, "{\"success\":false,\"message\":\"Sai tên đăng nhập hoặc mật khẩu!\"}");
            return;
        }

        boolean sessionOk = sessionManager.authenticateSession(session, user);
        if (!sessionOk) {
            session.sendPacket(PacketType.LOGIN_RESP, "{\"success\":false,\"message\":\"Tài khoản này đang đăng nhập ở nơi khác!\"}");
            return;
        }

        // Auto join all rooms so members receive broadcast messages and group file transfers
        for (RoomManager.ChatRoom r : roomManager.getAllRooms()) {
            roomManager.joinRoom(r.getRoomId(), session);
        }

        // Build response JSON with online users & room list
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":true,\"username\":\"").append(escapeJson(user)).append("\",");
        sb.append("\"onlineUsers\":[");
        List<String> users = sessionManager.getOnlineUsernames();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(users.get(i))).append("\"");
        }
        sb.append("],");

        sb.append("\"rooms\":[");
        List<RoomManager.ChatRoom> rooms = roomManager.getAllRooms();
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(escapeJson(rooms.get(i).getRoomId())).append("\",\"name\":\"")
              .append(escapeJson(rooms.get(i).getDisplayName())).append("\"}");
        }
        sb.append("]}");

        session.sendPacket(PacketType.LOGIN_RESP, sb.toString());
        notifyUsersChanged(sessionManager.getOnlineCount());
    }

    private void handleRegister(ClientSession session, String payload) throws IOException {
        String user = extractJsonValue(payload, "username");
        String pass = extractJsonValue(payload, "password");

        if (user == null || pass == null || user.trim().isEmpty() || pass.trim().isEmpty()) {
            session.sendPacket(PacketType.REGISTER_RESP, "{\"success\":false,\"message\":\"Dữ liệu đăng ký không hợp lệ\"}");
            return;
        }

        try {
            boolean registered = authService.register(user, pass.toCharArray());
            if (registered) {
                logEvent("[AUTH] New user registered: " + user);
                session.sendPacket(PacketType.REGISTER_RESP, "{\"success\":true,\"message\":\"Đăng ký tài khoản thành công!\"}");
            } else {
                session.sendPacket(PacketType.REGISTER_RESP, "{\"success\":false,\"message\":\"Tên đăng nhập đã tồn tại!\"}");
            }
        } catch (Exception e) {
            session.sendPacket(PacketType.REGISTER_RESP, "{\"success\":false,\"message\":\"Lỗi đăng ký: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleUserListReq(ClientSession session) throws IOException {
        StringBuilder sb = new StringBuilder("{\"onlineUsers\":[");
        List<String> users = sessionManager.getOnlineUsernames();
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(users.get(i))).append("\"");
        }
        sb.append("]}");
        session.sendPacket(PacketType.USER_LIST_RESP, sb.toString());
    }

    private void handleRoomListReq(ClientSession session) throws IOException {
        StringBuilder sb = new StringBuilder("{\"rooms\":[");
        List<RoomManager.ChatRoom> rooms = roomManager.getAllRooms();
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(escapeJson(rooms.get(i).getRoomId())).append("\",\"name\":\"")
              .append(escapeJson(rooms.get(i).getDisplayName())).append("\"}");
        }
        sb.append("]}");
        session.sendPacket(PacketType.ROOM_LIST_RESP, sb.toString());
    }

    private void handleRoomCreate(ClientSession session, String payload) throws IOException {
        String roomId = extractJsonValue(payload, "id");
        String name = extractJsonValue(payload, "name");
        if (roomId != null && !roomId.trim().isEmpty()) {
            roomManager.getOrCreateRoom(roomId, name);
            logEvent(String.format("[ROOM] Room '%s' created by %s", roomId, session.getUsername()));
            sessionManager.broadcast(PacketType.ROOM_LIST_RESP, buildRoomsJson());
        }
    }

    private void handleRoomJoin(ClientSession session, String payload) throws IOException {
        String roomId = extractJsonValue(payload, "id");
        if (roomId != null) {
            roomManager.joinRoom(roomId, session);
        }
    }

    private void handleRoomLeave(ClientSession session, String payload) throws IOException {
        String roomId = extractJsonValue(payload, "id");
        if (roomId != null) {
            roomManager.leaveRoom(roomId, session);
        }
    }

    private void handleRoomChatMessage(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String roomId = extractJsonValue(payload, "roomId");
        if (roomId != null) {
            // One-to-many broadcast to room members
            roomManager.broadcastToRoom(roomId, session, PacketType.ROOM_CHAT_MESSAGE, payload);
            logEvent(String.format("[GROUP CHAT] %s in room '%s'", session.getUsername(), roomId));
            ChatMessage chatMsg = ChatMessage.fromJson(payload);
            if (chatMsg != null) {
                StorageEngine.getInstance().recordChatMessage(chatMsg);
            }
        }
    }

    private void handlePrivateChatMessage(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String receiverName = extractJsonValue(payload, "receiver");
        if (receiverName == null) return;

        ChatMessage chatMsg = ChatMessage.fromJson(payload);
        if (chatMsg != null) {
            StorageEngine.getInstance().recordChatMessage(chatMsg);
        }

        ClientSession targetSession = sessionManager.getSessionByUsername(receiverName);
        if (targetSession != null && targetSession.isAuthenticated()) {
            targetSession.sendPacket(PacketType.PRIVATE_CHAT_MESSAGE, payload);
            logEvent(String.format("[PRIVATE CHAT] %s -> %s", session.getUsername(), receiverName));
        } else {
            // Target user is offline
            String errorJson = String.format("{\"type\":\"ERROR\",\"message\":\"Người dùng '%s' hiện không trực tuyến.\",\"receiver\":\"%s\"}",
                    escapeJson(receiverName), escapeJson(session.getUsername()));
            session.sendPacket(PacketType.CHAT_MESSAGE, errorJson);
        }
    }

    private void handleFriendAddReq(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String target = extractJsonValue(payload, "target");
        if (target == null || target.trim().isEmpty()) return;
        String me = session.getUsername();
        boolean added = dammtp2p.client.FriendsManager.getInstance().addFriend(me, target);
        logEvent(String.format("[FRIEND ADD] %s added friend %s (success=%b)", me, target, added));

        String respJson = String.format("{\"success\":%b,\"target\":\"%s\",\"message\":\"%s\"}",
                added, escapeJson(target), added ? "Đã kết bạn thành công với @" + target : "@" + target + " đã có trong danh sách bạn bè.");
        session.sendPacket(PacketType.FRIEND_ADD_RESP, respJson);

        ClientSession targetSession = sessionManager.getSessionByUsername(target);
        if (targetSession != null && targetSession.isAuthenticated()) {
            String notifyJson = String.format("{\"success\":true,\"target\":\"%s\",\"message\":\"@%s đã trở thành bạn bè với bạn!\"}",
                    escapeJson(me), escapeJson(me));
            targetSession.sendPacket(PacketType.FRIEND_ADD_RESP, notifyJson);
            handleUserListReq(targetSession);
        }
        handleUserListReq(session);
    }

    private void handleFriendRemoveReq(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String target = extractJsonValue(payload, "target");
        if (target == null || target.trim().isEmpty()) return;
        String me = session.getUsername();
        boolean removed = dammtp2p.client.FriendsManager.getInstance().removeFriend(me, target);
        logEvent(String.format("[FRIEND REMOVE] %s removed friend %s", me, target));

        String respJson = String.format("{\"success\":%b,\"target\":\"%s\",\"message\":\"Đã hủy kết bạn với @" + target + ".\"}",
                removed, escapeJson(target));
        session.sendPacket(PacketType.FRIEND_ADD_RESP, respJson);

        ClientSession targetSession = sessionManager.getSessionByUsername(target);
        if (targetSession != null && targetSession.isAuthenticated()) {
            handleUserListReq(targetSession);
        }
        handleUserListReq(session);
    }

    private void handleFileTransferRequest(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String transferId = extractJsonValue(payload, "transferId");
        String receiverName = extractJsonValue(payload, "receiver");
        String roomId = extractJsonValue(payload, "roomId");
        String isGroupStr = extractJsonValue(payload, "isGroup");

        if (roomId == null || roomId.trim().isEmpty()) {
            if (receiverName != null) {
                String cleanTarget = receiverName.replace("[Nhóm] # ", "").replace("[Nhóm]", "").replace("room:", "").trim();
                if (receiverName.startsWith("room:") || receiverName.startsWith("[Nhóm]") || roomManager.getRoom(cleanTarget) != null) {
                    roomId = cleanTarget;
                }
            }
        }
        boolean isGroup = "true".equalsIgnoreCase(isGroupStr) || (roomId != null && !roomId.isEmpty());

        long fileSize = parseLongSafe(extractJsonValue(payload, "fileSize"));
        if (fileSize < 0L || fileSize > Constants.MAX_FILE_SIZE_BYTES) {
            String rejectJson = String.format("{\"transferId\":\"%s\",\"reason\":\"%s\"}",
                    escapeJson(transferId), escapeJson(Constants.OVERSIZED_FILE_ERROR_MESSAGE));
            session.sendPacket(PacketType.FILE_TRANSFER_REJECT, rejectJson);
            logEvent(String.format("[FILE REJECT SECURITY] %s offered file exceeding 20 GiB limit (%d bytes, max: %d)",
                    session.getUsername(), fileSize, Constants.MAX_FILE_SIZE_BYTES));
            return;
        }

        if (isGroup && roomId != null) {
            String fileName = extractJsonValue(payload, "fileName");
            fileRelayManager.registerGroupTransfer(transferId, session.getUsername(), roomId, fileName, fileSize);
            RoomManager.ChatRoom room = roomManager.getRoom(roomId);
            if (room != null) {
                for (ClientSession member : room.getMembers()) {
                    if (member != session && member.isAuthenticated()) {
                        member.sendPacket(PacketType.FILE_TRANSFER_REQUEST, payload);
                    }
                }
                logEvent(String.format("[GROUP FILE OFFER] %s offers file to room '%s' (%d bytes)",
                        session.getUsername(), roomId, fileSize));
            }
            return;
        }

        if (receiverName == null) return;

        ClientSession targetSession = sessionManager.getSessionByUsername(receiverName);
        if (targetSession != null && targetSession.isAuthenticated()) {
            targetSession.sendPacket(PacketType.FILE_TRANSFER_REQUEST, payload);
            logEvent(String.format("[FILE OFFER] %s offers file to %s (%d bytes)",
                    session.getUsername(), receiverName, fileSize));
        } else {
            String rejectJson = String.format("{\"transferId\":\"%s\",\"reason\":\"Người nhận không trực tuyến\"}",
                    escapeJson(transferId));
            session.sendPacket(PacketType.FILE_TRANSFER_REJECT, rejectJson);
        }
    }

    private void handleFileTransferAccept(ClientSession session, String payload) throws IOException {
        if (!session.isAuthenticated()) return;
        String transferId = extractJsonValue(payload, "transferId");
        String senderName = extractJsonValue(payload, "sender");
        long fileSize = parseLongSafe(extractJsonValue(payload, "fileSize"));
        long offset = parseLongSafe(extractJsonValue(payload, "offset"));
        String fileName = extractJsonValue(payload, "fileName");

        if (fileSize < 0L || fileSize > Constants.MAX_FILE_SIZE_BYTES || offset < 0L || offset > fileSize) {
            String rejectJson = String.format("{\"transferId\":\"%s\",\"reason\":\"%s\"}",
                    escapeJson(transferId), escapeJson(Constants.OVERSIZED_FILE_ERROR_MESSAGE));
            session.sendPacket(PacketType.FILE_TRANSFER_REJECT, rejectJson);
            logEvent(String.format("[FILE ACCEPT SECURITY] Rejected accept with invalid size/offset from %s (size: %d, offset: %d)",
                    session.getUsername(), fileSize, offset));
            return;
        }

        if (fileRelayManager.isGroupTransfer(transferId)) {
            fileRelayManager.registerGroupAccept(transferId, session.getUsername());
            ClientSession senderSession = sessionManager.getSessionByUsername(senderName);
            if (senderSession != null && senderSession.isAuthenticated()) {
                senderSession.sendPacket(PacketType.FILE_TRANSFER_ACCEPT, payload);
                logEvent(String.format("[GROUP FILE ACCEPT] %s accepted group file from %s",
                        session.getUsername(), senderName));
                notifyTransfersChanged(fileRelayManager.getActiveTransferCount());
            }
            return;
        }

        fileRelayManager.registerTransferNegotiation(transferId, senderName, session.getUsername(), fileName, fileSize, offset);

        ClientSession senderSession = sessionManager.getSessionByUsername(senderName);
        if (senderSession != null && senderSession.isAuthenticated()) {
            senderSession.sendPacket(PacketType.FILE_TRANSFER_ACCEPT, payload);
            logEvent(String.format("[FILE ACCEPT] %s accepted file from %s (offset: %d / %d bytes)",
                    session.getUsername(), senderName, offset, fileSize));
            notifyTransfersChanged(fileRelayManager.getActiveTransferCount());
        }
    }

    private void handleFileTransferReject(ClientSession session, String payload) throws IOException {
        String senderName = extractJsonValue(payload, "sender");
        ClientSession senderSession = sessionManager.getSessionByUsername(senderName);
        if (senderSession != null && senderSession.isAuthenticated()) {
            senderSession.sendPacket(PacketType.FILE_TRANSFER_REJECT, payload);
            logEvent(String.format("[FILE REJECT] %s rejected file from %s", session.getUsername(), senderName));
        }
    }

    private void handleVoiceJoin(ClientSession session, String payload) throws IOException {
        String roomId = extractJsonValue(payload, "roomId");
        if (roomId != null && session.isAuthenticated()) {
            roomManager.joinVoice(roomId, session);
            session.sendPacket(PacketType.VOICE_JOIN_RESP, String.format("{\"success\":true,\"roomId\":\"%s\",\"voicePort\":%d}",
                    escapeJson(roomId), voicePort));
        }
    }

    private void handleVoiceLeave(ClientSession session, String payload) throws IOException {
        String roomId = extractJsonValue(payload, "roomId");
        if (roomId != null && session.isAuthenticated()) {
            roomManager.leaveVoice(roomId, session);
        } else {
            roomManager.removeUserFromAllVoice(session);
        }
    }

    private void routeVoiceCallPacket(ClientSession session, PacketType type, String payload) throws IOException {
        String receiverName = extractJsonValue(payload, "receiver");
        if (receiverName == null) {
            receiverName = extractJsonValue(payload, "targetUser");
        }
        if (receiverName != null) {
            ClientSession targetSession = sessionManager.getSessionByUsername(receiverName);
            if (targetSession != null && targetSession.isAuthenticated()) {
                targetSession.sendPacket(type, payload);
            }
        }
    }

    private void routeFileNegotiationPacket(ClientSession session, PacketType type, String payload) throws IOException {
        String targetName = extractJsonValue(payload, "targetUser");
        if (targetName == null) {
            targetName = extractJsonValue(payload, "sender");
        }
        if (targetName == null) {
            targetName = extractJsonValue(payload, "receiver");
        }
        if (targetName != null) {
            ClientSession targetSession = sessionManager.getSessionByUsername(targetName);
            if (targetSession != null) {
                targetSession.sendPacket(type, payload);
            }
        }
    }

    private String buildRoomsJson() {
        StringBuilder sb = new StringBuilder("{\"rooms\":[");
        List<RoomManager.ChatRoom> rooms = roomManager.getAllRooms();
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(escapeJson(rooms.get(i).getRoomId())).append("\",\"name\":\"")
              .append(escapeJson(rooms.get(i).getDisplayName())).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public void logEvent(String msg) {
        System.out.println(msg);
        recentEvents.add(msg);
        if (recentEvents.size() > 200) {
            recentEvents.remove(0);
        }
        for (ServerEventListener l : listeners) {
            l.onEvent(msg);
        }
        try {
            StorageEngine.getInstance().appendAuditLog("SERVER_EVENT", "SYSTEM", "-", msg);
        } catch (Exception ignored) {}
    }

    public void addListener(ServerEventListener l) {
        listeners.add(l);
    }

    public void removeListener(ServerEventListener l) {
        listeners.remove(l);
    }

    void notifyStateChanged(boolean running) {
        for (ServerEventListener l : listeners) l.onStateChanged(running);
    }

    void notifyUsersChanged(int count) {
        for (ServerEventListener l : listeners) l.onUsersChanged(count);
    }

    void notifyTransfersChanged(int count) {
        for (ServerEventListener l : listeners) l.onTransfersChanged(count);
    }

    public SessionManager getSessionManager() { return sessionManager; }
    public RoomManager getRoomManager() { return roomManager; }
    public ServerFileRelayManager getFileRelayManager() { return fileRelayManager; }
    public dammtp2p.voice.VoiceServerRelay getVoiceRelay() { return voiceRelay; }
    public boolean isRunning() { return running.get(); }
    public int getControlPort() { return controlPort; }
    public int getFilePort() { return filePort; }
    public int getVoicePort() { return voicePort; }
    public List<String> getRecentEvents() { return recentEvents; }

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

    private long parseLongSafe(String s) {
        if (s == null) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
