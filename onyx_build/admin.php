<?php
/**
 * Onyx_build 管理后台。
 * 登录凭证 = config.php 里的 ONYX_ADMIN_USER / ONYX_ADMIN_PASS。
 * 只存在服务器配置中，不进数据库。
 */
session_start();
require_once __DIR__ . '/config.php';

function page_head(string $title): void {
    echo '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">'
        . '<meta name="viewport" content="width=device-width, initial-scale=1">'
        . '<title>' . htmlspecialchars($title) . '</title><style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
       background: linear-gradient(160deg, #F3F7FD 0%, #E4EEFC 100%); min-height: 100vh; padding: 24px; }
.wrap { max-width: 1060px; margin: 0 auto; }
.card { background: #fff; border: 1px solid #D8E2F0; border-radius: 16px; padding: 22px; margin-bottom: 18px;
        box-shadow: 0 4px 18px rgba(46,111,216,.07); }
.brand { color: #2E6FD8; font-weight: 700; letter-spacing: 3px; font-size: 12px; }
h1 { color: #1B2735; font-size: 20px; margin: 4px 0 2px; }
.sub { color: #6B7A90; font-size: 13px; margin-bottom: 16px; }
label { display: block; color: #1B2735; font-size: 13px; font-weight: 600; margin: 10px 0 5px; }
input, select { width: 100%; padding: 10px 12px; border: 1px solid #D8E2F0; border-radius: 10px;
        font-size: 14px; background: #F6F9FE; color: #1B2735; outline: none; }
input:focus { border-color: #2E6FD8; background: #fff; }
button { padding: 10px 18px; border: 0; border-radius: 10px; background: #2E6FD8; color: #fff;
         font-size: 14px; font-weight: 700; cursor: pointer; }
button:hover { background: #1F4FA8; }
button.ghost { background: #EDF3FC; color: #1F4FA8; }
button.danger { background: #FBEAE8; color: #C4453A; }
.msg { border-radius: 10px; padding: 11px 14px; font-size: 13.5px; margin-bottom: 14px; }
.ok { background: #E4F1EA; color: #2F7D5B; } .err { background: #FBEAE8; color: #C4453A; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: #6B7A90; font-weight: 600; padding: 8px 10px; border-bottom: 1px solid #D8E2F0; white-space: nowrap; }
td { padding: 9px 10px; border-bottom: 1px solid #EDF3FC; color: #1B2735; word-break: break-all; }
tr:hover td { background: #F6F9FE; }
.tag { display: inline-block; padding: 2px 9px; border-radius: 20px; font-size: 12px; font-weight: 600; }
.tag.ok { background: #E4F1EA; color: #2F7D5B; } .tag.ban { background: #FBEAE8; color: #C4453A; }
.tag.dim { background: #EDF3FC; color: #1F4FA8; }
.row { display: flex; gap: 14px; flex-wrap: wrap; } .row > div { flex: 1; min-width: 200px; }
.top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; color: #6B7A90; }
</style></head><body><div class="wrap">';
}

function page_foot(): void { echo '</div></body></html>'; }

// ============ 登录 ============
if (!isset($_SESSION['onyx_admin'])) {
    $err = '';
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $inputUser = (string)($_POST['user'] ?? '');
        $inputPass = (string)($_POST['pass'] ?? '');
        if (hash_equals(ONYX_ADMIN_USER, $inputUser) && hash_equals(ONYX_ADMIN_PASS, $inputPass)) {
            session_regenerate_id(true);
            $_SESSION['onyx_admin'] = true;
            header('Location: admin.php');
            exit;
        }
        $err = '账号或密码错误';
    }
    page_head('Onyx_build · 后台登录');
    echo '<div class="card" style="max-width:420px;margin:8vh auto;"><div class="brand">ONYX_BUILD</div>
        <h1>管理后台</h1><div class="sub">请输入账号和密码登录</div>';
    if ($err) echo '<div class="msg err">' . htmlspecialchars($err) . '</div>';
    echo '<form method="post">
        <label>账号</label><input type="text" name="user" required autofocus>
        <label>密码</label><input type="password" name="pass" required>
        <div style="margin-top:16px"><button>登 录</button></div></form></div>';
    page_foot();
    exit;
}

// ============ 已登录 ============
mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
$db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS, ONYX_DB_NAME);
$db->set_charset('utf8mb4');

$flash = '';
$act = $_POST['act'] ?? '';

try {
    if ($act === 'create') {
        $u = trim($_POST['username'] ?? '');
        $p = (string)($_POST['password'] ?? '');
        if (!preg_match('/^[A-Za-z0-9_]{3,32}$/', $u) || strlen($p) < 4) {
            $flash = ['err', '用户名需 3-32 位字母数字下划线，密码至少 4 位'];
        } else {
            $stmt = $db->prepare('INSERT INTO Onyx_users (username, password_hash) VALUES (?, ?)');
            $hash = password_hash($p, PASSWORD_DEFAULT);
            $stmt->bind_param('ss', $u, $hash);
            $stmt->execute();
            $flash = ['ok', "账号 {$u} 创建成功"];
        }
    } elseif ($act === 'ban' || $act === 'unban') {
        $stmt = $db->prepare('UPDATE Onyx_users SET status = ?, banned_reason = ? WHERE username = ?');
        $ban = ($act === 'ban') ? 0 : 1;
        $reason = ($act === 'ban') ? (string)($_POST['reason'] ?? '违规使用') : null;
        $u = (string)($_POST['username'] ?? '');
        $stmt->bind_param('iss', $ban, $reason, $u);
        $stmt->execute();
        if ($act === 'ban') {
            $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE user_id = (SELECT id FROM Onyx_users WHERE username = ?)');
            $stmt->bind_param('s', $u);
            $stmt->execute();
        }
        $flash = ['ok', $act === 'ban' ? "已封禁 {$u}" : "已解封 {$u}"];
    } elseif ($act === 'unbind') {
        $u = (string)($_POST['username'] ?? '');
        $stmt = $db->prepare('UPDATE Onyx_users SET bound_device = NULL WHERE username = ?');
        $stmt->bind_param('s', $u);
        $stmt->execute();
        $flash = ['ok', "已解绑 {$u} 的设备"];
    } elseif ($act === 'resetpass') {
        $u = (string)($_POST['username'] ?? '');
        $p = (string)($_POST['password'] ?? '');
        if (strlen($p) < 4) $flash = ['err', '密码至少 4 位'];
        else {
            $stmt = $db->prepare('UPDATE Onyx_users SET password_hash = ? WHERE username = ?');
            $hash = password_hash($p, PASSWORD_DEFAULT);
            $stmt->bind_param('ss', $hash, $u);
            $stmt->execute();
            $flash = ['ok', "已重置 {$u} 的密码"];
        }
    } elseif ($act === 'delete') {
        $u = (string)($_POST['username'] ?? '');
        $stmt = $db->prepare('DELETE FROM Onyx_users WHERE username = ?');
        $stmt->bind_param('s', $u);
        $stmt->execute();
        $stmt = $db->prepare('DELETE FROM Onyx_sessions WHERE user_id = 0 OR user_id NOT IN (SELECT id FROM Onyx_users)');
        $stmt->execute();
        $flash = ['ok', "已删除账号 {$u}"];
    } elseif ($act === 'logout') {
        session_destroy();
        header('Location: admin.php');
        exit;
    }
} catch (Throwable $e) {
    $flash = ['err', '操作失败：' . $e->getMessage()];
}

page_head('Onyx_build · 管理后台');
echo '<div class="top"><div><div class="brand">ONYX_BUILD</div><h1>管理后台</h1></div>
    <form method="post"><input type="hidden" name="act" value="logout"><button class="ghost">退出</button></form></div>
    <div class="sub" style="margin-bottom:14px">用户与授权管理 · 设备绑定 · 登录审计</div>';
if ($flash) echo '<div class="msg ' . $flash[0] . '">' . htmlspecialchars($flash[1]) . '</div>';

// ---- 建号 ----
echo '<div class="card"><h1 style="font-size:15px">➕ 创建账号</h1>
    <form method="post"><input type="hidden" name="act" value="create"><div class="row">
    <div><label>用户名</label><input name="username" placeholder="3-32 位字母数字下划线" required></div>
    <div><label>初始密码</label><input name="password" required></div>
    <div style="flex:0;align-self:flex-end"><button>创建</button></div></div></form></div>';

// ---- 用户表 ----
$users = $db->query('SELECT id, username, status, banned_reason, bound_device, created_at FROM Onyx_users ORDER BY id DESC')->fetch_all(MYSQLI_ASSOC);
echo '<div class="card"><h1 style="font-size:15px;margin-bottom:10px">👥 用户（' . count($users) . '）</h1>
    <div style="overflow-x:auto"><table><tr><th>用户</th><th>状态</th><th>绑定设备</th><th>创建时间</th><th>操作</th></tr>';
foreach ($users as $u) {
    $banned = (int)$u['status'] === 0;
    echo '<tr><td><b>' . htmlspecialchars($u['username']) . '</b></td>';
    echo '<td>' . ($banned ? '<span class="tag ban">已封禁</span>' : '<span class="tag ok">正常</span>');
    if ($banned && $u['banned_reason']) echo '<div class="mono">' . htmlspecialchars($u['banned_reason']) . '</div>';
    echo '</td><td>' . ($u['bound_device']
        ? '<span class="tag dim mono">' . htmlspecialchars(substr($u['bound_device'], 0, 16)) . '…</span>'
        : '<span class="mono">未绑定</span>') . '</td>';
    echo '<td class="mono">' . htmlspecialchars($u['created_at']) . '</td><td>';
    $name = htmlspecialchars($u['username']);
    echo '<form method="post" style="display:inline" onsubmit="return confirm(\'确定?\')">'
        . '<input type="hidden" name="username" value="' . $name . '">';
    if ($banned) {
        echo '<input type="hidden" name="act" value="unban"><button class="ghost">解封</button> ';
    } else {
        echo '<input type="hidden" name="act" value="ban"><button class="danger" onclick="this.form.reason.value=prompt(\'封禁原因:\',\'违规使用\')||this.form.reason.value">封禁</button> ';
        echo '<input type="hidden" name="reason">';
    }
    echo '</form>';
    echo '<form method="post" style="display:inline" onsubmit="var p=prompt(\'新密码:\');if(!p)return false;this.form.password.value=p">'
        . '<input type="hidden" name="act" value="resetpass"><input type="hidden" name="username" value="' . $name . '">'
        . '<input type="hidden" name="password"><button class="ghost">改密</button> </form>';
    if ($u['bound_device']) {
        echo '<form method="post" style="display:inline"><input type="hidden" name="act" value="unbind">'
            . '<input type="hidden" name="username" value="' . $name . '"><button class="ghost">解绑设备</button> </form>';
    }
    echo '<form method="post" style="display:inline" onsubmit="return confirm(\'删除后不可恢复，确定?\')">'
        . '<input type="hidden" name="act" value="delete"><input type="hidden" name="username" value="' . $name . '">'
        . '<button class="danger">删除</button></form>';
    echo '</td></tr>';
}
echo '</table></div></div>';

// ---- 登录记录 ----
$logs = $db->query('SELECT username, ip, device_id, result, login_time FROM Onyx_login_logs ORDER BY id DESC LIMIT 100')->fetch_all(MYSQLI_ASSOC);
$resultName = ['success' => '成功', 'bad_credentials' => '密码错误', 'device_mismatch' => '设备不符', 'banned' => '已封禁', 'other' => '其他'];
echo '<div class="card"><h1 style="font-size:15px;margin-bottom:10px">📋 登录记录（最近 100 条）</h1>
    <div style="overflow-x:auto"><table><tr><th>时间</th><th>用户</th><th>IP</th><th>设备码</th><th>结果</th></tr>';
foreach ($logs as $l) {
    $cls = $l['result'] === 'success' ? 'ok' : ($l['result'] === 'banned' || $l['result'] === 'device_mismatch' ? 'ban' : 'dim');
    echo '<tr><td class="mono">' . htmlspecialchars($l['login_time']) . '</td>'
        . '<td><b>' . htmlspecialchars($l['username']) . '</b></td>'
        . '<td class="mono">' . htmlspecialchars($l['ip']) . '</td>'
        . '<td class="mono">' . htmlspecialchars(substr($l['device_id'], 0, 16)) . '…</td>'
        . '<td><span class="tag ' . $cls . '">' . ($resultName[$l['result']] ?? $l['result']) . '</span></td></tr>';
}
echo '</table></div></div>';
page_foot();