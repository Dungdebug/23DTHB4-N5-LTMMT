package dammtp2p.core.protocol;

/**
 * Type-safe enumeration of framed packet types with explicit numeric opcodes.
 */
public enum PacketType {

    // === Handshake & Session Control (0x01 - 0x0F) ===
    /** Initial handshake request with sender PeerInfo */
    HANDSHAKE_REQ((byte) 0x01),

    /** Handshake acknowledgment with responder PeerInfo */
    HANDSHAKE_ACK((byte) 0x02),

    /** Liveness heartbeat probe */
    HEARTBEAT((byte) 0x03),

    /** Liveness heartbeat acknowledgment */
    HEARTBEAT_ACK((byte) 0x04),

    /** Graceful disconnect notification */
    DISCONNECT((byte) 0x05),

    // === 1-to-1 Chat (0x10 - 0x1F) ===
    /** UTF-8 text chat message */
    CHAT_MESSAGE((byte) 0x10),

    /** Chat message receipt confirmation */
    CHAT_ACK((byte) 0x11),

    // === File Transfer Negotiation (0x20 - 0x3F) ===
    /** Sender requests file transfer with FileMetadata */
    FILE_TRANSFER_REQUEST((byte) 0x20),

    /** Receiver accepts file transfer (specifies receive offset for resume) */
    FILE_TRANSFER_ACCEPT((byte) 0x21),

    /** Receiver rejects file transfer */
    FILE_TRANSFER_REJECT((byte) 0x22),

    /** Receiver or sender requests resuming from a specific byte offset */
    FILE_TRANSFER_RESUME_REQ((byte) 0x23),

    /** Acknowledges resume request and confirms starting offset */
    FILE_TRANSFER_RESUME_ACK((byte) 0x24),

    /** Sender or receiver requests immediate transfer cancellation */
    FILE_TRANSFER_CANCEL((byte) 0x25),

    /** Sender or receiver pauses active transfer */
    FILE_TRANSFER_PAUSE((byte) 0x26),

    /** Receiver notifies sender that full file was received and verified */
    FILE_TRANSFER_COMPLETE((byte) 0x27),

    /** Receiver notifies sender of SHA-256 integrity verification failure */
    FILE_TRANSFER_VERIFY_FAILED((byte) 0x28),

    // === Authentication & User Management (0x40 - 0x4F) ===
    /** User login request containing username and password */
    LOGIN_REQ((byte) 0x40),

    /** Server response to login request */
    LOGIN_RESP((byte) 0x41),

    /** User account registration request */
    REGISTER_REQ((byte) 0x42),

    /** Server response to registration request */
    REGISTER_RESP((byte) 0x43),

    /** Request for current list of online users */
    USER_LIST_REQ((byte) 0x44),

    /** Server list of active online users */
    USER_LIST_RESP((byte) 0x45),

    /** Server broadcast of user state change (online/offline) */
    USER_STATUS_BROADCAST((byte) 0x46),

    // === Friendship Management (0x48 - 0x4F) ===
    /** Request to add a friend */
    FRIEND_ADD_REQ((byte) 0x48),

    /** Response or notification of friend request/add */
    FRIEND_ADD_RESP((byte) 0x49),

    /** Request to remove a friend */
    FRIEND_REMOVE_REQ((byte) 0x4A),

    /** Synchronize or broadcast friends list */
    FRIEND_LIST_SYNC((byte) 0x4B),

    // === Rooms & Group Chat (0x50 - 0x5F) ===
    /** Request for list of existing chat rooms */
    ROOM_LIST_REQ((byte) 0x50),

    /** Server response containing room list */
    ROOM_LIST_RESP((byte) 0x51),

    /** Request to create a new chat room */
    ROOM_CREATE_REQ((byte) 0x52),

    /** Request to join a chat room */
    ROOM_JOIN_REQ((byte) 0x53),

    /** Request to leave a chat room */
    ROOM_LEAVE_REQ((byte) 0x54),

    /** Group chat message broadcast within a room */
    ROOM_CHAT_MESSAGE((byte) 0x55),

    /** Request list of members in a room */
    ROOM_MEMBERS_REQ((byte) 0x56),

    /** Server response containing room member list */
    ROOM_MEMBERS_RESP((byte) 0x57),

    /** Distribution of 128-bit AES room encryption key */
    ROOM_KEY_PAYLOAD((byte) 0x58),

    /** 1-to-1 private chat message routed via server */
    PRIVATE_CHAT_MESSAGE((byte) 0x59),

    // === Encryption & Key Exchange (0x60 - 0x6F) ===
    /** Request conversation or session encryption key */
    KEY_EXCHANGE_REQ((byte) 0x60),

    /** Server response distributing conversation encryption key */
    KEY_EXCHANGE_RESP((byte) 0x61),

    // === Voice Chat Control (0x70 - 0x7F) ===
    /** Request to join a voice channel */
    VOICE_JOIN_REQ((byte) 0x70),

    /** Response confirming voice channel join */
    VOICE_JOIN_RESP((byte) 0x71),

    /** Request to leave a voice channel */
    VOICE_LEAVE_REQ((byte) 0x72),

    /** Broadcast of members currently connected to a voice channel */
    VOICE_MEMBERS_BROADCAST((byte) 0x73),

    /** 1-to-1 direct voice call initiation request */
    VOICE_CALL_REQUEST((byte) 0x74),

    /** 1-to-1 direct voice call accept */
    VOICE_CALL_ACCEPT((byte) 0x75),

    /** 1-to-1 direct voice call reject */
    VOICE_CALL_REJECT((byte) 0x76),

    /** 1-to-1 direct voice call end / hang up */
    VOICE_CALL_END((byte) 0x77);

    private final byte code;

    PacketType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * Resolves a PacketType by its numeric byte code.
     *
     * @param code The 1-byte opcode
     * @return The corresponding PacketType
     * @throws IllegalArgumentException if the code is unknown
     */
    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException(String.format("Unknown PacketType opcode: 0x%02X", code));
    }
}
