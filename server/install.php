<?php
/**
 * Onyx_build 一键安装向导。部署后浏览器打开本文件完成安装，装完务必删除。
 */

$SELF = basename(__FILE__);
$errors = [];
$done = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $host = trim($_POST['db_host'] ?? 'localhost');
    $name = trim($_POST['db_name'] ?? '');
    $user = trim($_POST['db_user'] ?? '');
    $pass = $_POST['db_pass'] ?? '';
    $adminKey = $_POST['admin_key'] ?? '';
    $signSecret = $_POST['sign_secret'] ?? '';

    if ($name === '' || $user === '' || $adminKey === '' || $signSecret === '') {
        $errors[] = '除主机外所有字段都必须填写';
    }
    if (strlen($signSecret) < 16) $errors[] = '请求签名盐至少 16 位';
    if (strlen($adminKey) < 16) $errors[] = '管理密钥至少 16 位';

    if (!$errors) {
        mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
        try {
            $db = new mysqli($host, $user, $pass, $name);
            $db->set_charset('utf8mb4');

            $tables = [
                "CREATE TABLE IF NOT EXISTS Onyx_users (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    banned_reason VARCHAR(255) DEFAULT NULL,
                    bound_device VARCHAR(64) DEFAULT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_device (bound_device)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS Onyx_login_logs (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id INT UNSIGNED NOT NULL,
                    username VARCHAR(64) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    device_id VARCHAR(64) NOT NULL,
                    result VARCHAR(32) NOT NULL,
                    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_user (user_id),
                    INDEX idx_time (login_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                "CREATE TABLE IF NOT EXISTS Onyx_sessions (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id INT UNSIGNED NOT NULL,
                    token CHAR(64) NOT NULL UNIQUE,
                    device_id VARCHAR(64) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    expires_at DATETIME NOT NULL,
                    INDEX idx_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            ];
            foreach ($tables as $sql) $db->query($sql);

            $config = "<?php\n"
                . "// 由 install.php 自动生成于 " . date('Y-m-d H:i:s') . "\n"
                . "define('ONYX_DB_HOST', " . var_export($host, true) . ");\n"
                . "define('ONYX_DB_NAME', " . var_export($name, true) . ");\n"
                . "define('ONYX_DB_USER', " . var_export($user, true) . ");\n"
                . "define('ONYX_DB_PASS', " . var_export($pass, true) . ");\n"
                . "define('ONYX_ADMIN_KEY', " . var_export($adminKey, true) . ");\n"
                . "define('ONYX_SIGN_SECRET', " . var_export($signSecret, true) . ");\n"
                . "define('ONYX_SESSION_TTL', 86400 * 7);\n";
            file_put_contents(__DIR__ . '/config.php', $config);

            // 直接创建管理员第一个账号
            $stmt = $db->prepare('INSERT IGNORE INTO Onyx_users (username, password_hash) VALUES (?, ?)');
            $firstUser = trim($_POST['first_user'] ?? '');
            $firstPass = $_POST['first_pass'] ?? '';
            if ($firstUser !== '' && $firstPass !== '' && preg_match('/^[A-Za-z0-9_]{3,32}$/', $firstUser)) {
                $hash = password_hash($firstPass, PASSWORD_DEFAULT);
                $stmt->bind_param('ss', $firstUser, $hash);
                $stmt->execute();
            }
            $done = true;
        } catch (Throwable $e) {
            $errors[] = '数据库连接失败：' . $e->getMessage();
        }
    }
}
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Onyx_build · 安装向导</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
       background: linear-gradient(160deg, #F3F7FD 0%, #E4EEFC 100%);
       min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; }
.card { background: #fff; border: 1px solid #D8E2F0; border-radius: 20px;
        padding: 32px; width: 100%; max-width: 440px; box-shadow: 0 8px 30px rgba(46,111,216,.10); }
.brand { color: #2E6FD8; font-weight: 700; letter-spacing: 3px; font-size: 12px; }
h1 { color: #1B2735; font-size: 22px; margin: 6px 0 4px; }
.sub { color: #6B7A90; font-size: 13px; margin-bottom: 22px; }
label { display: block; color: #1B2735; font-size: 13px; font-weight: 600; margin: 12px 0 5px; }
input { width: 100%; padding: 11px 13px; border: 1px solid #D8E2F0; border-radius: 11px;
        font-size: 14px; background: #F6F9FE; color: #1B2735; outline: none; }
input:focus { border-color: #2E6FD8; background: #fff; }
button { width: 100%; margin-top: 22px; padding: 13px; border: 0; border-radius: 12px;
         background: #2E6FD8; color: #fff; font-size: 15px; font-weight: 700; cursor: pointer; }
button:hover { background: #1F4FA8; }
.error { background: #FBEAE8; color: #C4453A; border-radius: 10px; padding: 10px 13px;
         font-size: 13px; margin-bottom: 10px; }
.ok { background: #E4F1EA; color: #2F7D5B; border-radius: 10px; padding: 14px 16px; font-size: 13.5px; line-height: 1.7; }
.hint { color: #93A3BA; font-size: 12px; margin-top: 4px; }
.sep { border: 0; border-top: 1px solid #EDF3FC; margin: 20px 0 4px; }
.sect { color: #93A3BA; font-size: 12px; font-weight: 600; letter-spacing: 1px; margin-top: 14px; }
code { background: #EDF3FC; color: #1F4FA8; padding: 2px 6px; border-radius: 5px; font-size: 12px; }
</style>
</head>
<body>
<div class="card">
    <div class="brand">ONYX_BUILD</div>
    <h1>安装向导</h1>
<?php if ($done): ?>
    <div class="sub">安装完成</div>
    <div class="ok">
        ✅ 数据表已创建，<code>config.php</code> 已生成。<br><br>
        <b>接下来请立即做两件事：</b><br>
        1. 删除服务器上的 <code><?= htmlspecialchars($SELF) ?></code>（安全要求）<br>
        2. 打开 <a href="admin.php">admin.php</a> 进入管理后台
    </div>
<?php else: ?>
    <div class="sub">填入数据库信息，自动建表并生成配置</div>
    <?php foreach ($errors as $e): ?><div class="error"><?= htmlspecialchars($e) ?></div><?php endforeach; ?>
    <form method="post">
        <label>数据库主机</label>
        <input name="db_host" value="<?= htmlspecialchars($_POST['db_host'] ?? 'localhost') ?>">
        <label>数据库名</label>
        <input name="db_name" value="<?= htmlspecialchars($_POST['db_name'] ?? 'lvbkblij') ?>" required>
        <label>数据库用户</label>
        <input name="db_user" value="<?= htmlspecialchars($_POST['db_user'] ?? 'lvbkblij') ?>" required>
        <label>数据库密码</label>
        <input name="db_pass" type="password" value="<?= htmlspecialchars($_POST['db_pass'] ?? '') ?>">
        <hr class="sep">
        <div class="sect">安全密钥</div>
        <label>管理密钥（ADMIN_KEY，后台登录用）</label>
        <input name="admin_key" required>
        <label>请求签名盐（SIGN_SECRET，须与 App 内一致）</label>
        <input name="sign_secret" required>
        <hr class="sep">
        <div class="sect">初始账号（可选）</div>
        <label>用户名</label>
        <input name="first_user" placeholder="如 test123">
        <label>密码</label>
        <input name="first_pass" type="password">
        <button>开始安装</button>
    </form>
<?php endif; ?>
</div>
</body>
</html>
