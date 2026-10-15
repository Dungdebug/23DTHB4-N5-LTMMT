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
    completed_at BIGINT NULL
);

CREATE TABLE IF NOT EXISTS voice_sessions (
    session_id VARCHAR(64) NOT NULL PRIMARY KEY,
    room_id VARCHAR(50) NOT NULL,
    caller_username VARCHAR(50) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT NULL,
    total_packets BIGINT DEFAULT 0,
    total_bytes BIGINT DEFAULT 0
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

CREATE INDEX IF NOT EXISTS idx_transfers_sender ON file_transfers(sender);
CREATE INDEX IF NOT EXISTS idx_transfers_receiver ON file_transfers(receiver);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON file_transfers(status);
CREATE INDEX IF NOT EXISTS idx_transfers_created ON file_transfers(created_at);
CREATE INDEX IF NOT EXISTS idx_voice_room ON voice_sessions(room_id);
CREATE INDEX IF NOT EXISTS idx_voice_caller ON voice_sessions(caller_username);
CREATE INDEX IF NOT EXISTS idx_logs_type ON server_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_logs_created ON server_logs(created_at);

INSERT OR REPLACE INTO file_transfers (transfer_id, file_name, file_size, sha256, offset_bytes, sender, receiver, room_id, is_encrypted, status, created_at, completed_at) VALUES
('tx-1001-setup-iso', 'Ubuntu_24_Server.iso', 2684354560, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 2684354560, 'alice', 'bob', NULL, 1, 'COMPLETED', 1700000010000, 1700000060000),
('tx-1002-big-backup', 'Database_Backup_20GB.tar.gz', 21474836480, 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', 10737418240, 'bob', 'alice', NULL, 1, 'TRANSFERRING', 1700000100000, NULL),
('tx-1003-group-slide', 'BaoCao_MonHoc_MMT.pdf', 15728640, '6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b', 15728640, 'tan kiet', NULL, 'MMT_NHOM05', 0, 'COMPLETED', 1700000200000, 1700000205000),
('tx-1004-interrupted', 'Large_Dataset_Video.mp4', 5368709120, '12c3f8e56b4a2d8e09f7a1c4b3d2e1f089a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3', 1048576000, 'charlie', 'admin', NULL, 1, 'FAILED', 1700000210000, NULL);

INSERT OR REPLACE INTO voice_sessions (session_id, room_id, caller_username, started_at, ended_at, total_packets, total_bytes) VALUES
('voice-s-001', 'Voice_Lounge', 'alice', 1700000300000, 1700000900000, 30000, 38400000),
('voice-s-002', 'MMT_NHOM05', 'tan kiet', 1700001000000, 1700001600000, 25000, 32000000),
('voice-s-003', 'Lobby', 'admin', 1700002000000, 1700002300000, 15000, 19200000);

INSERT OR REPLACE INTO server_logs (event_type, source_user, source_ip, message, created_at) VALUES
('AUTH', 'alice', '10.147.19.101', 'Nguoi dung alice ket noi qua ZeroTier port 8888', 1700000001000),
('FILE_RELAY', 'alice', '10.147.19.101', 'Khoi tao relay file Ubuntu_24_Server.iso (2.5 GiB) toi bob qua port 8889', 1700000010000),
('FILE_RELAY', 'alice', '10.147.19.101', 'Truyen thanh cong 2.5 GiB. Hash SHA-256 hop le 100%', 1700000060000),
('VOICE', 'alice', '10.147.19.101', 'Khoi tao kenh dam thoai UDP Voice_Lounge qua port 8890', 1700000300000),
('ERROR', 'charlie', '10.147.19.103', 'Dut ket noi mang dot ngot trong phien tx-1004-interrupted', 1700000220000);

INSERT OR REPLACE INTO app_settings (config_key, username, config_value, updated_at) VALUES
('server_tcp_control_port', 'SYSTEM', '8888', 1700000000000),
('server_tcp_file_port', 'SYSTEM', '8889', 1700000000000),
('server_udp_voice_port', 'SYSTEM', '8890', 1700000000000),
('max_file_size_gb', 'SYSTEM', '20', 1700000000000),
('default_theme', 'SYSTEM', 'dark', 1700000000000);

SELECT transfer_id, file_name, file_size, offset_bytes, sha256 FROM file_transfers WHERE status = 'TRANSFERRING';
SELECT transfer_id, file_name, file_size, sender, receiver, status FROM file_transfers WHERE sender = 'alice' OR receiver = 'alice';
SELECT session_id, room_id, caller_username, started_at, total_packets, total_bytes FROM voice_sessions;
SELECT log_id, event_type, source_user, source_ip, message, created_at FROM server_logs ORDER BY created_at DESC;
SELECT config_key, config_value FROM app_settings WHERE username = 'SYSTEM';
