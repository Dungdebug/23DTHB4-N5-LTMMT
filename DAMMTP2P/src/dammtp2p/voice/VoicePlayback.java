package dammtp2p.voice;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Handles audio playback for received VoIP packets using Java Sound SourceDataLine.
 * Includes jitter queuing, software gain multiplier, and deafen controls.
 */
public class VoicePlayback implements Runnable {

    public interface VoicePlaybackListener {
        void onRemoteSpeaking(String username, boolean isSpeaking);
        void onPlaybackError(String error);
    }

    private final BlockingQueue<VoicePacket> audioQueue = new LinkedBlockingQueue<>(20);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean deafened = new AtomicBoolean(false);
    private volatile double volumeGain = 1.0; // 0.0 to 1.5

    private SourceDataLine speakerLine;
    private VoicePlaybackListener listener;

    public void setListener(VoicePlaybackListener listener) {
        this.listener = listener;
    }

    public void setDeafened(boolean deafened) {
        this.deafened.set(deafened);
        if (deafened) {
            audioQueue.clear();
        }
    }

    public boolean isDeafened() {
        return deafened.get();
    }

    public void setVolume(double gain) {
        this.volumeGain = Math.max(0.0, Math.min(2.0, gain));
    }

    public double getVolume() {
        return volumeGain;
    }

    public void enqueuePacket(VoicePacket packet) {
        if (deafened.get() || !running.get()) return;
        if (packet.getAudioData() != null && packet.getAudioData().length > 0) {
            // Drop oldest packets if queue is overflowing to minimize latency
            if (audioQueue.remainingCapacity() == 0) {
                audioQueue.poll();
            }
            audioQueue.offer(packet);
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread t = new Thread(this, "voice-playback-worker");
            t.setDaemon(true);
            t.start();
        }
    }

    public void stop() {
        running.set(false);
        audioQueue.clear();
        if (speakerLine != null) {
            try {
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void run() {
        AudioFormat format = VoiceConstants.createAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        boolean hardwareAvailable = false;
        try {
            if (AudioSystem.isLineSupported(info)) {
                speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                speakerLine.open(format, VoiceConstants.AUDIO_PAYLOAD_BYTES * 8);
                speakerLine.start();
                hardwareAvailable = true;
            }
        } catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
            if (listener != null) {
                listener.onPlaybackError("Speaker not available (running in fallback mode): " + e.getMessage());
            }
        }

        while (running.get()) {
            try {
                VoicePacket packet = audioQueue.poll(100, TimeUnit.MILLISECONDS);
                if (packet == null) continue;

                if (deafened.get()) continue;

                byte[] rawAudio = packet.getAudioData();
                if (rawAudio == null || rawAudio.length == 0) continue;

                // Notify UI of remote speaker active
                if (listener != null) {
                    listener.onRemoteSpeaking(packet.getSender(), true);
                }

                byte[] processedAudio = rawAudio;
                if (Math.abs(volumeGain - 1.0) > 0.05) {
                    processedAudio = applySoftwareGain(rawAudio, volumeGain);
                }

                if (hardwareAvailable && speakerLine != null && speakerLine.isOpen()) {
                    speakerLine.write(processedAudio, 0, processedAudio.length);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private byte[] applySoftwareGain(byte[] input, double gain) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length - 1; i += 2) {
            short sample = (short) ((input[i] & 0xFF) | (input[i + 1] << 8));
            int amplified = (int) (sample * gain);
            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;

            output[i] = (byte) (amplified & 0xFF);
            output[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }
        return output;
    }
}