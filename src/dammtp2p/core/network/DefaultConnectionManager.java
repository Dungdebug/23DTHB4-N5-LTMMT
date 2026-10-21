package dammtp2p.core.network;

import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.protocol.PacketHeader;
import dammtp2p.util.Constants;
import dammtp2p.util.NetworkUtils;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe coordinator for the direct P2P networking layer.
 * Manages the dual roles (concurrent PeerListener and PeerConnector),
 * tracks connection state transitions, executes handshakes, and dispatches events.
 */
public class DefaultConnectionManager implements ConnectionManager, PeerSessionListener {

    private final ExecutorService executor;
    private final List<NetworkStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final ReentrantLock connectionLock = new ReentrantLock();

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);

    private DefaultPeerListener peerListener;
    private FileDataListener fileDataListener;
    private volatile PeerSession activeSession;

    private volatile dammtp2p.core.chat.DefaultChatService chatService;
    private volatile dammtp2p.core.transfer.FileTransferManager fileTransferManager;

    private volatile String localDisplayName = "Peer";
    private volatile int currentControlPort = Constants.DEFAULT_CONTROL_PORT;
    private volatile int currentFilePort = Constants.DEFAULT_FILE_TRANSFER_PORT;

    public DefaultConnectionManager() {
        this("Peer-" + System.currentTimeMillis() % 10000);
    }

    public DefaultConnectionManager(String displayName) {
        this.localDisplayName = displayName != null ? displayName.trim() : "Peer";
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "p2p-net-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        // Prepare listeners
        this.peerListener = new DefaultPeerListener(this::handleIncomingSocket, this::handleListenerError, executor);
        this.fileDataListener = new FileDataListener(executor);
    }

    @Override
    public void startListener(int port) throws IOException {
        connectionLock.lock();
        try {
            this.currentControlPort = port;
            this.currentFilePort = port + 1;

            peerListener.startListening(currentControlPort);
            try {
                fileDataListener.startListening(currentFilePort);
            } catch (IOException e) {
                System.err.printf("Warning: Could not start File Data listener on port %d: %s%n",
                        currentFilePort, e.getMessage());
            }

            for (NetworkStateListener listener : stateListeners) {
                listener.onListeningStarted(currentControlPort);
            }
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void stopListener() {
        connectionLock.lock();
        try {
            peerListener.stopListening();
            fileDataListener.stopListening();

            for (NetworkStateListener listener : stateListeners) {
                listener.onListeningStopped();
            }
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void connectToPeer(String remoteIp, int remotePort) throws IOException {
        Objects.requireNonNull(remoteIp, "remoteIp must not be null");

        connectionLock.lock();
        try {
            if (isConnected()) {
                throw new IllegalStateException(String.format(
                        "Already connected to peer: %s. Disconnect before starting a new connection.",
                        getRemotePeerInfo()));
            }

            transitionState(ConnectionState.CONNECTING);
            for (NetworkStateListener listener : stateListeners) {
                listener.onConnecting(remoteIp, remotePort);
            }
        } finally {
            connectionLock.unlock();
        }

        // Run socket connection and handshake in background worker to avoid blocking caller/UI
        executor.submit(() -> {
            Socket socket = null;
            try {
                socket = DefaultPeerConnector.openSocket(remoteIp, remotePort);

                connectionLock.lock();
                PeerSession session;
                try {
                    transitionState(ConnectionState.HANDSHAKING);
                    session = new PeerSession(socket, true, getLocalPeerInfo(), this, executor);
                    this.activeSession = session;
                } finally {
                    connectionLock.unlock();
                }

                // Perform bidirectional handshake
                session.performHandshake();
                session.startReaderLoop();

            } catch (Exception e) {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException ignored) {}
                }

                connectionLock.lock();
                try {
                    this.activeSession = null;
                    transitionState(ConnectionState.FAILED);
                    transitionState(ConnectionState.DISCONNECTED);
                } finally {
                    connectionLock.unlock();
                }

                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                for (NetworkStateListener listener : stateListeners) {
                    listener.onConnectionFailed(remoteIp, remotePort, reason);
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Handles incoming peer TCP sockets accepted by the PeerListener.
     */
    private void handleIncomingSocket(Socket socket) {
        String remoteAddress = socket.getRemoteSocketAddress().toString();

        connectionLock.lock();
        try {
            if (isConnected() || state.get() == ConnectionState.CONNECTING || state.get() == ConnectionState.HANDSHAKING) {
                // Reject duplicate / concurrent connections while already engaged
                System.err.printf("[WARN] Rejected incoming connection from %s: session already active or in progress.%n",
                        remoteAddress);
                try {
                    socket.close();
                } catch (IOException ignored) {}
                return;
            }

            transitionState(ConnectionState.INCOMING);
            transitionState(ConnectionState.HANDSHAKING);

            PeerSession session = new PeerSession(socket, false, getLocalPeerInfo(), this, executor);
            this.activeSession = session;

            // Run responder handshake
            executor.submit(() -> {
                try {
                    session.performHandshake();
                    session.startReaderLoop();
                } catch (Exception e) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {}

                    connectionLock.lock();
                    try {
                        if (this.activeSession == session) {
                            this.activeSession = null;
                        }
                        transitionState(ConnectionState.DISCONNECTED);
                    } finally {
                        connectionLock.unlock();
                    }

                    for (NetworkStateListener listener : stateListeners) {
                        listener.onError(e);
                    }
                }
            });
        } catch (IOException e) {
            handleListenerError(e);
        } finally {
            connectionLock.unlock();
        }
    }

    private void handleListenerError(Throwable cause) {
        for (NetworkStateListener listener : stateListeners) {
            listener.onError(cause);
        }
    }

    @Override
    public void disconnectPeer() {
        PeerSession sessionToClose = null;
        connectionLock.lock();
        try {
            if (activeSession != null) {
                transitionState(ConnectionState.DISCONNECTING);
                sessionToClose = activeSession;
                activeSession = null;
            }
        } finally {
            connectionLock.unlock();
        }

        if (sessionToClose != null) {
            sessionToClose.close("User requested disconnect");
        } else {
            connectionLock.lock();
            try {
                transitionState(ConnectionState.DISCONNECTED);
            } finally {
                connectionLock.unlock();
            }
        }
    }

    // === PeerSessionListener callbacks ===

    @Override
    public void onSessionConnected(PeerSession session, PeerInfo remotePeer) {
        connectionLock.lock();
        try {
            if (activeSession == session) {
                transitionState(ConnectionState.CONNECTED);
                for (NetworkStateListener listener : stateListeners) {
                    listener.onConnected(remotePeer);
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void onSessionDisconnected(PeerSession session, PeerInfo remotePeer, String reason) {
        connectionLock.lock();
        try {
            if (activeSession == session || activeSession == null) {
                activeSession = null;
                transitionState(ConnectionState.DISCONNECTED);
                if (fileTransferManager != null) {
                    fileTransferManager.handlePeerDisconnected();
                }
                for (NetworkStateListener listener : stateListeners) {
                    listener.onDisconnected(remotePeer, reason);
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void onSessionPacket(PeerSession session, PacketHeader header, byte[] payload) {
        dammtp2p.core.protocol.PacketType type = header.getPacketType();
        switch (type) {
            case CHAT_MESSAGE:
                if (chatService != null) {
                    chatService.handleIncomingChatPacket(payload);
                }
                break;
            case FILE_TRANSFER_REQUEST:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingFileRequest(payload);
                }
                break;
            case FILE_TRANSFER_ACCEPT:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingFileAccept(payload);
                }
                break;
            case FILE_TRANSFER_REJECT:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingFileReject(payload);
                }
                break;
            case FILE_TRANSFER_CANCEL:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingFileCancel(payload);
                }
                break;
            case FILE_TRANSFER_RESUME_REQ:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingResumeRequest(payload);
                }
                break;
            case FILE_TRANSFER_RESUME_ACK:
                if (fileTransferManager != null) {
                    fileTransferManager.handleIncomingResumeAck(payload);
                }
                break;
            default:
                break;
        }
    }

    public void setChatService(dammtp2p.core.chat.DefaultChatService chatService) {
        this.chatService = chatService;
    }

    public dammtp2p.core.chat.DefaultChatService getChatService() {
        return chatService;
    }

    public void setFileTransferManager(dammtp2p.core.transfer.FileTransferManager fileTransferManager) {
        this.fileTransferManager = fileTransferManager;
    }

    public dammtp2p.core.transfer.FileTransferManager getFileTransferManager() {
        return fileTransferManager;
    }

    @Override
    public void onSessionError(PeerSession session, Throwable cause) {
        for (NetworkStateListener listener : stateListeners) {
            listener.onError(cause);
        }
    }

    private void transitionState(ConnectionState newState) {
        this.state.set(newState);
    }

    @Override
    public boolean isConnected() {
        PeerSession session = activeSession;
        return session != null && session.isConnected() && state.get() == ConnectionState.CONNECTED;
    }

    @Override
    public boolean isListening() {
        return peerListener != null && peerListener.isListening();
    }

    public ConnectionState getConnectionState() {
        return state.get();
    }

    @Override
    public PeerInfo getLocalPeerInfo() {
        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        String localIp = zt.map(NetworkUtils.NetworkCandidate::getIpAddress)
                .orElseGet(() -> {
                    List<String> usable = NetworkUtils.getUsableIpv4Addresses();
                    return usable.isEmpty() ? "127.0.0.1" : usable.get(0);
                });

        return new PeerInfo(localIp, currentControlPort, currentFilePort, localDisplayName);
    }

    @Override
    public PeerInfo getRemotePeerInfo() {
        PeerSession session = activeSession;
        return session != null ? session.getRemotePeer() : null;
    }

    public PeerSession getActiveSession() {
        return activeSession;
    }

    public FileDataListener getFileDataListener() {
        return fileDataListener;
    }

    @Override
    public void addNetworkStateListener(NetworkStateListener listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    @Override
    public void removeNetworkStateListener(NetworkStateListener listener) {
        if (listener != null) {
            stateListeners.remove(listener);
        }
    }

    /**
     * Shuts down all listeners, terminates active sessions, and releases threads.
     */
    public void shutdown() {
        stopListener();
        disconnectPeer();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
