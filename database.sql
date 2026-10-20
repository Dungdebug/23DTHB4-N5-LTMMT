-- ==============================================================================
-- DU AN: DAMMTP2P - Ung dung Chat, Truyen Tep Sieu Lon va Voice UDP (P2P/Relay)
-- TE P TIN: database.sql
-- MO TA: Tap lenh SQL hoan chinh de thiet ke va khoi tao Co So Du Lieu (CSDL).
-- TUONG THICH: SQLite 3+ va MySQL 8.0+ / MariaDB / PostgreSQL (chuan ANSI SQL).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. BANG NGUOI DUNG VA XAC THUC (USERS & AUTHENTICATION)
-- Tuong ung: AuthService.java, CryptoUtils.java, accounts.json
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,            -- Ten dang nhap (viet thuong khong dau)
    display_name VARCHAR(100) NOT NULL,                   -- Ten hien thi tren giao dien chat
    salt VARCHAR(64) NOT NULL,                            -- Chuoi muoi 16-byte ngau nhien (dinh dang Hex)
    password_hash VARCHAR(128) NOT NULL,                  -- Mat khau bam PBKDF2-HMAC-SHA256 (10,000 vong)
    status VARCHAR(20) DEFAULT 'OFFLINE',                 -- ONLINE, OFFLINE, BUSY, IN_VOICE
    last_ip VARCHAR(45) NULL,                             -- Dia chi IP ket noi cuoi cung (ho tro ca IPv4/IPv6/ZeroTier)
    last_port INT NULL,                                   -- Port ket noi cuoi cung
    created_at BIGINT NOT NULL,                           -- Thoi diem tao tai khoan (Unix timestamp ms)
    last_login BIGINT NULL                                -- Thoi diem dang nhap gan nhat (Unix timestamp ms)
);

-- ------------------------------------------------------------------------------
-- 2. BANG PHONG CHAT VA KENH THOAI (CHAT ROOMS & VOICE CHANNELS)
-- Tuong ung: RoomManager.java
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_rooms (
    room_id VARCHAR(50) NOT NULL PRIMARY KEY,             -- Ma phong (vd: Lobby, General, Voice_Lounge)
    display_name VARCHAR(100) NOT NULL,                   -- Ten hien thi tieng Viet / bieu tuong
    room_key_aes VARCHAR(64) NULL,                        -- Khoa AES-128 GCM (Base64) ma hoa tin nhan phong
    created_by VARCHAR(50) NULL,                          -- Nguoi tao phong (khoa ngoai den users)
    is_voice_enabled BOOLEAN DEFAULT 1,                   -- Cho phep dam thoai UDP trong phong
    created_at BIGINT NOT NULL,                           -- Thoi gian tao phong (Unix timestamp ms)
    FOREIGN KEY (created_by) REFERENCES users(username) ON DELETE SET NULL
);

