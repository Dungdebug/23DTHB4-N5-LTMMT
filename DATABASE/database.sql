CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    last_ip VARCHAR(45) NULL,
    last_port INT NULL,
    created_at BIGINT NOT NULL,
    last_login BIGINT NULL
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    room_id VARCHAR(50) NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    room_key_aes VARCHAR(64) NULL,
    created_by VARCHAR(50) NULL,
    is_voice_enabled BOOLEAN DEFAULT 1,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (created_by) REFERENCES users(username) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS room_members (
    room_id VARCHAR(50) NOT NULL,
    username VARCHAR(50) NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    is_muted BOOLEAN DEFAULT 0,
    is_deafened BOOLEAN DEFAULT 0,
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (room_id, username),
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NULL,
    room_id VARCHAR(50) NULL,
    content TEXT NOT NULL,
    is_encrypted BOOLEAN DEFAULT 0,
    iv VARCHAR(32) NULL,
    timestamp BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'SENT',
    FOREIGN KEY (sender) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (receiver) REFERENCES users(username) ON DELETE SET NULL,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS file_transfers (
    transfer_id VARCHAR(64) NOT NULL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NULL,
    offset_bytes BIGINT DEFAULT 0,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NULL,
    room_id VARCHAR(50) NULL,
    is_encrypted BOOLEAN DEFAULT 0,
    aes_key VARCHAR(64) NULL,
    base_iv VARCHAR(32) NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT NULL,
    created_at BIGINT NOT NULL,
    completed_at BIGINT NULL,
    FOREIGN KEY (sender) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (receiver) REFERENCES users(username) ON DELETE SET NULL,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS voice_sessions (
    session_id VARCHAR(64) NOT NULL PRIMARY KEY,
    room_id VARCHAR(50) NOT NULL,
    caller_username VARCHAR(50) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT NULL,
    total_packets BIGINT DEFAULT 0,
    total_bytes BIGINT DEFAULT 0,
    FOREIGN KEY (room_id) REFERENCES chat_rooms(room_id) ON DELETE CASCADE,
    FOREIGN KEY (caller_username) REFERENCES users(username) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS server_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type VARCHAR(50) NOT NULL,
    source_user VARCHAR(50) NULL,
    source_ip VARCHAR(45) NULL,
    message TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS app_settings (
    config_key VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    config_value TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (config_key, username)
);

CREATE TABLE IF NOT EXISTS friends (
    user_id VARCHAR(50) NOT NULL,
    friend_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACCEPTED',
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(username) ON DELETE CASCADE
);

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

INSERT OR REPLACE INTO users (username, display_name, salt, password_hash, status, created_at) VALUES
('alice', 'Alice Wonder', '0463c8b2980431f091c54327c48b18da', 'b058eb923047cae032df462e5a4d7b14be91fe1ef9beb04ba2f3f43bd1335e5b', 'OFFLINE', 1700000000000),
('bob', 'Bob Builder', 'f0ac73816e3f53a0b6e1adad2ad103a1', '893743b42297b76fe666576feaad01578b2788d0ad4d9f5261aa7657c4caa0ff', 'OFFLINE', 1700000000000),
('charlie', 'Charlie Chaplin', '01161e79f149945bc69b9ee0b735e931', '5095eb2b6fcfa90c0fc1a8c6f2f7b85453bc9027894f1c76e8bd3fbe0e6147d7', 'OFFLINE', 1700000000000),
('admin', 'Administrator', 'f104475e8b0fad1eb8a51f64d9b940e4', '0a2101d2543bbc87158dfc4e52c027752986a928e007662b23e9d392d0247866', 'OFFLINE', 1700000000000),
('tan kiet', 'Tan Kiet', '431d206cdd998f7d1179b01d499729f3', '17cd01ede64e556bb24eb932ef247af12fe07a6bbc78d7f338b99320edde0749', 'OFFLINE', 1700000000000),
('giasuo', 'Giasuo Master', '1dbf6421f1eee8c1bb96c07b3b0c2b17', '43b401b6cd5ae953b2c976900fb8bf4f7f24a54402f9af7c13e163179cc347c0', 'OFFLINE', 1700000000000);

INSERT OR REPLACE INTO chat_rooms (room_id, display_name, created_by, is_voice_enabled, created_at) VALUES
('Lobby', 'Phòng Chung (Lobby)', 'admin', 1, 1700000000000),
('General', 'Thảo Luận Chung (General)', 'admin', 1, 1700000000000),
('Voice_Lounge', '🔊 Kênh Thoại Voice Lounge', 'admin', 1, 1700000000000),
('MMT_NHOM05', 'Nhóm 05 MMT', 'admin', 1, 1700000000000);

INSERT OR REPLACE INTO room_members (room_id, username, role, joined_at) VALUES
('Lobby', 'admin', 'ADMIN', 1700000000000),
('General', 'admin', 'ADMIN', 1700000000000),
('Voice_Lounge', 'admin', 'ADMIN', 1700000000000),
('MMT_NHOM05', 'admin', 'ADMIN', 1700000000000);

INSERT OR REPLACE INTO app_settings (config_key, username, config_value, updated_at) VALUES
('server_tcp_control_port', 'SYSTEM', '8888', 1700000000000),
('server_tcp_file_port', 'SYSTEM', '8889', 1700000000000),
('server_udp_voice_port', 'SYSTEM', '8890', 1700000000000),
('max_file_size_gb', 'SYSTEM', '20', 1700000000000),
('default_theme', 'SYSTEM', 'dark', 1700000000000);

INSERT OR REPLACE INTO friends (user_id, friend_id, status, created_at) VALUES
('alice', 'bob', 'ACCEPTED', 1700000000000),
('bob', 'alice', 'ACCEPTED', 1700000000000),
('alice', 'charlie', 'ACCEPTED', 1700000000000),
('charlie', 'alice', 'ACCEPTED', 1700000000000),
('tan kiet', 'giasuo', 'ACCEPTED', 1700000000000),
('giasuo', 'tan kiet', 'ACCEPTED', 1700000000000);
