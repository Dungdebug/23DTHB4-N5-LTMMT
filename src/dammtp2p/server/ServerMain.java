package dammtp2p.server;

import dammtp2p.server.ui.ServerFrame;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.NetworkUtils;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Independent entry point for the Central TCP Server.
 * <p>
 * Binds to 0.0.0.0 (reachable via ZeroTier IP, LAN IP, or localhost).
 * Handles multiple concurrent clients, displays network diagnostics,
 * and shuts down gracefully.
 * </p>
 */
public class ServerMain {

    public static void main(String[] args) {
        int controlPort = Constants.DEFAULT_CONTROL_PORT;
        int filePort = Constants.DEFAULT_FILE_TRANSFER_PORT;
        boolean forceNoGui = false;
        boolean forceGui = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--port".equalsIgnoreCase(arg) || "-p".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    try {
                        controlPort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Invalid control port: " + args[i]);
                    }
                }
            } else if ("--file-port".equalsIgnoreCase(arg) || "-f".equalsIgnoreCase(arg)) {
                if (i + 1 < args.length) {
                    try {
                        filePort = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Invalid file port: " + args[i]);
                    }
                }
            } else if ("--no-gui".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                forceNoGui = true;
            } else if ("--gui".equalsIgnoreCase(arg) || "-g".equalsIgnoreCase(arg)) {
                forceGui = true;
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                printHelp();
                return;
            }
        }

        boolean isHeadless = GraphicsEnvironment.isHeadless();
        boolean runGui = forceGui || (!forceNoGui && !isHeadless);

        if (runGui) {
            launchGuiServer(controlPort, filePort);
        } else {
            launchConsoleServer(controlPort, filePort);
        }
    }

    private static void printHelp() {
        System.out.println("===============================================================================");
        System.out.println("  " + Constants.APP_NAME + " - CENTRAL SERVER LAUNCHER (Phase 11A)");
        System.out.println("===============================================================================");
        System.out.println("Usage:");
        System.out.println("  java -cp DAMMTP2P.jar dammtp2p.server.ServerMain [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --port, -p <port>         Set TCP control/chat port (default: 8888)");
        System.out.println("  --file-port, -f <port>    Set reserved TCP file data port (default: 8889)");
        System.out.println("  --no-gui, --console, -c   Run in headless console mode");
        System.out.println("  --gui, -g                 Run with Swing Mission Control UI (default if desktop)");
        System.out.println("  --help, -h                Show this help message");
        System.out.println("===============================================================================");
    }

    private static void launchGuiServer(int controlPort, int filePort) {
        SwingUtilities.invokeLater(() -> {
            try {
                ThemeManager.setupTheme();
                ServerCore server = new ServerCore(controlPort, filePort);
                ServerFrame frame = new ServerFrame(server);
                frame.setVisible(true);

                // Auto-start server in background thread so UI doesn't block
                new Thread(() -> {
                    try {
                        server.start();
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to start server: " + e.getMessage());
                    }
                }, "server-starter").start();

                // Graceful JVM shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (server.isRunning()) {
                        System.out.println("[SERVER] JVM shutting down. Stopping ServerCore gracefully...");
                        server.stop();
                    }
                }, "server-shutdown-hook"));

            } catch (Exception e) {
                System.err.println("[ERROR] Failed to initialize Server GUI: " + e.getMessage());
                launchConsoleServer(controlPort, filePort);
            }
        });
    }

    private static void launchConsoleServer(int controlPort, int filePort) {
        printBanner();
        printDiagnostics(controlPort, filePort);

        ServerCore server = new ServerCore(controlPort, filePort);
        AtomicBoolean running = new AtomicBoolean(true);

        // Attach event listener for console logging
        server.addListener(new ServerCore.ServerEventListener() {
            @Override
            public void onEvent(String message) {
                System.out.println("[EVENT] " + message);
            }

            @Override
            public void onStateChanged(boolean running) {
                System.out.printf("[STATUS] Server running state: %s (Listening on 0.0.0.0:%d, File port: %d)%n",
                        running ? "ACTIVE" : "STOPPED", controlPort, filePort);
            }

            @Override
            public void onUsersChanged(int count) {
                System.out.printf("[USERS] Active authenticated users count: %d%n", count);
            }

            @Override
            public void onTransfersChanged(int count) {
                System.out.printf("[TRANSFERS] Active file transfers count: %d%n", count);
            }
        });

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            if (server.isRunning()) {
                System.out.println("\n[SHUTDOWN] Stopping server...");
                server.stop();
            }
        }, "console-shutdown-hook"));

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to start server: " + e.getMessage());
            return;
        }

        System.out.println();
        System.out.println("Commands available: [status] [clients] [clear] [quit]");
        System.out.println("Type 'quit' or press Ctrl+C to terminate the server.");
        System.out.println("-------------------------------------------------------------------------------");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running.get() && server.isRunning()) {
                System.out.print("server> ");
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim().toLowerCase();
                if ("quit".equals(line) || "exit".equals(line) || "q".equals(line)) {
                    System.out.println("[SERVER] Stopping server...");
                    server.stop();
                    break;
                } else if ("status".equals(line) || "s".equals(line)) {
                    printStatus(server, controlPort, filePort);
                } else if ("clients".equals(line) || "c".equals(line)) {
                    printClients(server);
                } else if ("help".equals(line) || "h".equals(line) || "?".equals(line)) {
                    System.out.println("Commands:");
                    System.out.println("  status   - Show server running state, ports, and connected clients");
                    System.out.println("  clients  - List all currently connected clients and sessions");
                    System.out.println("  quit     - Gracefully stop server and exit");
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    private static void printBanner() {
        System.out.println("===============================================================================");
        System.out.println("   ____      _    __  __ __  __ _____ ____  ____  ____    ____  ");
        System.out.println("  |  _ \\    / \\  |  \\/  |  \\/  |_   _|  _ \\|___ \\|  _ \\  / ___| ");
        System.out.println("  | | | |  / _ \\ | |\\/| | |\\/| | | | | |_) | __) | |_) | \\___ \\ ");
        System.out.println("  | |_| | / ___ \\| |  | | |  | | | | |  __/ / __/|  __/   ___) |");
        System.out.println("  |____/ /_/   \\_\\_|  |_|_|  |_| |_| |_|   |_____|_|     |____/ ");
        System.out.println("        CENTRAL TCP SERVER - MULTI-CLIENT ARCHITECTURE (PHASE 11A)        ");
        System.out.println("===============================================================================");
    }

    private static void printDiagnostics(int controlPort, int filePort) {
        System.out.println("[DIAGNOSTICS] Network Interface & ZeroTier Detection:");

        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        if (zt.isPresent()) {
            System.out.printf("  -> [ZeroTier Detected] IP: %s (Adapter: %s)%n",
                    zt.get().getIpAddress(), zt.get().getDisplayName());
        } else {
            System.out.println("  -> [ZeroTier] No dedicated ZeroTier virtual adapter identified.");
        }

        List<NetworkUtils.NetworkCandidate> candidates = NetworkUtils.getCandidateAddresses();
        System.out.println("  -> Candidate Local IPv4 Addresses for Clients to connect:");
        if (candidates.isEmpty()) {
            System.out.println("     - 127.0.0.1 (Localhost loopback only)");
        } else {
            for (NetworkUtils.NetworkCandidate c : candidates) {
                System.out.printf("     - %-15s [%s] %s%n",
                        c.getIpAddress(),
                        c.getInterfaceName(),
                        c.isZeroTierLikely() ? "(ZeroTier)" : "(LAN)");
            }
        }

        System.out.println("  -> Control / Chat Port : TCP " + controlPort);
        System.out.println("  -> Reserved File Port  : TCP " + filePort);
        System.out.println("  -> Bind Address        : 0.0.0.0 (All interfaces)");
        System.out.println("===============================================================================");
    }

    private static void printStatus(ServerCore server, int controlPort, int filePort) {
        System.out.println("--- Server Status ---");
        System.out.println("State              : " + (server.isRunning() ? "RUNNING (Active)" : "STOPPED"));
        System.out.println("Control Port (TCP) : " + controlPort);
        System.out.println("File Port (TCP)    : " + filePort);
        System.out.println("Active Sessions    : " + server.getSessionManager().getTotalSessionCount());
        System.out.println("Authenticated Users: " + server.getSessionManager().getOnlineUserCount());
        System.out.println("---------------------");
    }

    private static void printClients(ServerCore server) {
        List<ClientSession> sessions = server.getSessionManager().getAllSessions();
        System.out.printf("--- Connected Clients (%d) ---%n", sessions.size());
        if (sessions.isEmpty()) {
            System.out.println("No active clients connected.");
        } else {
            int idx = 1;
            for (ClientSession session : sessions) {
                System.out.printf("  %d. %s [Handshake: %s, Auth: %s]%n",
                        idx++,
                        session.getClientDesc(),
                        session.isHandshakeCompleted() ? "OK" : "PENDING",
                        session.isAuthenticated() ? ("User: " + session.getUsername()) : "ANONYMOUS");
            }
        }
        System.out.println("-----------------------------");
    }
}
