<?php
/**
 * 一键导入 onyx.sql 建表。
 * 用法（带管理密钥防滥用）：
 *   浏览器: import.php?key=你的ADMIN_KEY
 *   SSH:    php import.php
 * 导入成功后请删除本文件。
 */
require_once __DIR__ . '/config.php';

$isCli = PHP_SAPI === 'cli';
if (!is_file($sqlFile)) exit("找不到 {$sqlFile}\n");

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
try {
    $db = new mysqli(ONYX_DB_HOST, ONYX_DB_USER, ONYX_DB_PASS, ONYX_DB_NAME);
    $db->set_charset('utf8mb4');
    $db->multi_query(file_get_contents($sqlFile));
    while ($db->more_results() && $db->next_result()) {;}

    $out = [];
    foreach (['Onyx_users', 'Onyx_login_logs', 'Onyx_sessions'] as $t) {
        $r = $db->query("SHOW TABLES LIKE '{$t}'");
        $out[] = ($r && $r->num_rows ? '✅' : '❌') . ' ' . $t;
    }
    $msg = "SQL 导入完成：\n" . implode("\n", $out);
} catch (Throwable $e) {
    $msg = '❌ 导入失败：' . $e->getMessage();
}

if ($isCli) echo $msg . PHP_EOL;
else echo '<meta charset="utf-8"><pre>' . htmlspecialchars($msg) . '</pre>';
