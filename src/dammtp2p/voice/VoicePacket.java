package dammtp2p.voice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Binary UDP packet for DAMMTP2P Real-Time Voice Chat.
 *
 * <p>Wire Format:
 * <pre>
 * [4B Magic: 0x564F4943]
 * [1B PacketType]
 * [4B SequenceNumber]
 * [8B Timestamp]
 * [2B SenderNameLength] [NB SenderName UTF-8]
 * [2B RoomIdLength]     [NB RoomId UTF-8]
 * [2B PayloadLength]    [NB Payload (PCM audio or control data)]
 * </pre>
 */
public class VoicePacket {

    private final byte packetType;
    private final int sequenceNumber;
    private final long timestamp;
    private final String sender;
    private final String roomId;
    private final byte[] audioData;

    public VoicePacket(byte packetType, int sequenceNumber, long timestamp,
                       String sender, String roomId, byte[] audioData) {
        this.packetType = packetType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.sender = sender != null ? sender : "";
        this.roomId = roomId != null ? roomId : "";
        this.audioData = audioData != null ? audioData : new byte[0];
    }

    public byte getPacketType() {
        return packetType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getRoomId() {
        return roomId;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    /**
     * Serializes this voice packet into a binary byte array for UDP transmission.
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(64 + audioData.length);
            DataOutputStream out = new DataOutputStream(baos);

            out.writeInt(VoiceConstants.VOICE_MAGIC);
            out.writeByte(packetType);
            out.writeInt(sequenceNumber);
            out.writeLong(timestamp);

            byte[] senderBytes = sender.getBytes(StandardCharsets.UTF_8);
            out.writeShort(senderBytes.length);
            out.write(senderBytes);

            byte[] roomBytes = roomId.getBytes(StandardCharsets.UTF_8);
            out.writeShort(roomBytes.length);
            out.write(roomBytes);

            out.writeShort(audioData.length);
            if (audioData.length > 0) {
                out.write(audioData);
            }

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing VoicePacket", e);
        }
    }

    /**
     * Deserializes a VoicePacket from a raw byte buffer.
     */
    public static VoicePacket fromBytes(byte[] buffer, int offset, int length) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer, offset, length);
        DataInputStream in = new DataInputStream(bais);

        int magic = in.readInt();
        if (magic != VoiceConstants.VOICE_MAGIC) {
            throw new IOException(String.format("Invalid voice packet magic: 0x%08X", magic));
        }

        byte type = in.readByte();
        int seq = in.readInt();
        long time = in.readLong();

        int senderLen = in.readUnsignedShort();
        byte[] senderBytes = new byte[senderLen];
        in.readFully(senderBytes);
        String sender = new String(senderBytes, StandardCharsets.UTF_8);

        int roomLen = in.readUnsignedShort();
        byte[] roomBytes = new byte[roomLen];
        in.readFully(roomBytes);
        String roomId = new String(roomBytes, StandardCharsets.UTF_8);

        int dataLen = in.readUnsignedShort();
        byte[] audioData = new byte[dataLen];
        if (dataLen > 0) {
            in.readFully(audioData);
        }

        return new VoicePacket(type, seq, time, sender, roomId, audioData);
    }

    public static VoicePacket fromDatagramPacket(DatagramPacket datagram) throws IOException {
        return fromBytes(datagram.getData(), datagram.getOffset(), datagram.getLength());
    }

    /**
     * Calculates the Root Mean Square (RMS) amplitude of 16-bit PCM mono samples.
     * Used for Voice Activity Detection (VAD) and visual speaking wave meters.
     */
    public static double calculateRms(byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) return 0.0;
        long sum = 0;
        int sampleCount = pcmData.length / 2;
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            // Little-endian 16-bit signed integer
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sum += (long) sample * sample;
        }
        return Math.sqrt((double) sum / sampleCount);
    }

    @Override
    public String toString() {
        return String.format("VoicePacket[type=0x%02X, seq=%d, sender='%s', room='%s', bytes=%d]",
                packetType, sequenceNumber, sender, roomId, audioData.length);
    }
}
