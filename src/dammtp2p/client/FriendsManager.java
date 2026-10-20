package dammtp2p.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages client-side friendship relations and persistent storage.
 * Tracks friends list for each user, their Online/Offline presence,
 * and handles adding/removing friends directly from chat groups.
 */
public class FriendsManager {

    private static final FriendsManager INSTANCE = new FriendsManager();
    private final File storageFile;
    // Map of username (lower-case) -> Set of friends (case-preserved usernames)
    private final Map<String, Set<String>> userFriends = new ConcurrentHashMap<>();

    public static FriendsManager getInstance() {
        return INSTANCE;
    }

    public FriendsManager(File storageFile) {
        this.storageFile = storageFile;
        loadFriends();
        ensureSeedFriends();
    }

    private FriendsManager() {
        this(new File("server_data", "friends.json"));
    }

    private void ensureSeedFriends() {
        // Pre-seed default friendly connections for testing
        boolean modified = false;
        if (!userFriends.containsKey("alice")) {
            addFriendInternal("alice", "bob");
            addFriendInternal("alice", "charlie");
            modified = true;
        }
        if (!userFriends.containsKey("bob")) {
            addFriendInternal("bob", "alice");
            modified = true;
        }
        if (!userFriends.containsKey("tan kiet")) {
            addFriendInternal("tan kiet", "giasuo");
            modified = true;
        }
        if (modified) {
            saveFriends();
        }
    }

    /**
     * Checks if target is a friend of currentUser.
     */
    public boolean isFriend(String currentUser, String target) {
        if (currentUser == null || target == null) return false;
        Set<String> friends = userFriends.get(currentUser.trim().toLowerCase());
        if (friends == null) return false;
        for (String f : friends) {
            if (f.equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds target to currentUser's friends list.
     * @return true if friend was newly added, false if already a friend or invalid
     */
    public synchronized boolean addFriend(String currentUser, String target) {
        if (currentUser == null || target == null) return false;
        String userClean = currentUser.trim();
        String friendClean = target.trim();
        if (userClean.equalsIgnoreCase(friendClean)) return false; // Cannot friend self

        if (addFriendInternal(userClean, friendClean)) {
            // Also establish reciprocal friendship
            addFriendInternal(friendClean, userClean);
            saveFriends();
            return true;
        }
        return false;
    }

    private boolean addFriendInternal(String user, String friend) {
        String key = user.toLowerCase();
        Set<String> set = userFriends.computeIfAbsent(key, k -> Collections.synchronizedSet(new LinkedHashSet<>()));
        synchronized (set) {
            for (String existing : set) {
                if (existing.equalsIgnoreCase(friend)) {
                    return false;
                }
            }
            set.add(friend);
            return true;
        }
    }

    /**
     * Removes target from currentUser's friends list.
     */
    public synchronized boolean removeFriend(String currentUser, String target) {
        if (currentUser == null || target == null) return false;
        String userClean = currentUser.trim();
        String friendClean = target.trim();

        boolean removed = false;
        Set<String> set1 = userFriends.get(userClean.toLowerCase());
        if (set1 != null) {
            synchronized (set1) {
                removed = set1.removeIf(f -> f.equalsIgnoreCase(friendClean));
            }
        }
        Set<String> set2 = userFriends.get(friendClean.toLowerCase());
        if (set2 != null) {
            synchronized (set2) {
                set2.removeIf(f -> f.equalsIgnoreCase(userClean));
            }
        }
        if (removed) {
            saveFriends();
        }
        return removed;
    }

    /**
     * Gets list of all friends for the user.
     */
    public List<String> getFriends(String currentUser) {
        if (currentUser == null) return Collections.emptyList();
        Set<String> set = userFriends.get(currentUser.trim().toLowerCase());
        if (set == null || set.isEmpty()) return Collections.emptyList();
        synchronized (set) {
            List<String> list = new ArrayList<>(set);
            Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
            return list;
        }
    }

    /**
     * Filters friends that are currently Online.
     */
    public List<String> getOnlineFriends(String currentUser, List<String> currentOnlineUsers) {
        List<String> all = getFriends(currentUser);
        List<String> online = new ArrayList<>();
        if (currentOnlineUsers == null || currentOnlineUsers.isEmpty()) return online;

        for (String f : all) {
            for (String o : currentOnlineUsers) {
                if (f.equalsIgnoreCase(o)) {
                    online.add(f);
                    break;
                }
            }
        }
        return online;
    }

    /**
     * Filters friends that are currently Offline.
     */
    public List<String> getOfflineFriends(String currentUser, List<String> currentOnlineUsers) {
        List<String> all = getFriends(currentUser);
        List<String> offline = new ArrayList<>();

        for (String f : all) {
            boolean isOnline = false;
            if (currentOnlineUsers != null) {
                for (String o : currentOnlineUsers) {
                    if (f.equalsIgnoreCase(o)) {
                        isOnline = true;
                        break;
                    }
                }
            }
            if (!isOnline) {
                offline.add(f);
            }
        }
        return offline;
    }

    private synchronized void saveFriends() {
        if (storageFile == null) return;
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            StringBuilder sb = new StringBuilder("{\n");
            int userIdx = 0;
            for (Map.Entry<String, Set<String>> entry : userFriends.entrySet()) {
                if (userIdx > 0) sb.append(",\n");
                sb.append("  \"").append(escapeJson(entry.getKey())).append("\": [");
                int fIdx = 0;
                synchronized (entry.getValue()) {
                    for (String friend : entry.getValue()) {
                        if (fIdx > 0) sb.append(", ");
                        sb.append("\"").append(escapeJson(friend)).append("\"");
                        fIdx++;
                    }
                }
                sb.append("]");
                userIdx++;
            }
            sb.append("\n}\n");

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
        } catch (Exception e) {
            System.err.printf("[FRIENDS] Warning: Failed to persist friends: %s%n", e.getMessage());
        }
    }

    private synchronized void loadFriends() {
        if (storageFile == null || !storageFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            parseFriendsJson(sb.toString());
        } catch (Exception e) {
            System.err.printf("[FRIENDS] Warning: Failed to load friends: %s%n", e.getMessage());
        }
    }

    private void parseFriendsJson(String json) {
        if (json == null || json.trim().isEmpty()) return;
        // Simple JSON object parser for key: [strings]
        int pos = 0;
        while ((pos = json.indexOf('"', pos)) != -1) {
            int endKey = json.indexOf('"', pos + 1);
            if (endKey == -1) break;
            String user = json.substring(pos + 1, endKey).trim();
            int colon = json.indexOf(':', endKey);
            if (colon == -1) break;
            int openBracket = json.indexOf('[', colon);
            if (openBracket == -1) break;
            int closeBracket = json.indexOf(']', openBracket);
            if (closeBracket == -1) break;

            String arrStr = json.substring(openBracket + 1, closeBracket);
            Set<String> set = Collections.synchronizedSet(new LinkedHashSet<>());
            int aPos = 0;
            while ((aPos = arrStr.indexOf('"', aPos)) != -1) {
                int aEnd = arrStr.indexOf('"', aPos + 1);
                if (aEnd == -1) break;
                String friend = arrStr.substring(aPos + 1, aEnd).trim();
                if (!friend.isEmpty()) {
                    set.add(friend);
                }
                aPos = aEnd + 1;
            }
            userFriends.put(user.toLowerCase(), set);
            pos = closeBracket + 1;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
