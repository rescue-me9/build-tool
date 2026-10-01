<?php
/**
 * Onyx_build 授权后端。部署到 https://pw.5w.pw/onyx/api.php
 * 数据库表前缀: Onyx_
 * 配置由 install.php 生成的 config.php 提供。
 */

if (!file_exists(__DIR__ . '/config.php')) {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => false, 'code' => 'not_installed', 'msg' => '后端未安装，请先运行 install.php']);
    exit;
}
require_once __DIR__ . '/config.php';
$DB_HOST = ONYX_DB_HOST;
$DB_NAME = ONYX_DB_NAME;
$DB_USER = ONYX_DB_USER;
$DB_PASS = ONYX_DB_PASS;
$ADMIN_KEY = ONYX_ADMIN_KEY;
$SIGN_SECRET = ONYX_SIGN_SECRET;
$SESSION_TTL = ONYX_SESSION_TTL;

header('Content-Type: application/json; charset=utf-8');

function out($ok, $code, $msg, $extra = []) {
    echo json_encode(array_merge(['ok' => $ok, 'code' => $code, 'msg' => $msg], $extra), JSON_UNESCAPED_UNICODE);
    exit;
}

function client_ip() {
    return $_SERVER['HTTP_CF_CONNECTING_IP']
        ?? $_SERVER['HTTP_X_FORWARDED_FOR'] ?? $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
}

function rate_limit(string $bucket, int $max, int $windowSeconds): void {
    $dir = sys_get_temp_dir() . '/onyx_rl';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    $file = $dir . '/' . hash('sha256', $bucket) . '.json';
    $now = time();
    $state = ['count' => 0, 'reset' => $now + $windowSeconds];
    if (is_file($file)) {
        $state = json_decode((string)file_get_contents($file), true) ?: $state;
        if ($now > (int)$state['reset']) $state = ['count' => 0, 'reset' => $now + $windowSeconds];
    }
    $state['count']++;
    file_put_contents($file, json_encode($state), LOCK_EX);
    if ($state['count'] > $max) {
        $retry = max(1, (int)$state['reset'] - $now);
        header("Retry-After: {$retry}");
        out(false, 'rate_limited', "请求过于频繁，请 {$retry} 秒后重试");
    }
}

function register_fail(string $bucket): void {
    $dir = sys_get_temp_dir() . '/onyx_fail';
    if (!is_dir($dir)) @mkdir($dir, 0700, true);
    $file = $dir . '/' . hash('sha256', $bucket) . '.json';
    $now = time();
    $state = is_file($file) ? (json_decode((string)file_get_contents($file), true) ?: []) : [];
    $state['fails'] = (int)($state['fails'] ?? 0) + 1;
    $state['until'] = $now + 120;
    file_put_contents($file, json_encode($state), LOCK_EX);
}

function check_locked(string $bucket): void {
    $file = sys_get_temp_dir() . '/onyx_fail/' . hash('sha256', $bucket) . '.json';
    if (!is_file($file)) return;
    $state = json_decode((string)file_get_contents($file), true);
    if ($state && ($state['fails'] ?? 0) >= 6 && time() < (int)($state['until'] ?? 0)) {
        $left = (int)$state['until'] - time();
        out(false, 'locked', "失败次数过多，请 " . max(1, $left) . " 秒后重试");
    }
}

function clear_fails(string $bucket): void {
    @unlink(sys_get_temp_dir() . '/onyx_fail/' . hash('sha256', $bucket) . '.json');
}

function body() {
    $raw = file_get_contents('php://input');
    $data = json_decode($raw, true);
    if (!is_array($data)) out(false, 'bad_request', '请求格式错误');
    return $data;
}