-- ------------------------------------------------------------------------------
-- 3. BANG THANH VIEN TRONG PHONG (ROOM MEMBERSHIPS)
-- Tuong ung: RoomManager.ChatRoom.getMembers()
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_members (
    room_id VARCHAR(50) NOT NULL,
    username VARCHAR(50) NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',                    -- OWNER, ADMIN, MEMBER
    is_muted BOOLEAN DEFAULT 0,                           -- Trang thai tat micro trong kenh voice
    is_deafened BOOLEAN DEFAULT 0,                        -- Trang thai tat loa trong kenh voice
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (room_id, username),
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 4. BANG TIN NHAN TRO CHUYEN (CHAT MESSAGES)
-- Tuong ung: ChatMessage.java (ho tro chat 1-1 rieng tu va chat nhom)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY,          -- UUID duy nhat cua tin nhan
    sender VARCHAR(50) NOT NULL,                          -- Nguoi gui tin nhan
    receiver VARCHAR(50) NULL,                            -- Nguoi nhan (NULL neu la tin nhan phong chat)
    room_id VARCHAR(50) NULL,                             -- Phong nhan tin (NULL neu la chat rieng 1-1)
    content TEXT NOT NULL,                                -- Noi dung van ban hoac Ciphertext ma hoa
    is_encrypted BOOLEAN DEFAULT 0,                       -- 1 neu noi dung duoc ma hoa AES-128-GCM, 0 neu plain
    iv VARCHAR(32) NULL,                                  -- Vector khoi tao IV (12-byte Hex/Base64)
    timestamp BIGINT NOT NULL,                            -- Thoi gian gui tin (Unix timestamp ms)
    status VARCHAR(20) DEFAULT 'SENT',                    -- SENT, DELIVERED, READ
    FOREIGN KEY (sender) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (receiver) REFERENCES users(username) ON DELETE SET NULL,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 5. BANG LICH SU TRUYEN TEP DUNG LUONG LON (FILE TRANSFERS)
-- Tuong ung: FileMetadata.java, FileTransferManager.java, TransferState.java
-- Luu y: file_size va offset_bytes bat buoc dung BIGINT (64-bit) de ho tro file 20 GiB
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_transfers (
    transfer_id VARCHAR(64) NOT NULL PRIMARY KEY,         -- UUID cua phien truyen tep tin
    file_name VARCHAR(255) NOT NULL,                      -- Ten file goc (da duoc sanitize chong path traversal)
    file_size BIGINT NOT NULL,                            -- Kich thuoc file tinh theo Byte (cho phep toi 20 GiB)
    sha256 VARCHAR(64) NULL,                              -- Ma bam SHA-256 (64 hex characters) kiem tra toan ven
    offset_bytes BIGINT DEFAULT 0,                        -- So byte da truyen (dung de Resume khi dut ket noi)
    sender VARCHAR(50) NOT NULL,                          -- Nguoi gui file
    receiver VARCHAR(50) NULL,                            -- Nguoi nhan (NULL neu truyen multicast den phong)
    room_id VARCHAR(50) NULL,                             -- Phong nhan tep neu gui cho ca nhom
    is_encrypted BOOLEAN DEFAULT 0,                       -- Tep tin co ma hoa noi dung truoc khi gui khong
    aes_key VARCHAR(64) NULL,                             -- Khoa ma hoa AES file (neu co)
    base_iv VARCHAR(32) NULL,                             -- Vector khoi tao IV cua file (neu co)
    status VARCHAR(30) NOT NULL,                          -- PENDING, TRANSFERRING, COMPLETED, FAILED, CANCELLED
    error_message TEXT NULL,                              -- Thong tin loi neu qua trinh truyen gap su co
    created_at BIGINT NOT NULL,                           -- Thoi gian khoi tao phien gui (ms)
    completed_at BIGINT NULL,                             -- Thoi gian hoan tat gui file (ms)
    FOREIGN KEY (sender) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (receiver) REFERENCES users(username) ON DELETE SET NULL,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE SET NULL
);

-- ------------------------------------------------------------------------------
-- 6. BANG PHIEN DAM THOAI VOICE UDP (VOICE CALL SESSIONS)
-- Tuong ung: VoiceServerRelay.java, VoiceClient.java
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS voice_sessions (
    session_id VARCHAR(64) NOT NULL PRIMARY KEY,          -- UUID phien goi thoai
    room_id VARCHAR(50) NOT NULL,                         -- Kenh thoai / phong dien ra cuoc goi
    caller_username VARCHAR(50) NOT NULL,                 -- Nguoi bat dau ket noi voice
    started_at BIGINT NOT NULL,                           -- Thoi diem bat dau (ms)
    ended_at BIGINT NULL,                                 -- Thoi diem ket thuc (ms)
    total_packets BIGINT DEFAULT 0,                       -- Tong so goi tin UDP truyen nhan
    total_bytes BIGINT DEFAULT 0,                         -- Tong dung luong stream am thanh
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (caller_username) REFERENCES users(username) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 7. BANG NHAT KY HE THONG & GIAM SAT SERVER (SERVER AUDIT LOGS)
-- Tuong ung: ServerDashboardPanel.java, ServerCore.logEvent()
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS server_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,             -- SQLite: AUTOINCREMENT / MySQL: AUTO_INCREMENT
    event_type VARCHAR(50) NOT NULL,                      -- AUTH, CHAT, FILE_RELAY, VOICE, SECURITY, ERROR
    source_user VARCHAR(50) NULL,                         -- Nguoi dung gay ra su kien (neu co)
    source_ip VARCHAR(45) NULL,                           -- IP client
    message TEXT NOT NULL,                                -- Chi tiet nhat ky
    created_at BIGINT NOT NULL                            -- Thoi diem ghi log (ms)
);

-- ------------------------------------------------------------------------------
-- 8. BANG CAU HINH CLIENT & SERVER (APPLICATION SETTINGS)
-- Tuong ung: AppConfig.java, client_config.json
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    config_key VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',       -- 'SYSTEM' cho Server, hoac ten user cho Client
    config_value TEXT NOT NULL,                           -- Gia tri luu tru
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (config_key, username)
);

-- ------------------------------------------------------------------------------
-- 9. BANG QUAN HE BAN BE (FRIENDSHIPS)
-- Tuong ung: FriendsManager.java
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS friends (
    user_id VARCHAR(50) NOT NULL,
    friend_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACCEPTED',                 -- ACCEPTED, PENDING, BLOCKED
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(username) ON DELETE CASCADE
);

