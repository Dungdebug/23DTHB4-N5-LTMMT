package dammtp2p.server;

import dammtp2p.core.protocol.PacketType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe central session registry managing all active client connections,
 * authenticated usernames, online presence, and message distribution.
 */
public class SessionManager {

    private final ServerCore server;
    private final Map<String, ClientSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> sessionsByUsername = new ConcurrentHashMap<>();

    public SessionManager(ServerCore server) {
        this.server = server;
    }

    public void registerSession(ClientSession session) {
        sessionsById.put(session.getSessionId(), session);
    }

    public synchronized boolean authenticateSession(ClientSession session, String username) {
        String cleanUser = username.trim();
        // Check if user is already logged in
        ClientSession existing = sessionsByUsername.get(cleanUser.toLowerCase());
        if (existing != null && existing != session && !existing.getSocket().isClosed()) {
            return false; // Duplicate login rejected
        }

        session.setAuthenticated(cleanUser);
        sessionsByUsername.put(cleanUser.toLowerCase(), session);

        // Broadcast to all other clients that user came online
        String broadcastJson = String.format("{\"username\":\"%s\",\"status\":\"ONLINE\",\"timestamp\":%d}",
                escapeJson(cleanUser), System.currentTimeMillis());
        broadcastExcept(session, PacketType.USER_STATUS_BROADCAST, broadcastJson);

        server.logEvent("[AUTH] User '" + cleanUser + "' logged in successfully from " + session.getSocket().getRemoteSocketAddress());
        return true;
    }

    public void removeSession(ClientSession session) {
        ClientSession removed = sessionsById.remove(session.getSessionId());
        String username = session.getUsername();
        if (username != null) {
            sessionsByUsername.remove(username.toLowerCase());
            server.getRoomManager().removeUserFromAllRooms(session);

            // Broadcast offline to remaining clients
            String broadcastJson = String.format("{\"username\":\"%s\",\"status\":\"OFFLINE\",\"timestamp\":%d}",
                    escapeJson(username), System.currentTimeMillis());
            broadcast(PacketType.USER_STATUS_BROADCAST, broadcastJson);

            server.logEvent(String.format("[DISCONNECT] User '%s' disconnected: %s", username, session.getClientDesc()));
        } else if (removed != null) {
            server.logEvent(String.format("[DISCONNECT] Connection closed: %s", session.getClientDesc()));
        }
        server.notifyUsersChanged(sessionsByUsername.size());
    }

    public ClientSession getSessionByUsername(String username) {
        if (username == null) return null;
        return sessionsByUsername.get(username.trim().toLowerCase());
    }

    public List<String> getOnlineUsernames() {
        List<String> list = new ArrayList<>();
        for (ClientSession session : sessionsByUsername.values()) {
            if (session.isAuthenticated()) {
                list.add(session.getUsername());
            }
        }
        Collections.sort(list);
        return list;
    }

    public int getOnlineCount() {
        return sessionsByUsername.size();
    }

    public int getOnlineUserCount() {
        return getOnlineCount();
    }

    public int getTotalSessionCount() {
        return sessionsById.size();
    }

    public List<ClientSession> getAllSessions() {
        return new ArrayList<>(sessionsById.values());
    }

    public void broadcast(PacketType type, String jsonPayload) {
        for (ClientSession session : sessionsById.values()) {
            if (session.isAuthenticated()) {
                try {
                    session.sendPacket(type, jsonPayload);
                } catch (IOException ignored) {}
            }
        }
    }

    public void broadcastExcept(ClientSession except, PacketType type, String jsonPayload) {
        for (ClientSession session : sessionsById.values()) {
            if (session != except && session.isAuthenticated()) {
                try {
                    session.sendPacket(type, jsonPayload);
                } catch (IOException ignored) {}
            }
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
