package dammtp2p.server;

import dammtp2p.core.protocol.PacketType;
import dammtp2p.security.CryptoUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

/**
 * Thread-safe Room Manager supporting Group Chat, Room creation/join/leave,
 * AES-128 Room Key distribution, and one-to-many message broadcast.
 */
public class RoomManager {

    public static class ChatRoom {
        private final String roomId;
        private final String displayName;
        private final SecretKey roomAesKey;
        private final Set<ClientSession> members = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private final Set<String> voiceMembers = Collections.newSetFromMap(new ConcurrentHashMap<>());

        public ChatRoom(String roomId, String displayName) {
            this.roomId = roomId;
            this.displayName = displayName;
            this.roomAesKey = CryptoUtils.generateAes128Key();
        }

        public String getRoomId() {
            return roomId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public SecretKey getRoomAesKey() {
            return roomAesKey;
        }

        public Set<ClientSession> getMembers() {
            return members;
        }

        public Set<String> getVoiceMembers() {
            return voiceMembers;
        }

        public void addMember(ClientSession session) {
            members.add(session);
        }

        public void removeMember(ClientSession session) {
            members.remove(session);
            if (session.isAuthenticated()) {
                voiceMembers.remove(session.getUsername());
            }
        }

        public void addVoiceMember(String username) {
            if (username != null) voiceMembers.add(username);
        }

        public void removeVoiceMember(String username) {
            if (username != null) voiceMembers.remove(username);
        }

        public List<String> getMemberUsernames() {
            List<String> list = new ArrayList<>();
            for (ClientSession session : members) {
                if (session.isAuthenticated()) {
                    list.add(session.getUsername());
                }
            }
            Collections.sort(list);
            return list;
        }
    }

    private final ServerCore server;
    private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    public RoomManager(ServerCore server) {
        this.server = server;
        // Create default persistent rooms
        getOrCreateRoom("Lobby", "Phòng Chung (Lobby)");
        getOrCreateRoom("General", "Thảo Luận Chung (General)");
        getOrCreateRoom("Voice_Lounge", "🔊 Kênh Thoại Voice Lounge");
        getOrCreateRoom("MMT_NHOM05", "Nhóm 05 MMT");
    }

    public ChatRoom getOrCreateRoom(String roomId, String displayName) {
        String cleanId = roomId.trim();
        return rooms.computeIfAbsent(cleanId.toLowerCase(), id -> new ChatRoom(cleanId, displayName != null ? displayName : cleanId));
    }

    public ChatRoom getRoom(String roomId) {
        if (roomId == null) return null;
        return rooms.get(roomId.trim().toLowerCase());
    }

    public synchronized boolean joinRoom(String roomId, ClientSession session) {
        ChatRoom room = getOrCreateRoom(roomId, roomId);
        room.addMember(session);
        session.addRoom(room.getRoomId());

        server.logEvent(String.format("[ROOM] User '%s' joined room '%s'", session.getUsername(), room.getRoomId()));

        // Distribute room AES key to the client
        try {
            String keyBase64 = CryptoUtils.keyToBase64(room.getRoomAesKey());
            String keyPayload = String.format("{\"roomId\":\"%s\",\"key\":\"%s\"}", room.getRoomId(), keyBase64);
            session.sendPacket(PacketType.ROOM_KEY_PAYLOAD, keyPayload);
        } catch (IOException ignored) {}

        // Broadcast member joined notification to room
        String memberJoined = String.format("{\"roomId\":\"%s\",\"username\":\"%s\",\"event\":\"JOIN\"}",
                room.getRoomId(), session.getUsername());
        broadcastToRoom(room.getRoomId(), null, PacketType.ROOM_MEMBERS_RESP, memberJoined);

        // Also notify about existing voice members in this room
        broadcastVoiceMembers(room);

        // Replay recent room messages from StorageEngine to joining member
        try {
            List<StorageEngine.ChatMessageRecord> recent = StorageEngine.getInstance().getRecentRoomMessages(room.getRoomId(), 30);
            for (StorageEngine.ChatMessageRecord r : recent) {
                session.sendPacket(PacketType.ROOM_CHAT_MESSAGE, r.toJson());
            }
        } catch (Exception ignored) {}

        return true;
    }

    public synchronized void leaveRoom(String roomId, ClientSession session) {
        ChatRoom room = getRoom(roomId);
        if (room != null) {
            room.removeMember(session);
            session.removeRoom(room.getRoomId());
            server.logEvent(String.format("[ROOM] User '%s' left room '%s'", session.getUsername(), room.getRoomId()));

            String memberLeft = String.format("{\"roomId\":\"%s\",\"username\":\"%s\",\"event\":\"LEAVE\"}",
                    room.getRoomId(), session.getUsername());
            broadcastToRoom(room.getRoomId(), null, PacketType.ROOM_MEMBERS_RESP, memberLeft);
            broadcastVoiceMembers(room);
        }
    }

    public void removeUserFromAllRooms(ClientSession session) {
        for (String roomId : session.getJoinedRooms()) {
            leaveRoom(roomId, session);
        }
        removeUserFromAllVoice(session);
    }

    public synchronized void joinVoice(String roomId, ClientSession session) {
        ChatRoom room = getOrCreateRoom(roomId, roomId);
        if (session.isAuthenticated()) {
            room.addVoiceMember(session.getUsername());
            server.logEvent(String.format("[VOICE] User '%s' joined voice channel '%s'", session.getUsername(), room.getRoomId()));
            broadcastVoiceMembers(room);
        }
    }

    public synchronized void leaveVoice(String roomId, ClientSession session) {
        ChatRoom room = getRoom(roomId);
        if (room != null && session.isAuthenticated()) {
            room.removeVoiceMember(session.getUsername());
            server.logEvent(String.format("[VOICE] User '%s' left voice channel '%s'", session.getUsername(), room.getRoomId()));
            broadcastVoiceMembers(room);
        }
    }

    public synchronized void removeUserFromAllVoice(ClientSession session) {
        if (!session.isAuthenticated()) return;
        for (ChatRoom room : rooms.values()) {
            if (room.getVoiceMembers().remove(session.getUsername())) {
                server.logEvent(String.format("[VOICE] User '%s' left voice channel '%s'", session.getUsername(), room.getRoomId()));
                broadcastVoiceMembers(room);
            }
        }
    }

    public void broadcastVoiceMembers(ChatRoom room) {
        List<String> list = new ArrayList<>(room.getVoiceMembers());
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"roomId\":\"").append(room.getRoomId()).append("\",\"members\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]}");
        broadcastToRoom(room.getRoomId(), null, PacketType.VOICE_MEMBERS_BROADCAST, sb.toString());
    }

    /**
     * One-to-many broadcast of group message to all authenticated members of the room.
     */
    public void broadcastToRoom(String roomId, ClientSession sender, PacketType type, String jsonPayload) {
        ChatRoom room = getRoom(roomId);
        if (room == null) return;

        for (ClientSession member : room.getMembers()) {
            if (member.isAuthenticated() && (sender == null || member != sender)) {
                try {
                    member.sendPacket(type, jsonPayload);
                } catch (IOException ignored) {}
            }
        }
    }

    public List<ChatRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
}
