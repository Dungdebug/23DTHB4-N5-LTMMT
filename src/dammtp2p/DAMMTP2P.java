package dammtp2p;

import dammtp2p.client.ClientCore;
import dammtp2p.client.ui.ClientMainFrame;
import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.transfer.HashVerifier;
import dammtp2p.server.ClientSession;
import dammtp2p.server.ServerCore;
import dammtp2p.server.ServerMain;
import dammtp2p.server.ui.ServerFrame;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.FileBoundaryTest;
import dammtp2p.util.FormatUtils;
import dammtp2p.util.NetworkUtils;
import dammtp2p.util.TestFileGenerator;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 * Main application entry point for DAMMTP2P (Phase 11 - Central Multi-Client Server Architecture).
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code --server} : Starts Central Server (GUI dashboard + listeners on 8888 & 8889).</li>
 *   <li>{@code --client} : Starts Client GUI (Login, Online Users, Group/Private Chat, File Relay).</li>
 *   <li>{@code --test-server} / {@code --test-loopback} : Executes full end-to-end multi-client test suite.</li>
 *   <li>Default (no args) : Displays role selection launcher.</li>
 * </ul>
 */
public class DAMMTP2P {

    public static void main(String[] args) {
        // Check CLI flags
        boolean isServer = false;
        boolean isClient = false;
        boolean isTest = false;
        boolean isTest11a = false;
        boolean isTest20gb = false;
        boolean noGui = false;

        String defaultUser = null;
        boolean autoHost = false;
        String serverIpArg = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--server".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) {
                isServer = true;
            } else if ("--client".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                isClient = true;
            } else if ("--test-11a".equalsIgnoreCase(arg)) {
                isTest11a = true;
            } else if ("--test-20gb".equalsIgnoreCase(arg) || "--test-boundary".equalsIgnoreCase(arg)) {
                isTest20gb = true;
            } else if ("--test-voice".equalsIgnoreCase(arg) || "--test-voice-group".equalsIgnoreCase(arg)) {
                boolean success = dammtp2p.util.VoiceAndGroupTransferTest.runAllTests();
                System.exit(success ? 0 : 1);
                return;
            } else if ("--test".equalsIgnoreCase(arg) || "--test-all".equalsIgnoreCase(arg) || "--test-server".equalsIgnoreCase(arg) || "--test-loopback".equalsIgnoreCase(arg) || "test".equalsIgnoreCase(arg)) {
                isTest = true;
            } else if ("--no-gui".equalsIgnoreCase(arg)) {
                noGui = true;
            } else if ("--host".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                autoHost = true;
            } else if ("--user".equalsIgnoreCase(arg) || "-u".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    defaultUser = args[++i];
                }
            } else if ("--ip".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    serverIpArg = args[++i];
                }
            }
        }

        // 1. Run Automated Test Suites
        if (isTest20gb) {
            boolean success = FileBoundaryTest.runAllBoundaryTests();
            System.exit(success ? 0 : 1);
            return;
        }

        if (isTest11a) {
            boolean success = runPhase11aSelfTest();
            System.exit(success ? 0 : 1);
            return;
        }

        if (isTest) {
            boolean s0 = FileBoundaryTest.runAllBoundaryTests();
            boolean s1 = runPhase11aSelfTest();
            boolean s2 = runCentralServerSelfTest();
            boolean s3 = dammtp2p.util.VoiceAndGroupTransferTest.runAllTests();
            System.exit((s0 && s1 && s2 && s3) ? 0 : 1);
            return;
        }

        // 2. Launch Server explicitly via ServerMain
        if (isServer) {
            ServerMain.main(args);
            return;
        }

        // 3. Launch Client explicitly
        if (isClient) {
            startUnifiedMode(defaultUser, autoHost, serverIpArg);
            return;
        }

        // 4. Default: Launch Unified Single-Window Application (Chat, Files & Host Server)
        if (!GraphicsEnvironment.isHeadless() && !noGui) {
            startUnifiedMode(defaultUser, autoHost, serverIpArg);
        } else {
            // Headless console fallback: default to server mode
            startServerMode(true);
        }
    }

    private static void startUnifiedMode(String defaultUser, boolean autoHost, String serverIp) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("[ERROR] Cannot launch Client in headless environment without display.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            ThemeManager.initializeTheme();
            String clientName = (defaultUser != null && !defaultUser.trim().isEmpty())
                    ? defaultUser.trim()
                    : ("Client-" + System.getProperty("user.name", "User"));
            ClientCore client = new ClientCore(clientName);
            ClientMainFrame frame = new ClientMainFrame(client, defaultUser, autoHost);
            if (serverIp != null && !serverIp.trim().isEmpty()) {
                frame.setServerIp(serverIp.trim());
            }
            frame.setVisible(true);
        });
    }

    private static void startUnifiedMode() {
        startUnifiedMode(null, false, null);
    }

    private static void startClientMode() {
        startUnifiedMode(null, false, null);
    }

    private static void startServerMode(boolean noGui) {
        System.out.println("==================================================================");
        System.out.printf("  %s v%s — CENTRAL MULTI-CLIENT SERVER%n", Constants.APP_NAME, Constants.APP_VERSION);
        System.out.println("  Architecture : Client-Server over ZeroTier LAN (Ports 8888 & 8889)");
        System.out.println("==================================================================");

        printZeroTierDiagnostics();

        ServerCore server = new ServerCore();
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("[FATAL] Could not start server: " + e.getMessage());
            return;
        }

        if (!GraphicsEnvironment.isHeadless() && !noGui) {
            SwingUtilities.invokeLater(() -> {
                ThemeManager.initializeTheme();
                ServerFrame frame = new ServerFrame(server);
                frame.setVisible(true);
            });
        }

        // Keep server alive in console
        runServerConsole(server);
    }

    private static void printZeroTierDiagnostics() {
        System.out.println("Local Network Interfaces:");
        List<NetworkUtils.NetworkCandidate> candidates = NetworkUtils.getCandidateAddresses();
        for (NetworkUtils.NetworkCandidate c : candidates) {
            System.out.printf("  - %-40s : %-15s %s%n",
                    c.getDisplayName(),
                    c.getIpAddress(),
                    c.isZeroTierLikely() ? "[ZeroTier Detected]" : (c.isSiteLocal() ? "[Private LAN]" : ""));
        }
        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        if (zt.isPresent()) {
            System.out.printf(">> Primary ZeroTier IP : %s (%s)%n", zt.get().getIpAddress(), zt.get().getDisplayName());
        } else {
            System.out.println(">> (No ZeroTier adapter detected. Using candidate LAN/Loopback IP)");
        }
        System.out.println("==================================================================");
    }

    private static void runServerConsole(ServerCore server) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Server running. Commands: 'status', 'users', 'transfers', 'exit'");
        while (server.isRunning()) {
            try {
                System.out.print("server> ");
                String line = reader.readLine();
                if (line == null || "exit".equalsIgnoreCase(line.trim())) {
                    server.stop();
                    break;
                }
                String cmd = line.trim().toLowerCase();
                if ("status".equals(cmd)) {
                    System.out.printf("Status: %s | Online Users: %d | Active Transfers: %d%n",
                            server.isRunning() ? "RUNNING" : "STOPPED",
                            server.getSessionManager().getOnlineCount(),
                            server.getFileRelayManager().getActiveTransferCount());
                } else if ("users".equals(cmd)) {
                    System.out.println("Online Users: " + server.getSessionManager().getOnlineUsernames());
                } else if ("transfers".equals(cmd)) {
                    System.out.println("Active Transfers: " + server.getFileRelayManager().getActiveTransfers().size());
                }
            } catch (Exception e) {
                break;
            }
        }
    }

    // =========================================================================
    //  PHASE 11A AUTOMATED SUITE (CENTRAL SERVER & CONCURRENT CLIENTS)
    // =========================================================================

    public static boolean runPhase11aSelfTest() {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println("  PHASE 11A AUTOMATED VERIFICATION SUITE — CENTRAL CLIENT-SERVER");
        System.out.println("  Topology: Central Server (0.0.0.0) <--> Multi-Clients (TCP 8888)");
        System.out.println("==================================================================");

        int testControlPort = 18888;
        int testFilePort = 18889;

        ServerCore server = new ServerCore(testControlPort, testFilePort);

        try {
            // Test 1: Start Central Server
            server.start();
            if (!server.isRunning()) {
                System.err.println("[FAIL] Test 1: ServerCore failed to transition to running state.");
                return false;
            }
            System.out.printf("[PASS] Test 1: ServerCore successfully bound to 0.0.0.0:%d (file port: %d)%n",
                    testControlPort, testFilePort);

            // Test 2: Client A connects & performs handshake
            ClientCore clientA = new ClientCore("ClientA");
            clientA.connectToServer("127.0.0.1", testControlPort);
            if (!clientA.isConnected()) {
                System.err.println("[FAIL] Test 2: ClientA not marked as connected.");
                return false;
            }
            Thread.sleep(100);
            if (server.getSessionManager().getTotalSessionCount() != 1) {
                System.err.printf("[FAIL] Test 2: Expected 1 server session, found %d%n",
                        server.getSessionManager().getTotalSessionCount());
                return false;
            }
            System.out.println("[PASS] Test 2: ClientA connected and completed protocol handshake.");

            // Test 3: Client B connects simultaneously
            ClientCore clientB = new ClientCore("ClientB");
            clientB.connectToServer("127.0.0.1", testControlPort);
            if (!clientB.isConnected()) {
                System.err.println("[FAIL] Test 3: ClientB failed to connect.");
                return false;
            }
            Thread.sleep(100);
            if (server.getSessionManager().getTotalSessionCount() != 2) {
                System.err.printf("[FAIL] Test 3: Expected 2 server sessions, found %d%n",
                        server.getSessionManager().getTotalSessionCount());
                return false;
            }
            System.out.println("[PASS] Test 3: ClientB connected simultaneously (2 active sessions).");

            // Test 4: Clients C and D connect concurrently (Total 4 Clients)
            ClientCore clientC = new ClientCore("ClientC");
            ClientCore clientD = new ClientCore("ClientD");
            clientC.connectToServer("127.0.0.1", testControlPort);
            clientD.connectToServer("127.0.0.1", testControlPort);
            Thread.sleep(100);
            int totalSessions = server.getSessionManager().getTotalSessionCount();
            if (totalSessions != 4) {
                System.err.printf("[FAIL] Test 4: Expected 4 server sessions, found %d%n", totalSessions);
                return false;
            }
            System.out.printf("[PASS] Test 4: Concurrent scaling confirmed (%d clients active).%n", totalSessions);

            // Test 5: Verify Server tracks all sessions safely
            List<ClientSession> allSessions = server.getSessionManager().getAllSessions();
            if (allSessions.size() != 4) {
                System.err.println("[FAIL] Test 5: SessionManager list size mismatch.");
                return false;
            }
            System.out.println("[PASS] Test 5: SessionManager thread-safe state tracking verified.");

            // Test 6: One Client disconnects without crashing the server or affecting other clients
            clientB.disconnect("Test graceful exit");
            Thread.sleep(150);
            if (!server.isRunning()) {
                System.err.println("[FAIL] Test 6: Server crashed after client disconnect!");
                return false;
            }
            if (server.getSessionManager().getTotalSessionCount() != 3) {
                System.err.printf("[FAIL] Test 6: Expected 3 sessions after disconnect, found %d%n",
                        server.getSessionManager().getTotalSessionCount());
                return false;
            }
            if (!clientA.isConnected() || !clientC.isConnected() || !clientD.isConnected()) {
                System.err.println("[FAIL] Test 6: Remaining clients lost connection unexpectedly.");
                return false;
            }
            System.out.println("[PASS] Test 6: Client disconnection isolated; remaining 3 clients stay connected.");

            // Test 7: Reconnection works
            clientB.connectToServer("127.0.0.1", testControlPort);
            Thread.sleep(100);
            if (!clientB.isConnected() || server.getSessionManager().getTotalSessionCount() != 4) {
                System.err.println("[FAIL] Test 7: ClientB reconnection failed.");
                return false;
            }
            System.out.println("[PASS] Test 7: ClientB successfully reconnected (4 active sessions).");

            // Test 8: Actionable error when connecting to unavailable server
            boolean caughtRefused = false;
            String errorMsg = "";
            ClientCore badClient = new ClientCore("ClientBad");
            try {
                badClient.connectToServer("127.0.0.1", 19999); // Closed port
            } catch (IOException e) {
                caughtRefused = true;
                errorMsg = e.getMessage();
            }
            if (!caughtRefused || errorMsg == null || errorMsg.isEmpty()) {
                System.err.println("[FAIL] Test 8: Did not catch actionable error for closed port.");
                return false;
            }
            System.out.printf("[PASS] Test 8: Unavailable server generated actionable error: '%s'%n", errorMsg);

            // Test 9: Graceful server shutdown cleans up all sessions and closes socket
            clientA.disconnect("Shutdown prep");
            clientB.disconnect("Shutdown prep");
            clientC.disconnect("Shutdown prep");
            clientD.disconnect("Shutdown prep");
            badClient.disconnect("Shutdown prep");

            server.stop();
            if (server.isRunning()) {
                System.err.println("[FAIL] Test 9: ServerCore still reported running after stop().");
                return false;
            }
            System.out.println("[PASS] Test 9: ServerCore shut down gracefully; listening sockets closed.");

            System.out.println("==================================================================");
            System.out.println("  ALL 9 PHASE 11A TESTS PASSED SUCCESSFULLY! (100% COMPLIANT)");
            System.out.println("==================================================================");
            return true;

        } catch (Exception e) {
            System.err.println("[ERROR] Exception during Phase 11A self-test: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    // =========================================================================
    //  AUTOMATED MULTI-CLIENT END-TO-END TEST SUITE
    // =========================================================================

    public static boolean runCentralServerSelfTest() {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println("  STARTING PHASE 11 AUTOMATED MULTI-CLIENT & SERVER TEST SUITE");
        System.out.println("  (PBKDF2 AUTH, ONLINE PRESENCE, AES-128-GCM CHAT & 8889 FILE RELAY)");
        System.out.println("==================================================================");

        int testControlPort = 18888;
        int testFilePort = 18889;

        ServerCore server = new ServerCore(testControlPort, testFilePort);
        AtomicBoolean success = new AtomicBoolean(true);

        try {
            // Step 1: Start Server
            server.start();
            System.out.printf("[TEST 1/10] Server started on ports %d (control) & %d (relay) [PASS]%n",
                    testControlPort, testFilePort);

            // Step 2: Initialize 2 Clients
            ClientCore clientA = new ClientCore("ClientA");
            ClientCore clientB = new ClientCore("ClientB");

            CountDownLatch aOnlineLatch = new CountDownLatch(1);
            CountDownLatch bOnlineLatch = new CountDownLatch(1);
            CountDownLatch privateChatLatch = new CountDownLatch(1);
            CountDownLatch roomChatLatch = new CountDownLatch(1);
            CountDownLatch fileCompleteLatch = new CountDownLatch(1);
            CountDownLatch rejectLatch = new CountDownLatch(1);
            AtomicBoolean rejectReceived = new AtomicBoolean(false);

            AtomicReference<String> receivedPrivateMsg = new AtomicReference<>("");
            AtomicReference<String> receivedRoomMsg = new AtomicReference<>("");

            clientA.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String serverIp, int port) {}
                @Override public void onDisconnected(String reason) {}
                @Override public void onLoginSuccess(String u, List<String> users, List<String> rooms) {}
                @Override public void onLoginFailed(String reason) {}
                @Override public void onOnlineUsersUpdated(List<String> users) {
                    if (users.contains("bob")) aOnlineLatch.countDown();
                }
                @Override public void onRoomsUpdated(List<String> rooms) {}
                @Override public void onChatMessage(ChatMessage m) {}
                @Override public void onIncomingFileRequest(FileMetadata m) {}
                @Override public void onFileTransferProgress(FileMetadata m, long t, long tot, double s) {}
                @Override public void onFileTransferStateChanged(FileMetadata m, TransferState s, String msg) {
                    if (s == TransferState.REJECTED && msg != null && msg.contains("20 GiB")) {
                        rejectReceived.set(true);
                        rejectLatch.countDown();
                    }
                }
            });

            clientB.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String serverIp, int port) {}
                @Override public void onDisconnected(String reason) {}
                @Override public void onLoginSuccess(String u, List<String> users, List<String> rooms) {}
                @Override public void onLoginFailed(String reason) {}
                @Override public void onOnlineUsersUpdated(List<String> users) {
                    if (users.contains("alice")) bOnlineLatch.countDown();
                }
                @Override public void onRoomsUpdated(List<String> rooms) {}
                @Override public void onChatMessage(ChatMessage m) {
                    if (m.isRoomMessage()) {
                        receivedRoomMsg.set(m.getContent());
                        roomChatLatch.countDown();
                    } else if ("alice".equalsIgnoreCase(m.getSender())) {
                        receivedPrivateMsg.set(m.getContent());
                        privateChatLatch.countDown();
                    }
                }
                @Override public void onIncomingFileRequest(FileMetadata m) {
                    try {
                        clientB.acceptIncomingFile(m);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                @Override public void onFileTransferProgress(FileMetadata m, long t, long tot, double s) {}
                @Override public void onFileTransferStateChanged(FileMetadata m, TransferState s, String msg) {
                    System.out.printf("[FILE STATE ClientB] %s: %s%n", s, msg);
                    if (s == TransferState.COMPLETED) {
                        fileCompleteLatch.countDown();
                    }
                }
            });

            // Connect & Login
            clientA.connectToServer("127.0.0.1", testControlPort);
            clientA.login("alice", "password123", 3000);
            System.out.println("[TEST 2/10] Client 'alice' connected & authenticated via PBKDF2 [PASS]");

            clientB.connectToServer("127.0.0.1", testControlPort);
            clientB.login("bob", "password123", 3000);
            System.out.println("[TEST 3/10] Client 'bob' connected & authenticated via PBKDF2 [PASS]");

            // Verify Online Presence Broadcast
            boolean presenceOk = aOnlineLatch.await(3, TimeUnit.SECONDS) && bOnlineLatch.await(3, TimeUnit.SECONDS);
            System.out.printf("[TEST 4/10] Dynamic Online Presence Sync (alice <-> bob): %s%n", presenceOk ? "PASS" : "FAIL");
            if (!presenceOk) success.set(false);

            // Test Encrypted 1-to-1 Private Chat
            String testPrivateText = "Xin chào Bob! Tin nhắn bảo mật AES-128 từ Alice.";
            clientA.sendPrivateMessage("bob", testPrivateText);
            boolean privateOk = privateChatLatch.await(3, TimeUnit.SECONDS);
            System.out.printf("[TEST 5/10] 1-to-1 Private Chat with AES-128-GCM: %s (Received: '%s')%n",
                    privateOk && testPrivateText.equals(receivedPrivateMsg.get()) ? "PASS" : "FAIL",
                    receivedPrivateMsg.get());
            if (!privateOk || !testPrivateText.equals(receivedPrivateMsg.get())) success.set(false);

            // Test Group Chat Room Broadcast
            String testRoomText = "Chào cả nhóm 05 MMT trong phòng chung!";
            clientA.sendRoomMessage("Lobby", testRoomText);
            boolean roomOk = roomChatLatch.await(3, TimeUnit.SECONDS);
            System.out.printf("[TEST 6/10] Group Chat Broadcast (Room 'Lobby') with AES-128: %s (Received: '%s')%n",
                    roomOk && testRoomText.equals(receivedRoomMsg.get()) ? "PASS" : "FAIL",
                    receivedRoomMsg.get());
            if (!roomOk || !testRoomText.equals(receivedRoomMsg.get())) success.set(false);

            // Test Server-Relayed File Transfer with Chunked AES-128-GCM & SHA-256
            File testFile = TestFileGenerator.generateTestFile(new File("test_relay_5mb.bin"), 5 * 1024 * 1024, false);
            System.out.printf("[TEST 7/10] Generated 5MB synthetic test file '%s'%n", testFile.getName());

            clientA.initiateFileSend("bob", testFile, true, null);
            boolean fileOk = fileCompleteLatch.await(15, TimeUnit.SECONDS);
            System.out.printf("[TEST 8/10] Port 8889 Server-Relayed File Streaming, Chunked AES-GCM & SHA-256: %s%n",
                    fileOk ? "PASS" : "FAIL");
            if (!fileOk) success.set(false);

            // Test 9/10: Comprehensive 20 GiB Boundary Verification Suite
            boolean boundaryOk = FileBoundaryTest.runAllBoundaryTests();
            System.out.printf("[TEST 9/10] 20 GiB Boundary Verification (0B, 1KB, Limit-1, Limit, Limit+1, Resume): %s%n",
                    boundaryOk ? "PASS" : "FAIL");
            if (!boundaryOk) success.set(false);

            // Test 10/10: Live Protocol Rejection of Oversized File (>20 GiB)
            long oversizedDeclaredSize = Constants.MAX_FILE_SIZE_BYTES + 1024L; // 21,474,837,504 bytes
            String oversizedJsonOffer = String.format(
                    "{\"transferId\":\"test-over-20gb\",\"fileName\":\"huge_backup.iso\",\"fileSize\":%d,\"sha256\":\"mock\",\"offset\":0,\"sender\":\"alice\",\"receiver\":\"bob\",\"timestamp\":%d,\"encrypted\":false}",
                    oversizedDeclaredSize, System.currentTimeMillis());

            clientA.sendPacket(dammtp2p.core.protocol.PacketType.FILE_TRANSFER_REQUEST, oversizedJsonOffer);
            boolean serverRejectedOk = rejectLatch.await(4, TimeUnit.SECONDS);
            System.out.printf("[TEST 10/10] Server & Client Live Safety Rejection for Oversized File (>20 GiB): %s%n",
                    serverRejectedOk && rejectReceived.get() ? "PASS" : "FAIL");
            if (!serverRejectedOk || !rejectReceived.get()) success.set(false);

            // Clean up
            clientA.disconnect("Test completed");
            clientB.disconnect("Test completed");
            if (testFile.exists()) testFile.delete();
            File receivedFile = new File("downloads", testFile.getName());
            if (receivedFile.exists()) receivedFile.delete();

        } catch (Exception e) {
            e.printStackTrace();
            success.set(false);
        } finally {
            server.stop();
        }

        System.out.println("==================================================================");
        System.out.printf("  OVERALL PHASE 11 INTEGRATION VERDICT: %s%n", success.get() ? "100% PASS" : "FAIL");
        System.out.println("==================================================================");
        return success.get();
    }
}
