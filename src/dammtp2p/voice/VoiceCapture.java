package dammtp2p.voice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Captures microphone audio using Java Sound API (16 kHz, 16-bit mono PCM).
 * Segments audio into 20ms packets (640 bytes), computes Voice Activity Detection (VAD),
 * and transmits UDP VoicePackets to the server.
 */
public class VoiceCapture implements Runnable {

    public interface VoiceCaptureListener {
        void onLocalSpeakingChanged(boolean isSpeaking);
        void onCaptureError(String error);
    }

    private final InetAddress serverAddress;
    private final int serverVoicePort;
    private final DatagramSocket udpSocket;
    private final String username;
    private volatile String currentRoomId;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);

    private TargetDataLine microphoneLine;
    private VoiceCaptureListener listener;
    private volatile boolean wasSpeaking = false;
    private long lastSpeakingTime = 0;

    public VoiceCapture(InetAddress serverAddress, int serverVoicePort,
                        DatagramSocket udpSocket, String username, String roomId) {
        this.serverAddress = serverAddress;
        this.serverVoicePort = serverVoicePort;
        this.udpSocket = udpSocket;
        this.username = username;
        this.currentRoomId = roomId;
    }

    public void setListener(VoiceCaptureListener listener) {
        this.listener = listener;
    }

    public void setRoomId(String roomId) {
        this.currentRoomId = roomId;
    }

    public void setMuted(boolean muted) {
        this.muted.set(muted);
        if (muted && wasSpeaking) {
            wasSpeaking = false;
            if (listener != null) listener.onLocalSpeakingChanged(false);
        }
    }

    public boolean isMuted() {
        return muted.get();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread t = new Thread(this, "voice-capture-" + username);
            t.setDaemon(true);
            t.start();
        }
    }

    public void stop() {
        running.set(false);
        if (microphoneLine != null) {
            try {
                microphoneLine.stop();
                microphoneLine.close();
            } catch (Exception ignored) {}
        }
        if (wasSpeaking && listener != null) {
            wasSpeaking = false;
            listener.onLocalSpeakingChanged(false);
        }
    }

    @Override
    public void run() {
        AudioFormat format = VoiceConstants.createAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        boolean hardwareAvailable = false;
        try {
            if (AudioSystem.isLineSupported(info)) {
                microphoneLine = (TargetDataLine) AudioSystem.getLine(info);
                microphoneLine.open(format, VoiceConstants.AUDIO_PAYLOAD_BYTES * 4);
                microphoneLine.start();
                hardwareAvailable = true;
            }
        } catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
            if (listener != null) {
                listener.onCaptureError("Microphone not available (running in fallback mode): " + e.getMessage());
            }
        }

        byte[] audioBuffer = new byte[VoiceConstants.AUDIO_PAYLOAD_BYTES];

        while (running.get()) {
            if (currentRoomId == null || currentRoomId.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            int bytesRead = 0;
            if (hardwareAvailable && microphoneLine != null && microphoneLine.isOpen()) {
                bytesRead = microphoneLine.read(audioBuffer, 0, audioBuffer.length);
            } else {
                // Synthetic timing fallback when no physical microphone is attached
                try {
                    Thread.sleep(VoiceConstants.PACKET_DURATION_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (bytesRead <= 0 && hardwareAvailable) {
                continue;
            }

            if (muted.get()) {
                if (wasSpeaking) {
                    wasSpeaking = false;
                    if (listener != null) listener.onLocalSpeakingChanged(false);
                }
                continue;
            }

            // Voice Activity Detection
            double rms = VoicePacket.calculateRms(audioBuffer);
            boolean currentlySpeaking = rms >= VoiceConstants.VAD_SILENCE_THRESHOLD;

            long now = System.currentTimeMillis();
            if (currentlySpeaking) {
                lastSpeakingTime = now;
                if (!wasSpeaking) {
                    wasSpeaking = true;
                    if (listener != null) listener.onLocalSpeakingChanged(true);
                }
            } else if (wasSpeaking && (now - lastSpeakingTime > 300)) {
                // 300ms hangover time prevents flickering between words
                wasSpeaking = false;
                if (listener != null) listener.onLocalSpeakingChanged(false);
            }

            // If speaking (or during hangover), transmit packet
            if (wasSpeaking || currentlySpeaking) {
                VoicePacket packet = new VoicePacket(
                        VoiceConstants.TYPE_AUDIO,
                        sequenceCounter.incrementAndGet(),
                        now,
                        username,
                        currentRoomId,
                        audioBuffer
                );

                byte[] packetBytes = packet.toBytes();
                try {
                    DatagramPacket datagram = new DatagramPacket(
                            packetBytes, packetBytes.length, serverAddress, serverVoicePort);
                    udpSocket.send(datagram);
                } catch (IOException ignored) {}
            }
        }
    }
}
