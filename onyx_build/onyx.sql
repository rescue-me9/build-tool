-- Onyx_build 授权系统数据库结构
-- 表前缀: Onyx_

CREATE TABLE IF NOT EXISTS Onyx_users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常 0=封禁',
    banned_reason VARCHAR(255) DEFAULT NULL,
    bound_device VARCHAR(64) DEFAULT NULL COMMENT '首次登录绑定的设备码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device (bound_device)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS Onyx_login_logs (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    username VARCHAR(64) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL COMMENT 'success/device_mismatch/banned/bad_credentials',
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS Onyx_sessions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    token CHAR(64) NOT NULL UNIQUE,
    device_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
