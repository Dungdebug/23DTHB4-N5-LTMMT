package dammtp2p.server;

import dammtp2p.security.CryptoUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authentication service managing user registration, credential validation,
 * and secure persistent storage using PBKDF2-HMAC-SHA256 password hashing.
 *
 * <p>Never stores or logs plaintext passwords. Preloads standard testing accounts:
 * <ul>
 *   <li>alice / password123</li>
 *   <li>bob / password123</li>
 *   <li>charlie / password123</li>
 *   <li>admin / admin123</li>
 * </ul>
 */
public class AuthService {

    public static class AccountRecord {
        private final String username;
        private final byte[] salt;
        private final byte[] passwordHash;

        public AccountRecord(String username, byte[] salt, byte[] passwordHash) {
            this.username = Objects.requireNonNull(username, "username must not be null");
            this.salt = Objects.requireNonNull(salt, "salt must not be null");
            this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        }

        public String getUsername() {
            return username;
        }

        public byte[] getSalt() {
            return salt;
        }

        public byte[] getPasswordHash() {
            return passwordHash;
        }
    }

    private final Map<String, AccountRecord> accounts = new ConcurrentHashMap<>();
    private final File storageFile;

    public AuthService(File storageFile) {
        this.storageFile = storageFile;
        loadAccounts();
        ensureDefaultAccounts();
    }

    public AuthService() {
        this(new File("server_data", "accounts.json"));
    }

    private void ensureDefaultAccounts() {
        String[][] defaults = {
            {"alice", "password123"},
            {"bob", "password123"},
            {"charlie", "password123"},
            {"admin", "admin123"}
        };
        boolean added = false;
        for (String[] def : defaults) {
            String user = def[0];
            if (!accounts.containsKey(user.toLowerCase())) {
                byte[] salt = CryptoUtils.generateSalt();
                byte[] hash = CryptoUtils.hashPassword(def[1].toCharArray(), salt);
                accounts.put(user.toLowerCase(), new AccountRecord(user, salt, hash));
                added = true;
            }
        }
        if (added) {
            saveAccounts();
        }
    }

    public synchronized boolean register(String username, char[] password) throws IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        String cleanUser = username.trim();
        if (accounts.containsKey(cleanUser.toLowerCase())) {
            return false; // User already exists
        }

        byte[] salt = CryptoUtils.generateSalt();
        byte[] hash = CryptoUtils.hashPassword(password, salt);
        accounts.put(cleanUser.toLowerCase(), new AccountRecord(cleanUser, salt, hash));
        saveAccounts();
        return true;
    }

    public boolean authenticate(String username, char[] password) {
        if (username == null || password == null) {
            return false;
        }
        AccountRecord record = accounts.get(username.trim().toLowerCase());
        if (record == null) {
            return false;
        }
        return CryptoUtils.verifyPassword(password, record.getSalt(), record.getPasswordHash());
    }

    public boolean userExists(String username) {
        return username != null && accounts.containsKey(username.trim().toLowerCase());
    }

    private synchronized void saveAccounts() {
        if (storageFile == null) return;
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            StringBuilder sb = new StringBuilder("[\n");
            int count = 0;
            for (AccountRecord record : accounts.values()) {
                if (count > 0) sb.append(",\n");
                sb.append("  {\n");
                sb.append("    \"username\": \"").append(escapeJson(record.getUsername())).append("\",\n");
                sb.append("    \"salt\": \"").append(CryptoUtils.bytesToHex(record.getSalt())).append("\",\n");
                sb.append("    \"hash\": \"").append(CryptoUtils.bytesToHex(record.getPasswordHash())).append("\"\n");
                sb.append("  }");
                count++;
            }
            sb.append("\n]");

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            System.err.printf("[AUTH] Warning: Failed to persist accounts to %s: %s%n",
                    storageFile.getPath(), e.getMessage());
        }
    }

    private synchronized void loadAccounts() {
        if (storageFile == null || !storageFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            parseAccountsJson(sb.toString());
        } catch (Exception e) {
            System.err.printf("[AUTH] Warning: Failed to load accounts from %s: %s%n",
                    storageFile.getPath(), e.getMessage());
        }
    }

    private void parseAccountsJson(String json) {
        int pos = 0;
        while ((pos = json.indexOf('{', pos)) != -1) {
            int end = json.indexOf('}', pos);
            if (end == -1) break;
            String obj = json.substring(pos + 1, end);
            String user = extractJsonValue(obj, "username");
            String saltHex = extractJsonValue(obj, "salt");
            String hashHex = extractJsonValue(obj, "hash");

            if (user != null && saltHex != null && hashHex != null) {
                try {
                    byte[] salt = CryptoUtils.hexToBytes(saltHex);
                    byte[] hash = CryptoUtils.hexToBytes(hashHex);
                    accounts.put(user.toLowerCase(), new AccountRecord(user, salt, hash));
                } catch (Exception ignored) {}
            }
            pos = end + 1;
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '\r' && json.charAt(end) != '\n') {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