// 客户端请求签名: sha256(username + device_id + timestamp + SECRET)
// 时间戳偏差超过 10 分钟拒绝，防重放
function check_signature($data, $secret) {
    foreach (['username', 'device_id', 'timestamp', 'sign'] as $k) {
        if (!isset($data[$k]) || !is_string($data[$k]) || $data[$k] === '') {
            out(false, 'bad_request', '参数缺失');
        }
    }
    if (abs(time() - (int)$data['timestamp']) > 600) {
        out(false, 'bad_request', '请求已过期，请校准设备时间');
    }
    $expect = hash('sha256', $data['username'] . $data['device_id'] . $data['timestamp'] . $secret);
    if (!hash_equals($expect, $data['sign'])) {
        out(false, 'bad_sign', '签名校验失败');
    }
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$action = $_GET['action'] ?? '';

if ($method !== 'POST') out(false, 'bad_method', '仅支持 POST');

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
try {
    $db = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME);
    $db->set_charset('utf8mb4');
} catch (Throwable $e) {
    out(false, 'db_error', '服务暂时不可用');
}

function log_login(mysqli $db, int $uid, string $username, string $ip, string $device, string $result): void {
    $stmt = $db->prepare('INSERT INTO Onyx_login_logs (user_id, username, ip, device_id, result) VALUES (?,?,?,?,?)');
    $stmt->bind_param('issss', $uid, $username, $ip, $device, $result);
    $stmt->execute();
}

