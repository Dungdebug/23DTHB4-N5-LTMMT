package dammtp2p.core.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implementation of {@link PeerListener} that opens a ServerSocket on the control port
 * and listens for incoming direct P2P connections.
 *
 * <p>Why bind to {@code 0.0.0.0}?
 * <ul>
 *   <li><b>ZeroTier Compatibility:</b> Binding to the wildcard address ({@code 0.0.0.0}) enables
 *       the ServerSocket to receive packets across <i>all</i> active host network interfaces,
 *       including the ZeroTier Virtual LAN adapter ({@code 10.x.x.x}), physical adapters
 *       (Ethernet/Wi-Fi), and the loopback adapter ({@code 127.0.0.1}).</li>
 *   <li><b>Loopback & Local Testing:</b> Allows running local instances or loopback tests without
 *       reconfiguring network bindings.</li>
 *   <li><b>Dynamic Resilience:</b> If the ZeroTier adapter disconnects or acquires a new virtual IP,
 *       the listener remains active without needing a restart.</li>
 * </ul>
 */
public class DefaultPeerListener implements PeerListener {

    private final Consumer<Socket> incomingSocketConsumer;
    private final Consumer<Throwable> errorConsumer;
    private final ExecutorService executor;

    private ServerSocket serverSocket;
    private int boundPort = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DefaultPeerListener(Consumer<Socket> incomingSocketConsumer,
                               Consumer<Throwable> errorConsumer,
                               ExecutorService executor) {
        this.incomingSocketConsumer = Objects.requireNonNull(incomingSocketConsumer, "incomingSocketConsumer must not be null");
        this.errorConsumer = Objects.requireNonNull(errorConsumer, "errorConsumer must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public synchronized void startListening(int port) throws IOException {
        if (running.get()) {
            return;
        }

        // Bind to 0.0.0.0 (wildcard) with backlog of 50
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress("0.0.0.0", port), 50);
        this.boundPort = serverSocket.getLocalPort();
        this.running.set(true);

        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Hand off incoming socket immediately to the executor so listener loop is not blocked
                executor.submit(() -> incomingSocketConsumer.accept(clientSocket));
            } catch (SocketException e) {
                // Thrown normally when serverSocket.close() is called during stopListening()
                if (!running.get()) {
                    break;
                }
                errorConsumer.accept(e);
            } catch (IOException e) {
                if (running.get()) {
                    errorConsumer.accept(e);
                }
            }
        }
    }

    @Override
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

    @Override
    public boolean isListening() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    @Override
    public int getBoundPort() {
        return boundPort;
    }
}
