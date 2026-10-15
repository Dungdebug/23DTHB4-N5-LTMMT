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

CREATE TABLE IF NOT EXISTS friends (
    user_id VARCHAR(50) NOT NULL,
    friend_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACCEPTED',
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(username) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_friends_friend ON friends(friend_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

INSERT OR REPLACE INTO users (username, display_name, salt, password_hash, status, created_at) VALUES
('alice', 'Alice Wonder', '0463c8b2980431f091c54327c48b18da', 'b058eb923047cae032df462e5a4d7b14be91fe1ef9beb04ba2f3f43bd1335e5b', 'ONLINE', 1700000000000),
('bob', 'Bob Builder', 'f0ac73816e3f53a0b6e1adad2ad103a1', '893743b42297b76fe666576feaad01578b2788d0ad4d9f5261aa7657c4caa0ff', 'ONLINE', 1700000000000),
('charlie', 'Charlie Chaplin', '01161e79f149945bc69b9ee0b735e931', '5095eb2b6fcfa90c0fc1a8c6f2f7b85453bc9027894f1c76e8bd3fbe0e6147d7', 'OFFLINE', 1700000000000),
('admin', 'Administrator', 'f104475e8b0fad1eb8a51f64d9b940e4', '0a2101d2543bbc87158dfc4e52c027752986a928e007662b23e9d392d0247866', 'ONLINE', 1700000000000),
('tan kiet', 'Tan Kiet', '431d206cdd998f7d1179b01d499729f3', '17cd01ede64e556bb24eb932ef247af12fe07a6bbc78d7f338b99320edde0749', 'OFFLINE', 1700000000000),
('giasuo', 'Giasuo Master', '1dbf6421f1eee8c1bb96c07b3b0c2b17', '43b401b6cd5ae953b2c976900fb8bf4f7f24a54402f9af7c13e163179cc347c0', 'OFFLINE', 1700000000000);

INSERT OR REPLACE INTO friends (user_id, friend_id, status, created_at) VALUES
('alice', 'bob', 'ACCEPTED', 1700000000000),
('bob', 'alice', 'ACCEPTED', 1700000000000),
('alice', 'charlie', 'ACCEPTED', 1700000000000),
('charlie', 'alice', 'ACCEPTED', 1700000000000),
('tan kiet', 'giasuo', 'ACCEPTED', 1700000000000),
('giasuo', 'tan kiet', 'ACCEPTED', 1700000000000);

SELECT username, display_name, salt, password_hash, status FROM users WHERE username = 'alice';
SELECT f.friend_id, u.display_name, u.status, u.last_ip, f.created_at FROM friends f JOIN users u ON f.friend_id = u.username WHERE f.user_id = 'alice' AND f.status = 'ACCEPTED';
SELECT username, display_name, status FROM users WHERE status = 'ONLINE';