switch ($action) {
    // ================= 登录 =================
    case 'login': {
        rate_limit('login:' . client_ip(), 15, 60);
        $data = body();
        check_signature($data, $SIGN_SECRET);
        $username = $data['username'];
        $device = $data['device_id'];
        if (!isset($data['password']) || $data['password'] === '') out(false, 'bad_request', '参数缺失');
        $password = $data['password'];

        $failBucket = 'login:' . client_ip() . ':' . strtolower($username);
        check_locked($failBucket);

        $stmt = $db->prepare('SELECT id, password_hash, status, banned_reason, bound_device FROM Onyx_users WHERE username = ? LIMIT 1');
        $stmt->bind_param('s', $username);
        $stmt->execute();
        $user = $stmt->get_result()->fetch_assoc();

        if (!$user || !password_verify($password, $user['password_hash'])) {
            register_fail($failBucket);
            log_login($db, $user['id'] ?? 0, $username, client_ip(), $device, 'bad_credentials');
            out(false, 'bad_credentials', '账号或密码错误');
        }
        if ((int)$user['status'] === 0) {
            log_login($db, (int)$user['id'], $username, client_ip(), $device, 'banned');
            out(false, 'banned', '当前您已被封禁，请联系管理员解封');
        }
        if ($user['bound_device'] !== null && $user['bound_device'] !== $device) {
            log_login($db, (int)$user['id'], $username, client_ip(), $device, 'device_mismatch');
            out(false, 'device_mismatch', '设备码错误：该账号已绑定其他设备');
        }
        if ($user['bound_device'] === null) {
            $stmt = $db->prepare('UPDATE Onyx_users SET bound_device = ? WHERE id = ?');
            $stmt->bind_param('si', $device, $user['id']);
            $stmt->execute();
        }

        // 换发新会话并清理旧会话
        clear_fails($failBucket);
        $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE user_id = ?');
        $stmt->bind_param('i', $user['id']);
        $stmt->execute();

        $token = bin2hex(random_bytes(32));
        $stmt = $db->prepare('INSERT INTO Onyx_sessions (user_id, token, device_id, expires_at) VALUES (?,?,?, DATE_ADD(NOW(), INTERVAL ? SECOND))');
        $stmt->bind_param('issi', $user['id'], $token, $device, $SESSION_TTL);
        $stmt->execute();

        log_login($db, (int)$user['id'], $username, client_ip(), $device, 'success');
        out(true, 'ok', '登录成功', ['token' => $token]);
    }

    // ================= 会话校验（每次打开面板调用）=================
    case 'verify': {
        rate_limit('verify:' . client_ip(), 30, 60);
        $data = body();
        foreach (['token', 'device_id'] as $k) {
            if (!isset($data[$k]) || !is_string($data[$k]) || $data[$k] === '') out(false, 'bad_request', '参数缺失');
        }
        $stmt = $db->prepare(
            'SELECT s.id, u.id AS uid, u.username, u.status, u.banned_reason, s.device_id
             FROM Onyx_sessions s JOIN Onyx_users u ON u.id = s.user_id
             WHERE s.token = ? AND s.expires_at > NOW() LIMIT 1'
        );
        $stmt->bind_param('s', $data['token']);
        $stmt->execute();
        $row = $stmt->get_result()->fetch_assoc();
        if (!$row) out(false, 'expired', '登录已过期，请重新登录');
        if ((int)$row['status'] === 0) {
            $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE user_id = ?');
            $stmt->bind_param('i', $row['uid']);
            $stmt->execute();
            out(false, 'banned', '当前您已被封禁，请联系管理员解封');
        }
        if ($row['device_id'] !== $data['device_id']) out(false, 'device_mismatch', '设备码错误：请在本绑定设备上使用');
        out(true, 'ok', '已登录', ['username' => $row['username']]);
    }

    // ================= 登出 =================
    case 'logout': {
        $data = body();
        if (!isset($data['token'])) out(false, 'bad_request', '参数缺失');
        $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE token = ?');
        $stmt->bind_param('s', $data['token']);
        $stmt->execute();
        out(true, 'ok', '已退出');
    }

    // ================= 管理：建号 =================
    case 'admin_create': {
        $data = body();
        if (($data['admin_key'] ?? '') !== $ADMIN_KEY) out(false, 'forbidden', '禁止访问');
        foreach (['username', 'password'] as $k) {
            if (!isset($data[$k]) || !is_string($data[$k]) || $data[$k] === '') out(false, 'bad_request', '参数缺失');
        }
        if (!preg_match('/^[A-Za-z0-9_]{3,32}$/', $data['username'])) out(false, 'bad_request', '用户名仅限 3-32 位字母数字下划线');
        $hash = password_hash($data['password'], PASSWORD_DEFAULT);
        $stmt = $db->prepare('INSERT INTO Onyx_users (username, password_hash) VALUES (?, ?)');
        try {
            $stmt->bind_param('ss', $data['username'], $hash);
            $stmt->execute();
        } catch (Throwable $e) {
            out(false, 'duplicate', '用户名已存在');
        }
        out(true, 'ok', '创建成功');
    }

    // ================= 管理：封禁/解封 =================
    case 'admin_ban': {
        $data = body();
        if (($data['admin_key'] ?? '') !== $ADMIN_KEY) out(false, 'forbidden', '禁止访问');
        if (!isset($data['username'])) out(false, 'bad_request', '参数缺失');
        $ban = !empty($data['ban']);
        $reason = $ban ? (string)($data['reason'] ?? '违规使用') : null;
        $stmt = $db->prepare('UPDATE Onyx_users SET status = ?, banned_reason = ? WHERE username = ?');
        $status = $ban ? 0 : 1;
        $stmt->bind_param('iss', $status, $reason, $data['username']);
        $stmt->execute();
        if ($ban) {
            $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE user_id = (SELECT id FROM Onyx_users WHERE username = ?)');
            $stmt->bind_param('s', $data['username']);
            $stmt->execute();
        }
        out(true, 'ok', $ban ? '已封禁' : '已解封');
    }

    // ================= 管理：解绑设备 =================
    case 'admin_unbind': {
        $data = body();
        if (($data['admin_key'] ?? '') !== $ADMIN_KEY) out(false, 'forbidden', '禁止访问');
        if (!isset($data['username'])) out(false, 'bad_request', '参数缺失');
        $stmt = $db->prepare('UPDATE Onyx_users SET bound_device = NULL WHERE username = ?');
        $stmt->bind_param('s', $data['username']);
        $stmt->execute();
        out(true, 'ok', '已解绑设备');
    }

    // ================= 管理：查询登录记录 =================
    case 'admin_logs': {
        $data = body();
        if (($data['admin_key'] ?? '') !== $ADMIN_KEY) out(false, 'forbidden', '禁止访问');
        $limit = min((int)($data['limit'] ?? 50), 200);
        $res = $db->query("SELECT username, ip, device_id, result, login_time FROM Onyx_login_logs ORDER BY id DESC LIMIT $limit");
        $rows = $res->fetch_all(MYSQLI_ASSOC);
        out(true, 'ok', 'ok', ['logs' => $rows]);
    }

    default:
        out(false, 'unknown_action', '未知操作');
}
