package dammtp2p.voice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side Voice Chat coordinator.
 * Manages local UDP socket, audio capture, audio playback, heartbeat pings,
 * and room join/leave lifecycle.
 */
public class VoiceClient {

    public interface VoiceListener {
        void onVoiceConnected(String roomId);
        void onVoiceDisconnected();
        void onLocalSpeaking(boolean isSpeaking);
        void onRemoteSpeaking(String username, boolean isSpeaking);
        void onVoiceError(String message);
    }

    private final String username;
    private final InetAddress serverAddress;
    private final int serverVoicePort;

    private DatagramSocket udpSocket;
    private VoiceCapture capture;
    private VoicePlayback playback;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean inVoice = new AtomicBoolean(false);
    private volatile String currentRoomId = null;

    private final List<VoiceListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "voice-client-worker");
        t.setDaemon(true);
        return t;
    });

    public VoiceClient(String username, InetAddress serverAddress, int serverVoicePort) {
        this.username = username;
        this.serverAddress = serverAddress;
        this.serverVoicePort = serverVoicePort;
    }

    public void addListener(VoiceListener listener) {
        listeners.add(listener);
    }

    public void removeListener(VoiceListener listener) {
        listeners.remove(listener);
    }

    public synchronized void start() throws SocketException {
        if (running.get()) return;

        udpSocket = new DatagramSocket(); // Bind to any available local UDP port
        running.set(true);

        playback = new VoicePlayback();
        playback.setListener(new VoicePlayback.VoicePlaybackListener() {
            @Override
            public void onRemoteSpeaking(String remoteUser, boolean isSpeaking) {
                for (VoiceListener l : listeners) {
                    l.onRemoteSpeaking(remoteUser, isSpeaking);
                }
            }

            @Override
            public void onPlaybackError(String error) {
                for (VoiceListener l : listeners) {
                    l.onVoiceError(error);
                }
            }
        });
        playback.start();

        capture = new VoiceCapture(serverAddress, serverVoicePort, udpSocket, username, null);
        capture.setListener(new VoiceCapture.VoiceCaptureListener() {
            @Override
            public void onLocalSpeakingChanged(boolean isSpeaking) {
                for (VoiceListener l : listeners) {
                    l.onLocalSpeaking(isSpeaking);
                }
            }

            @Override
            public void onCaptureError(String error) {
                for (VoiceListener l : listeners) {
                    l.onVoiceError(error);
                }
            }
        });
        capture.start();

        executor.submit(this::receiveLoop);
        executor.submit(this::heartbeatLoop);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;

        leaveVoiceRoom();

        if (capture != null) capture.stop();
        if (playback != null) playback.stop();
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        executor.shutdownNow();
    }

    public synchronized void joinVoiceRoom(String roomId) {
        if (!running.get() || roomId == null || roomId.trim().isEmpty()) return;

        this.currentRoomId = roomId.trim();
        if (capture != null) {
            capture.setRoomId(this.currentRoomId);
        }

        inVoice.set(true);

        // Send JOIN packet to server UDP
        sendControlPacket(VoiceConstants.TYPE_JOIN, this.currentRoomId);

        for (VoiceListener l : listeners) {
            l.onVoiceConnected(this.currentRoomId);
        }
    }

    public synchronized void leaveVoiceRoom() {
        if (!inVoice.get()) return;

        String oldRoom = this.currentRoomId;
        this.currentRoomId = null;
        inVoice.set(false);

        if (capture != null) {
            capture.setRoomId(null);
        }

        // Send LEAVE packet to server UDP
        if (oldRoom != null) {
            sendControlPacket(VoiceConstants.TYPE_LEAVE, oldRoom);
        }

        for (VoiceListener l : listeners) {
            l.onVoiceDisconnected();
        }
    }

    public void setMuted(boolean muted) {
        if (capture != null) {
            capture.setMuted(muted);
        }
    }

    public boolean isMuted() {
        return capture != null && capture.isMuted();
    }

    public void setDeafened(boolean deafened) {
        if (playback != null) {
            playback.setDeafened(deafened);
        }
        // When deafened, automatically mute microphone as well
        setMuted(deafened);
    }

    public boolean isDeafened() {
        return playback != null && playback.isDeafened();
    }

    public void setVolume(double gain) {
        if (playback != null) {
            playback.setVolume(gain);
        }
    }

    public double getVolume() {
        return playback != null ? playback.getVolume() : 1.0;
    }

    public boolean isInVoice() {
        return inVoice.get();
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    private void sendControlPacket(byte type, String roomId) {
        if (udpSocket == null || udpSocket.isClosed()) return;
        try {
            VoicePacket packet = new VoicePacket(type, 0, System.currentTimeMillis(), username, roomId, null);
            byte[] bytes = packet.toBytes();
            DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, serverAddress, serverVoicePort);
            udpSocket.send(datagram);
        } catch (IOException ignored) {}
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(datagram);

                VoicePacket packet = VoicePacket.fromBytes(datagram.getData(), datagram.getOffset(), datagram.getLength());
                if (packet.getPacketType() == VoiceConstants.TYPE_AUDIO) {
                    if (playback != null) {
                        playback.enqueuePacket(packet);
                    }
                }
            } catch (SocketException e) {
                if (!running.get()) break;
            } catch (IOException ignored) {}
        }
    }

    private void heartbeatLoop() {
        while (running.get()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                break;
            }
            if (inVoice.get() && currentRoomId != null) {
                sendControlPacket(VoiceConstants.TYPE_PING, currentRoomId);
            }
        }
    }
}
