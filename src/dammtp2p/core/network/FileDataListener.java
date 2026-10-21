package dammtp2p.core.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listener for the dedicated binary file data channel (Port 8889).
 *
 * <p>Phase 2 prepares and opens this port to verify firewall clearance and port readiness,
 * while binary streaming and chunking will be implemented in Phase 4.
 */
public class FileDataListener {

    private ServerSocket serverSocket;
    private int boundPort = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;

    private volatile java.util.function.Consumer<Socket> connectionHandler;

    public FileDataListener(ExecutorService executor) {
        this.executor = executor;
    }

    public void setConnectionHandler(java.util.function.Consumer<Socket> handler) {
        this.connectionHandler = handler;
    }

    public synchronized void startListening(int port) throws IOException {
        if (running.get()) {
            return;
        }

        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress("0.0.0.0", port), 10);
        this.boundPort = serverSocket.getLocalPort();
        this.running.set(true);

        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket dataSocket = serverSocket.accept();
                java.util.function.Consumer<Socket> handler = connectionHandler;
                if (handler != null) {
                    handler.accept(dataSocket);
                } else {
                    try {
                        dataSocket.close();
                    } catch (IOException ignored) {}
                }
            } catch (SocketException e) {
                if (!running.get()) break;
            } catch (IOException ignored) {
                if (!running.get()) break;
            }
        }
    }

    public synchronized void stopListening() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        boundPort = -1;
    }

    public boolean isListening() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getBoundPort() {
        return boundPort;
    }
}
