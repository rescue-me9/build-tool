<?php
/**
 * Onyx_build 数据库自动安装脚本
 * ------------------------------------------------------------------
 * 用法：
 *   CLI    ：php install.php [用户名] [密码]
 *   浏览器 ：install.php?key=你的ONYX_ADMIN_KEY[&user=用户名&pass=密码]
 *
 * 流程：连接 MySQL → 自动建库 → 自动建表 → 校验表 → (可选) 建首个账号
 * 安装完成后请务必删除本文件（以及 import.php / adduser.php）。
 */

$isCli = PHP_SAPI === 'cli';

function h(string $s): string {
    return htmlspecialchars($s, ENT_QUOTES, 'UTF-8');
}

/* ================= 0. 读取配置 ================= */
if (!is_file(__DIR__ . '/config.php')) {
    $msg = '找不到 config.php，请先创建配置文件后再运行安装。';
    if ($isCli) { fwrite(STDERR, "❌ {$msg}\n"); exit(1); }
    http_response_code(500);
    exit('<meta charset="utf-8"><p style="font:14px/1.7 -apple-system,sans-serif;padding:24px">❌ ' . h($msg) . '</p>');
}
require_once __DIR__ . '/config.php';

/* ================= 1. 浏览器模式鉴权 ================= */
if (!$isCli) {
    $inputKey = (string)($_GET['key'] ?? '');
    if ($inputKey === '' || !hash_equals(ONYX_ADMIN_KEY, $inputKey)) {
        http_response_code(403);
        exit('<meta charset="utf-8"><p style="font:14px/1.7 -apple-system,sans-serif;padding:24px">'
            . '🔒 禁止访问：请使用 <code>install.php?key=你的管理密钥</code></p>');
    }
}

$lines = [];   // 每条: [级别 ok|warn|err, 文字]
$fatal = false;

/* ================= 2. 开始安装 ================= */
try {
    mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);

    // 2.1 连接 MySQL 服务器（先不选库，方便自动建库）
    $db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS);
    $db->set_charset('utf8mb4');
    $lines[] = ['ok', '已连接 MySQL 服务器 ' . ONYX_DB_HOST];

    // 2.2 数据库不存在则自动创建
    $dbName = ONYX_DB_NAME;
    $stmt = $db->prepare('SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?');
    $stmt->bind_param('s', $dbName);
    $stmt->execute();
    $dbExists = (bool)$stmt->get_result()->fetch_row();
    $stmt->close();

    if ($dbExists) {
        $lines[] = ['ok', "数据库 {$dbName} 已存在"];
    } else {
        $safeName = str_replace('`', '``', $dbName);
        $db->query("CREATE DATABASE `{$safeName}` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        $lines[] = ['ok', "数据库 {$dbName} 不存在，已自动创建"];
    }

    // 2.3 选中数据库
    $db->select_db($dbName);
    $lines[] = ['ok', "已选中数据库 {$dbName}"];

    // 2.4 建表：优先 onyx.sql，再用内置语句兜底补齐
    $builtinSql = <<<'SQL'
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
SQL;

    $scripts = [];
    $sqlFile = __DIR__ . '/onyx.sql';
    if (is_file($sqlFile)) {
        $sql = trim((string)file_get_contents($sqlFile));
        if ($sql !== '') {
            $scripts[] = $sql;
            $lines[] = ['ok', '已读取 onyx.sql'];
        }
    } else {
        $lines[] = ['warn', '未找到 onyx.sql，仅使用内置建表语句'];
    }
    $scripts[] = $builtinSql;

    foreach ($scripts as $sql) {
        $db->multi_query($sql);
        while ($db->more_results() && $db->next_result()) { /* 清空结果集 */ }
    }
    $lines[] = ['ok', '数据表结构创建完成'];

    // 2.5 校验表是否都在
    $required = ['Onyx_users', 'Onyx_login_logs', 'Onyx_sessions'];
    $missing = [];
    foreach ($required as $t) {
        $r = $db->query("SHOW TABLES LIKE '{$t}'");
        if (!$r || $r->num_rows === 0) $missing[] = $t;
    }
    if ($missing) {
        $fatal = true;
        $lines[] = ['err', '以下数据表创建失败：' . implode('、', $missing)];
    } else {
        $lines[] = ['ok', '已校验：' . implode('、', $required) . ' 全部就绪'];
    }

    // 2.6 可选：顺便创建第一个账号
    $newUser = $isCli ? (string)($argv[1] ?? '') : (string)($_GET['user'] ?? '');
    $newPass = $isCli ? (string)($argv[2] ?? '') : (string)($_GET['pass'] ?? '');
    if ($newUser !== '' || $newPass !== '') {
        if (!preg_match('/^[A-Za-z0-9_]{3,32}$/', $newUser)) {
            $lines[] = ['warn', '用户名需 3-32 位字母数字下划线，已跳过建号'];
        } elseif (strlen($newPass) < 4) {
            $lines[] = ['warn', '密码至少 4 位，已跳过建号'];
        } else {
            $stmt = $db->prepare('INSERT INTO Onyx_users (username, password_hash) VALUES (?, ?)');
            $hash = password_hash($newPass, PASSWORD_DEFAULT);
            $stmt->bind_param('ss', $newUser, $hash);
            try {
                $stmt->execute();
                $lines[] = ['ok', "已创建账号 {$newUser}"];
            } catch (mysqli_sql_exception $ex) {
                if ((int)$ex->getCode() === 1062) {
                    $lines[] = ['warn', "账号 {$newUser} 已存在，跳过创建"];
                } else {
                    $lines[] = ['err', '创建账号失败：' . $ex->getMessage()];
                }
            }
        }
    }
} catch (Throwable $e) {
    $fatal = true;
    $lines[] = ['err', '安装中断：' . $e->getMessage()];

    $m = $e->getMessage();
    if (stripos($m, 'Access denied') !== false) {
        $lines[] = ['warn', '请检查 config.php 里的 ONYX_DB_USER / ONYX_DB_PASS 是否正确'];
    } elseif (stripos($m, 'CREATE DATABASE') !== false || stripos($m, 'command denied') !== false) {
        $lines[] = ['warn', '数据库账号可能没有建库权限，请先手动创建数据库 ' . (defined('ONYX_DB_NAME') ? ONYX_DB_NAME : '')];
    }
}

