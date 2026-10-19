package dammtp2p.voice;

import javax.sound.sampled.AudioFormat;

/**
 * Audio format and network protocol constants for DAMMTP2P Real-Time Voice Chat.
 * Uses wideband PCM (16 kHz, 16-bit mono) optimized for VoIP over ZeroTier LAN.
 */
public final class VoiceConstants {

    private VoiceConstants() {
        // Prevent instantiation
    }

    /** Audio sample rate: 16,000 Hz (Wideband VoIP) */
    public static final float SAMPLE_RATE = 16000.0f;

    /** Bits per sample: 16-bit signed PCM */
    public static final int SAMPLE_SIZE_BITS = 16;

    /** Channels: 1 (Mono) */
    public static final int CHANNELS = 1;

    /** Frame size in bytes: 2 bytes per frame (16-bit mono) */
    public static final int FRAME_SIZE_BYTES = 2;

    /** Frame rate: 16,000 frames/sec */
    public static final float FRAME_RATE = 16000.0f;

    /** Audio frame duration per packet: 20 milliseconds (Standard VoIP frame) */
    public static final int PACKET_DURATION_MS = 20;

    /** Samples per 20ms packet: 16000 * 0.02 = 320 samples */
    public static final int SAMPLES_PER_PACKET = (int) (SAMPLE_RATE * PACKET_DURATION_MS / 1000);

    /** Audio payload byte size per packet: 320 * 2 = 640 bytes */
    public static final int AUDIO_PAYLOAD_BYTES = SAMPLES_PER_PACKET * FRAME_SIZE_BYTES;

    /** Default UDP Voice Relay port */
    public static final int DEFAULT_VOICE_PORT = 8890;

    /** 4-byte magic header for voice UDP datagrams ("VOIC" in hex: 0x56 0x4F 0x49 0x43) */
    public static final int VOICE_MAGIC = 0x564F4943;

    // UDP Packet Types
    public static final byte TYPE_AUDIO = 0x01;
    public static final byte TYPE_JOIN = 0x02;
    public static final byte TYPE_LEAVE = 0x03;
    public static final byte TYPE_PING = 0x04;
    public static final byte TYPE_SPEAKING = 0x05;

    /** Voice Activity Detection (VAD) RMS amplitude threshold */
    public static final double VAD_SILENCE_THRESHOLD = 250.0;

    /** Inactive timeout in milliseconds before a voice endpoint is pruned from room */
    public static final long ENDPOINT_TIMEOUT_MS = 15_000;

    /**
     * Creates standard Java AudioFormat for VoIP voice capture and playback.
     */
    public static AudioFormat createAudioFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                FRAME_SIZE_BYTES,
                FRAME_RATE,
                false // Little-endian (standard for x86/ARM and Windows/Linux)
        );
    }
}
