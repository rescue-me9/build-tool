<?php
require_once __DIR__ . '/config.php';

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);

try {
    $db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS);
    $db->set_charset('utf8mb4');

    $db->query('CREATE DATABASE IF NOT EXISTS `' . ONYX_DB_NAME . '` DEFAULT CHARACTER SET utf8mb4');
    $db->select_db(ONYX_DB_NAME);

    $sql = array();

    $sql[] = "CREATE TABLE IF NOT EXISTS Onyx_users (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        status TINYINT NOT NULL DEFAULT 1,
        banned_reason VARCHAR(255) DEFAULT NULL,
        bound_device VARCHAR(64) DEFAULT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_device (bound_device)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    $sql[] = "CREATE TABLE IF NOT EXISTS Onyx_login_logs (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        username VARCHAR(64) NOT NULL,
        ip VARCHAR(45) NOT NULL,
        device_id VARCHAR(64) NOT NULL,
        result VARCHAR(32) NOT NULL,
        login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_user (user_id),
        INDEX idx_time (login_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    $sql[] = "CREATE TABLE IF NOT EXISTS Onyx_sessions (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id INT UNSIGNED NOT NULL,
        token CHAR(64) NOT NULL UNIQUE,
        device_id VARCHAR(64) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        expires_at DATETIME NOT NULL,
        INDEX idx_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    foreach ($sql as $q) {
        $db->query($q);
    }

    echo '<meta charset="utf-8"><pre>✅ 安装完成！</pre>';
    echo '<pre>数据库: ' . ONYX_DB_NAME . "\n";
    echo "已创建表:\n";

    $check = array('Onyx_users', 'Onyx_login_logs', 'Onyx_sessions');
    foreach ($check as $t) {
        $r = $db->query("SHOW TABLES LIKE '{$t}'");
        echo (($r && $r->num_rows) ? '  ✅ ' : '  ❌ ') . $t . "\n";
    }

    echo "\n⚠️  请立即删除本文件（78.php）！</pre>";

} catch (Throwable $e) {
    echo '<meta charset="utf-8"><pre>❌ 安装失败: ' . htmlspecialchars($e->getMessage()) . '</pre>';
}