-- ==============================================================================
-- INDEXES - TOI UU HOA TOC DO TRUY VAN DU LIEU MANG
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_friends_user ON friends(user_id);
CREATE INDEX IF NOT EXISTS idx_friends_friend ON friends(friend_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON chat_messages(sender);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON chat_messages(receiver);
CREATE INDEX IF NOT EXISTS idx_messages_room ON chat_messages(room_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON chat_messages(timestamp);
CREATE INDEX IF NOT EXISTS idx_transfers_sender ON file_transfers(sender);
CREATE INDEX IF NOT EXISTS idx_transfers_receiver ON file_transfers(receiver);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON file_transfers(status);
CREATE INDEX IF NOT EXISTS idx_logs_type ON server_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_logs_created ON server_logs(created_at);

-- ==============================================================================
-- DU LIEU MAU KHOI TAO SAN CO (INITIAL SEED DATA)
-- Dong bo voi tai khoan trong server_data/accounts.json va phong mac dinh
-- ==============================================================================

-- 1. Tai khoan mac dinh (Salt va Hash PBKDF2 goc tu he thong)
INSERT OR REPLACE INTO users (username, display_name, salt, password_hash, status, created_at) VALUES
('alice', 'Alice Wonder', '0463c8b2980431f091c54327c48b18da', 'b058eb923047cae032df462e5a4d7b14be91fe1ef9beb04ba2f3f43bd1335e5b', 'OFFLINE', 1700000000000),
('bob', 'Bob Builder', 'f0ac73816e3f53a0b6e1adad2ad103a1', '893743b42297b76fe666576feaad01578b2788d0ad4d9f5261aa7657c4caa0ff', 'OFFLINE', 1700000000000),
('charlie', 'Charlie Chaplin', '01161e79f149945bc69b9ee0b735e931', '5095eb2b6fcfa90c0fc1a8c6f2f7b85453bc9027894f1c76e8bd3fbe0e6147d7', 'OFFLINE', 1700000000000),
('admin', 'Administrator', 'f104475e8b0fad1eb8a51f64d9b940e4', '0a2101d2543bbc87158dfc4e52c027752986a928e007662b23e9d392d0247866', 'OFFLINE', 1700000000000),
('tan kiet', 'Tan Kiet', '431d206cdd998f7d1179b01d499729f3', '17cd01ede64e556bb24eb932ef247af12fe07a6bbc78d7f338b99320edde0749', 'OFFLINE', 1700000000000),
('giasuo', 'Giasuo Master', '1dbf6421f1eee8c1bb96c07b3b0c2b17', '43b401b6cd5ae953b2c976900fb8bf4f7f24a54402f9af7c13e163179cc347c0', 'OFFLINE', 1700000000000);

-- 2. Cac phong mac dinh tu RoomManager.java
INSERT OR REPLACE INTO chat_rooms (room_id, display_name, created_by, is_voice_enabled, created_at) VALUES
('Lobby', 'Phòng Chung (Lobby)', 'admin', 1, 1700000000000),
('General', 'Thảo Luận Chung (General)', 'admin', 1, 1700000000000),
('Voice_Lounge', '🔊 Kênh Thoại Voice Lounge', 'admin', 1, 1700000000000),
('MMT_NHOM05', 'Nhóm 05 MMT', 'admin', 1, 1700000000000);

-- 3. Gan quyen Admin cho user admin trong cac phong
INSERT OR REPLACE INTO room_members (room_id, username, role, joined_at) VALUES
('Lobby', 'admin', 'ADMIN', 1700000000000),
('General', 'admin', 'ADMIN', 1700000000000),
('Voice_Lounge', 'admin', 'ADMIN', 1700000000000),
('MMT_NHOM05', 'admin', 'ADMIN', 1700000000000);

-- 4. Cau hinh mac dinh ung dung
INSERT OR REPLACE INTO app_settings (config_key, username, config_value, updated_at) VALUES
('server_tcp_control_port', 'SYSTEM', '8888', 1700000000000),
('server_tcp_file_port', 'SYSTEM', '8889', 1700000000000),
('server_udp_voice_port', 'SYSTEM', '8890', 1700000000000),
('max_file_size_gb', 'SYSTEM', '20', 1700000000000),
('default_theme', 'SYSTEM', 'dark', 1700000000000);

-- 5. Du lieu ban be mau khoi tao (Seed Friends)
INSERT OR REPLACE INTO friends (user_id, friend_id, status, created_at) VALUES
('alice', 'bob', 'ACCEPTED', 1700000000000),
('bob', 'alice', 'ACCEPTED', 1700000000000),
('alice', 'charlie', 'ACCEPTED', 1700000000000),
('charlie', 'alice', 'ACCEPTED', 1700000000000),
('tan kiet', 'giasuo', 'ACCEPTED', 1700000000000),
('giasuo', 'tan kiet', 'ACCEPTED', 1700000000000);
