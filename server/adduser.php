<?php
/**
 * 快速建号：php adduser.php <用户名> <密码>
 * 浏览器访问用法：adduser.php?u=用户名&p=密码（用完请删除本文件）
 */
require_once __DIR__ . '/config.php';

$isCli = PHP_SAPI === 'cli';
if ($isCli) {
    [$u, $p] = [$argv[1] ?? '', $argv[2] ?? ''];
} else {
    $u = $_GET['u'] ?? '';
    $p = $_GET['p'] ?? '';
}

function reply(string $msg, bool $ok): void {
    if (PHP_SAPI === 'cli') { echo $msg . PHP_EOL; }
    else { echo '<meta charset="utf-8">' . htmlspecialchars($msg); }
    exit($ok ? 0 : 1);
}

if ($u === '' || $p === '') reply("用法: " . ($isCli ? 'php adduser.php 用户名 密码' : 'adduser.php?u=用户名&p=密码'), false);
if (!preg_match('/^[A-Za-z0-9_]{3,32}$/', $u)) reply('用户名需 3-32 位字母数字下划线', false);
if (strlen($p) < 4) reply('密码至少 4 位', false);

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
try {
    $db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS, ONYX_DB_NAME);
    $db->set_charset('utf8mb4');

    // 表不存在时顺手建好（等于代替 onyx.sql）
    $db->query("CREATE TABLE IF NOT EXISTS Onyx_users (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        status TINYINT NOT NULL DEFAULT 1,
        banned_reason VARCHAR(255) DEFAULT NULL,
        bound_device VARCHAR(64) DEFAULT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_device (bound_device)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $db->query("CREATE TABLE IF NOT EXISTS Onyx_login_logs (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        username VARCHAR(64) NOT NULL,
        ip VARCHAR(45) NOT NULL,
        device_id VARCHAR(64) NOT NULL,
        result VARCHAR(32) NOT NULL,
        login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_user (user_id), INDEX idx_time (login_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $db->query("CREATE TABLE IF NOT EXISTS Onyx_sessions (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        token CHAR(64) NOT NULL UNIQUE,
        device_id VARCHAR(64) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        expires_at DATETIME NOT NULL,
        INDEX idx_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $stmt = $db->prepare('INSERT INTO Onyx_users (username, password_hash) VALUES (?, ?)');
    $hash = password_hash($p, PASSWORD_DEFAULT);
    $stmt->bind_param('ss', $u, $hash);
    $stmt->execute();
    reply("✅ 账号 {$u} 创建成功", true);
} catch (mysqli_sql_exception $e) {
    if ((int)$e->getCode() === 1062) reply("❌ 用户名 {$u} 已存在", false);
    reply('❌ 数据库错误：' . $e->getMessage(), false);
}
