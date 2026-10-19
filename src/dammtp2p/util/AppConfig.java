package dammtp2p.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Manages client persistent configuration stored in 'client_config.json'.
 * Preserves Server ZeroTier IP, control/data/voice ports, download path,
 * and audio settings across application restarts.
 */
public class AppConfig {

    private static final String CONFIG_FILE = "client_config.json";
    private static AppConfig instance;

    private String serverIp = "127.0.0.1";
    private int controlPort = Constants.DEFAULT_CONTROL_PORT;
    private int filePort = Constants.DEFAULT_FILE_TRANSFER_PORT;
    private int voicePort = Constants.DEFAULT_VOICE_PORT;
    private String downloadDir = Constants.DEFAULT_DOWNLOAD_DIR;
    private String lastUsername = "";
    private double voiceVolume = 1.0;
    private boolean voiceMuted = false;
    private boolean voiceDeafened = false;
    private boolean autoAcceptFiles = true;

    private AppConfig() {
        load();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public synchronized void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            save(); // Create default configuration file
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
            String json = sb.toString();

            String ip = extractString(json, "serverIp");
            if (ip != null && !ip.trim().isEmpty()) serverIp = ip.trim();

            int cp = extractInt(json, "controlPort", -1);
            if (cp > 0 && cp <= 65535) controlPort = cp;

            int fp = extractInt(json, "filePort", -1);
            if (fp > 0 && fp <= 65535) filePort = fp;

            int vp = extractInt(json, "voicePort", -1);
            if (vp > 0 && vp <= 65535) voicePort = vp;

            String dl = extractString(json, "downloadDir");
            if (dl != null && !dl.trim().isEmpty()) downloadDir = dl.trim();

            String u = extractString(json, "lastUsername");
            if (u != null) lastUsername = u.trim();

            double vol = extractDouble(json, "voiceVolume", -1.0);
            if (vol >= 0.0 && vol <= 2.0) voiceVolume = vol;

            voiceMuted = extractBoolean(json, "voiceMuted", false);
            voiceDeafened = extractBoolean(json, "voiceDeafened", false);
            autoAcceptFiles = extractBoolean(json, "autoAcceptFiles", true);

        } catch (Exception e) {
            System.err.println("[CONFIG] Error reading " + CONFIG_FILE + ": " + e.getMessage());
        }
    }

    public synchronized void save() {
        File file = new File(CONFIG_FILE);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"serverIp\": \"").append(escapeJson(serverIp)).append("\",\n");
            sb.append("  \"controlPort\": ").append(controlPort).append(",\n");
            sb.append("  \"filePort\": ").append(filePort).append(",\n");
            sb.append("  \"voicePort\": ").append(voicePort).append(",\n");
            sb.append("  \"downloadDir\": \"").append(escapeJson(downloadDir)).append("\",\n");
            sb.append("  \"lastUsername\": \"").append(escapeJson(lastUsername)).append("\",\n");
            sb.append("  \"voiceVolume\": ").append(String.format(java.util.Locale.US, "%.2f", voiceVolume)).append(",\n");
            sb.append("  \"voiceMuted\": ").append(voiceMuted).append(",\n");
            sb.append("  \"voiceDeafened\": ").append(voiceDeafened).append(",\n");
            sb.append("  \"autoAcceptFiles\": ").append(autoAcceptFiles).append("\n");
            sb.append("}\n");
            writer.write(sb.toString());
        } catch (Exception e) {
            System.err.println("[CONFIG] Error saving " + CONFIG_FILE + ": " + e.getMessage());
        }
    }

    // Getters and Setters
    public String getServerIp() { return serverIp; }
    public void setServerIp(String serverIp) { this.serverIp = serverIp; }

    public int getControlPort() { return controlPort; }
    public void setControlPort(int controlPort) { this.controlPort = controlPort; }

    public int getFilePort() { return filePort; }
    public void setFilePort(int filePort) { this.filePort = filePort; }

    public int getVoicePort() { return voicePort; }
    public void setVoicePort(int voicePort) { this.voicePort = voicePort; }

    public String getDownloadDir() { return downloadDir; }
    public void setDownloadDir(String downloadDir) { this.downloadDir = downloadDir; }

    public String getLastUsername() { return lastUsername; }
    public void setLastUsername(String lastUsername) { this.lastUsername = lastUsername; }

    public double getVoiceVolume() { return voiceVolume; }
    public void setVoiceVolume(double voiceVolume) { this.voiceVolume = voiceVolume; }

    public boolean isVoiceMuted() { return voiceMuted; }
    public void setVoiceMuted(boolean voiceMuted) { this.voiceMuted = voiceMuted; }

    public boolean isVoiceDeafened() { return voiceDeafened; }
    public void setVoiceDeafened(boolean voiceDeafened) { this.voiceDeafened = voiceDeafened; }

    public boolean isAutoAcceptFiles() { return autoAcceptFiles; }
    public void setAutoAcceptFiles(boolean autoAcceptFiles) { this.autoAcceptFiles = autoAcceptFiles; }

    // Helpers
    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '\n' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }

    private static int extractInt(String json, String key, int def) {
        String s = extractString(json, key);
        if (s == null) return def;
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return def; }
    }

    private static double extractDouble(String json, String key, double def) {
        String s = extractString(json, key);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static boolean extractBoolean(String json, String key, boolean def) {
        String s = extractString(json, key);
        if (s == null) return def;
        return "true".equalsIgnoreCase(s);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
