package dammtp2p.core.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Handles encoding and decoding of framed protocol packets over standard Java streams.
 * All text payloads are strictly encoded using {@link ProtocolConstants#PROTOCOL_CHARSET} (UTF-8).
 */
public final class MessageSerializer {

    private MessageSerializer() {
        // Utility class
    }

    /**
     * Serializes a packet with a byte array payload into a complete framed byte array.
     *
     * @param type    Type of packet
     * @param payload Raw payload bytes (can be empty byte array, not null)
     * @return Fully framed byte array [10-byte header + payload]
     */
    public static byte[] serialize(PacketType type, byte[] payload) {
        Objects.requireNonNull(type, "type must not be null");
        byte[] data = payload != null ? payload : new byte[0];

        PacketHeader header = new PacketHeader(type, data.length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(ProtocolConstants.HEADER_SIZE + data.length);
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            header.writeTo(dos);
            if (data.length > 0) {
                dos.write(data);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            // Cannot happen with ByteArrayOutputStream
            throw new RuntimeException("Unexpected serialization error", e);
        }
    }

    /**
     * Serializes a packet with a UTF-8 string payload into a complete framed byte array.
     *
     * @param type       Type of packet
     * @param utf8String UTF-8 text payload (not null)
     * @return Fully framed byte array
     */
    public static byte[] serialize(PacketType type, String utf8String) {
        Objects.requireNonNull(utf8String, "utf8String must not be null");
        byte[] payloadBytes = utf8String.getBytes(ProtocolConstants.PROTOCOL_CHARSET);
        return serialize(type, payloadBytes);
    }

    /**
     * Writes a framed packet directly to an output stream.
     *
     * @param out        Target OutputStream
     * @param type       Type of packet
     * @param utf8String UTF-8 text payload
     * @throws IOException on I/O failure
     */
    public static void writePacket(OutputStream out, PacketType type, String utf8String) throws IOException {
        byte[] framed = serialize(type, utf8String);
        out.write(framed);
        out.flush();
    }

    /**
     * Reads a packet header from an input stream.
     *
     * @param in DataInputStream wrapping the socket input
     * @return Deserialized PacketHeader
     * @throws IOException on connection drop or invalid header
     */
    public static PacketHeader readHeader(DataInputStream in) throws IOException {
        return PacketHeader.readFrom(in);
    }

    /**
     * Reads exactly {@code length} payload bytes from an input stream.
     *
     * @param in     InputStream
     * @param length Number of bytes to read
     * @return Byte array containing payload
     * @throws IOException if EOF is reached before reading all bytes
     */
    public static byte[] readPayload(InputStream in, int length) throws IOException {
        if (length < 0 || length > ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE) {
            throw new IOException(String.format("Invalid payload length: %d (max allowed: %d)",
                    length, ProtocolConstants.MAX_CONTROL_PAYLOAD_SIZE));
        }
        if (length == 0) {
            return new byte[0];
        }

        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                throw new EOFException(String.format("Premature EOF while reading payload (%d/%d bytes)", totalRead, length));
            }
            totalRead += read;
        }
        return buffer;
    }

    /**
     * Reads exactly {@code length} payload bytes and decodes them as a UTF-8 String.
     *
     * @param in     InputStream
     * @param length Length of payload in bytes
     * @return UTF-8 decoded String
     * @throws IOException on I/O error or premature EOF
     */
    public static String readUtf8Payload(InputStream in, int length) throws IOException {
        byte[] raw = readPayload(in, length);
        return new String(raw, ProtocolConstants.PROTOCOL_CHARSET);
    }
}