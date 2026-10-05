<?php
header('Content-Type: application/json; charset=utf-8');

if (!file_exists(__DIR__ . '/config.php')) {
    echo json_encode(['status' => 'fail', 'message' => '后端未配置'], JSON_UNESCAPED_UNICODE);
    exit;
}
require_once __DIR__ . '/config.php';

define('REGISTER_PASSWORD', 'mmdjknb');

function out_fail(string $msg): void {
    echo json_encode(['status' => 'fail', 'message' => $msg], JSON_UNESCAPED_UNICODE);
    exit;
}

function out_ok(string $msg): void {
    echo json_encode(['status' => 'success', 'data' => ['message' => $msg]], JSON_UNESCAPED_UNICODE);
    exit;
}

$password = (string)($_GET['password'] ?? '');
if ($password === '' || !hash_equals(REGISTER_PASSWORD, $password)) {
    out_fail('注册密钥错误');
}

$username = trim((string)($_GET['username'] ?? ''));
$userpass = (string)($_GET['userpass'] ?? '');
$dcId     = trim((string)($_GET['dc_id'] ?? ''));

if ($username === '' || $userpass === '') out_fail('账号和密码不能为空');
if (!preg_match('/^[A-Za-z0-9_]{3,32}$/', $username)) out_fail('账号需 3-32 位字母数字下划线');
if (strlen($userpass) < 4) out_fail('密码至少 4 位');

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
try {
    $db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS, ONYX_DB_NAME);
    $db->set_charset('utf8mb4');
} catch (Throwable $e) {
    out_fail('服务暂时不可用');
}

$stmt = $db->prepare('SELECT id FROM Onyx_users WHERE username = ? LIMIT 1');
$stmt->bind_param('s', $username);
$stmt->execute();
if ($stmt->get_result()->fetch_assoc()) {
    out_fail('该账号已被注册');
}

$hash = password_hash($userpass, PASSWORD_DEFAULT);
$stmt = $db->prepare('INSERT INTO Onyx_users (username, password_hash) VALUES (?, ?)');
try {
    $stmt->bind_param('ss', $username, $hash);
    $stmt->execute();
} catch (Throwable $e) {
    out_fail('该账号已被注册');
}

out_ok('注册成功');