/* ================= 3. 输出结果 ================= */
if ($isCli) {
    $icon = ['ok' => '✅', 'warn' => '⚠️ ', 'err' => '❌'];
    foreach ($lines as [$lvl, $txt]) {
        echo $icon[$lvl] . ' ' . $txt . PHP_EOL;
    }
    echo PHP_EOL;
    if ($fatal) {
        echo '❌ 安装未完成，请根据上面提示处理后重试。' . PHP_EOL;
        exit(1);
    }
    echo '🎉 安装完成！建议立即删除 install.php / import.php / adduser.php。' . PHP_EOL;
    exit(0);
}

$head = $fatal ? '安装失败' : '安装完成';

echo '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">'
   . '<meta name="viewport" content="width=device-width,initial-scale=1">'
   . '<title>Onyx_build · 安装</title><style>
body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
     background:linear-gradient(160deg,#F3F7FD,#E4EEFC);min-height:100vh;margin:0;padding:24px;color:#1B2735;}
.card{max-width:680px;margin:6vh auto;background:#fff;border:1px solid #D8E2F0;border-radius:16px;
      padding:24px;box-shadow:0 4px 18px rgba(46,111,216,.07);}
.brand{color:#2E6FD8;font-weight:700;letter-spacing:3px;font-size:12px;}
h1{font-size:20px;margin:6px 0 16px;}
ul{padding:0;margin:0 0 14px;}
li{list-style:none;padding:9px 12px;border-radius:10px;font-size:14px;margin-bottom:8px;line-height:1.6;}
li.ok{background:#E4F1EA;color:#2F7D5B;}
li.warn{background:#FDF6E3;color:#8A6D1F;}
li.err{background:#FBEAE8;color:#C4453A;}
.tip{font-size:13px;color:#6B7A90;line-height:1.9;border-top:1px solid #EDF3FC;padding-top:14px;}
code{background:#EDF3FC;padding:1px 6px;border-radius:6px;font-size:13px;}
</style></head><body><div class="card"><div class="brand">ONYX_BUILD</div><h1>'
   . h($head) . '</h1><ul>';

foreach ($lines as [$lvl, $txt]) {
    echo '<li class="' . $lvl . '">' . h($txt) . '</li>';
}

echo '</ul><div class="tip">';
if ($fatal) {
    echo '请根据上方错误提示处理后重新访问本页面。';
} else {
    echo '安装已完成。出于安全考虑，请立即删除 <code>install.php</code>、<code>import.php</code>、<code>adduser.php</code>。<br>'
       . '管理后台入口：<code>admin.php</code>';
}
echo '</div></div></body></html>';