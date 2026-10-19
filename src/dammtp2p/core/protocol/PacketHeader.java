package dammtp2p.core.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Represents the 10-byte fixed binary header for framed control and chat packets.
 *
 * <p>Wire Layout:
 * <pre>
 * +----------------------+------------------+------------------+-----------------------+
 * | Magic (4 bytes, int) | Version (1 byte) | Type (1 byte)    | Length (4 bytes, int) |
 * +----------------------+------------------+------------------+-----------------------+
 * </pre>
 */
public class PacketHeader {

    private final int magicNumber;
    private final byte version;
    private final PacketType packetType;
    private final int payloadLength;

    public PacketHeader(PacketType packetType, int payloadLength) {
        this(ProtocolConstants.MAGIC_NUMBER, ProtocolConstants.PROTOCOL_VERSION, packetType, payloadLength);
    }

    public PacketHeader(int magicNumber, byte version, PacketType packetType, int payloadLength) {
        if (payloadLength < 0) {
            throw new IllegalArgumentException("payloadLength cannot be negative: " + payloadLength);
        }
        if (payloadLength > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "payloadLength (%d) exceeds MAX_CONTROL_PAYLOAD_SIZE (%d)",
                    payloadLength, ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE));
        }

        this.magicNumber = magicNumber;
        this.version = version;
        this.packetType = Objects.requireNonNull(packetType, "packetType must not be null");
        this.payloadLength = payloadLength;
    }

    public int getMagicNumber() {
        return magicNumber;
    }

    public byte getVersion() {
        return version;
    }

    public PacketType getPacketType() {
        return packetType;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    /**
     * Checks if the header contains a valid magic identifier and compatible version.
     */
    public boolean isValid() {
        return magicNumber == ProtocolConstants.MAGIC_NUMBER && version == ProtocolConstants.PROTOCOL_VERSION;
    }

    /**
     * Writes this header to a DataOutputStream.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(magicNumber);
        out.writeByte(version);
        out.writeByte(packetType.getCode());
        out.writeInt(payloadLength);
    }

    /**
     * Reads a PacketHeader from a DataInputStream.
     *
     * @param in Stream to read 10 bytes from
     * @return Deserialized PacketHeader
     * @throws IOException on EOF or I/O failure
     * @throws ProtocolException if magic or version is invalid
     */
    public static PacketHeader readFrom(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != ProtocolConstants.MAGIC_NUMBER) {
            throw new IOException(String.format("Invalid protocol magic: 0x%08X (expected 0x%08X)",
                    magic, ProtocolConstants.MAGIC_NUMBER));
        }
        byte version = in.readByte();
        if (version != ProtocolConstants.PROTOCOL_VERSION) {
            throw new IOException(String.format("Unsupported protocol version: %d (expected %d)",
                    version, ProtocolConstants.PROTOCOL_VERSION));
        }
        byte typeCode = in.readByte();
        PacketType type;
        try {
            type = PacketType.fromCode(typeCode);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Unsupported packet type code: 0x" + Integer.toHexString(typeCode & 0xFF), ex);
        }
        int length = in.readInt();
        if (length < 0 || length > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
            throw new IOException(String.format("Invalid packet payload length: %d (max: %d)",
                    length, ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE));
        }
        return new PacketHeader(magic, version, type, length);
    }

    @Override
    public String toString() {
        return String.format("PacketHeader[type=%s, len=%d, v=%d]", packetType, payloadLength, version);
    }
}
