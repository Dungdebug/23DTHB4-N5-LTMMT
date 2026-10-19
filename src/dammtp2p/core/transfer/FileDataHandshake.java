package dammtp2p.core.transfer;

import dammtp2p.util.Constants;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Binary control handshake transmitted over the dedicated File Data Channel (Port 8889)
 * before raw/encrypted file bytes are streamed.
 *
 * <p>Identifies and validates the incoming transfer against the negotiated offer on Port 8888.
 * Supports 64-bit file sizes up to 20 GiB (21,474,836,480 bytes), resume offsets, and client role (SENDER or RECEIVER)
 * for server-relayed data routing.
 */
public class FileDataHandshake {

    /** 4-byte magic number: ASCII "DAMF" (0x44414D46) */
    public static final int MAGIC = 0x44414D46;

    /** Current protocol version for the binary data channel */
    public static final byte VERSION = 2;

    /** Client connection roles for server data relay */
    public static final byte ROLE_SENDER = 0x01;
    public static final byte ROLE_RECEIVER = 0x02;

    /** Receiver status codes */
    public static final byte STATUS_REJECTED = 0x00;
    public static final byte STATUS_OK = 0x01;
    public static final byte STATUS_COMPLETE = 0x02;

    private final int version;
    private final byte role;
    private final String transferId;
    private final long fileSize;
    private final long startingOffset;

    public FileDataHandshake(int version, byte role, String transferId, long fileSize, long startingOffset) {
        if (fileSize < 0L || fileSize > Constants.MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Invalid fileSize: " + fileSize + " (max allowed: " + Constants.MAX_FILE_SIZE_BYTES + ")");
        }
        if (startingOffset < 0L || startingOffset > fileSize) {
            throw new IllegalArgumentException(String.format("Invalid startingOffset (%d) for fileSize (%d)", startingOffset, fileSize));
        }
        this.version = version;
        this.role = role;
        this.transferId = Objects.requireNonNull(transferId, "transferId must not be null");
        this.fileSize = fileSize;
        this.startingOffset = startingOffset;
    }

    public FileDataHandshake(String transferId, long fileSize, long startingOffset, byte role) {
        this(VERSION, role, transferId, fileSize, startingOffset);
    }

    public FileDataHandshake(String transferId, long fileSize, long startingOffset) {
        this(VERSION, ROLE_SENDER, transferId, fileSize, startingOffset);
    }

    public int getVersion() {
        return version;
    }

    public byte getRole() {
        return role;
    }

    public String getTransferId() {
        return transferId;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getStartingOffset() {
        return startingOffset;
    }

    /**
     * Serializes this handshake header to the data output stream.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(version);
        out.writeByte(role);
        out.writeUTF(transferId);
        out.writeLong(fileSize);
        out.writeLong(startingOffset);
        out.flush();
    }

    /**
     * Reads and parses a handshake header from the data input stream.
     */
    public static FileDataHandshake readFrom(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException(String.format("Invalid file data magic header: 0x%08X (expected 0x%08X)", magic, MAGIC));
        }

        byte ver = in.readByte();
        byte role = ROLE_SENDER;
        if (ver >= 2) {
            role = in.readByte();
        }

        String tid = in.readUTF();
        long size = in.readLong();
        long offset = in.readLong();

        if (size < 0L || offset < 0L || size > Constants.MAX_FILE_SIZE_BYTES || offset > size) {
            throw new IOException(String.format("Invalid size or offset in handshake: size=%d, offset=%d (max allowed: %d)",
                    size, offset, Constants.MAX_FILE_SIZE_BYTES));
        }

        return new FileDataHandshake(ver, role, tid, size, offset);
    }

    /**
     * Sends a status response back across the data channel.
     */
    public static void sendResponse(DataOutputStream out, byte status, String message) throws IOException {
        out.writeByte(status);
        out.writeUTF(message != null ? message : "");
        out.flush();
    }

    /**
     * Reads a status response from the data channel.
     */
    public static Response readResponse(DataInputStream in) throws IOException {
        byte status = in.readByte();
        String msg = in.readUTF();
        return new Response(status, msg);
    }

    /**
     * Response payload representing handshake validation or completion confirmation.
     */
    public static class Response {
        private final byte status;
        private final String message;

        public Response(byte status, String message) {
            this.status = status;
            this.message = message;
        }

        public byte getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public boolean isOk() {
            return status == STATUS_OK;
        }

        public boolean isComplete() {
            return status == STATUS_COMPLETE;
        }
    }
}
