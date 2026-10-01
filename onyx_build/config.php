<?php
// Onyx_build 数据库与密钥配置（api.php / admin.php 共用）
// 修改这里后立即生效，无需重启。

define('ONYX_DB_HOST', 'localhost');
define('ONYX_DB_NAME', 'lvbkblij');
define('ONYX_DB_USER', 'lVBkBlij');
define('ONYX_DB_PASS', 'aDaP24gG');

// 管理后台登录密钥（admin.php 用，不进数据库）
define('ONYX_ADMIN_KEY', 'OnYx_build@qq&discord;**k**topicwuxu-Ju(&)_OP@+27-wusid#-OLUH\'!-Ipo-OnYx_Build_J-Top£');

// 登录请求签名盐（必须与 App 内 OnyxAuth.kt 的 SIGN_SECRET 一致）
define('ONYX_SIGN_SECRET', 'OnYx_build@qq&discord;**k**topicwuxu-Ju(&)_OP@+27-wusid#-OLUH\'!-Ipo-OnYx_Build_J-Top£');

// 会话有效期（秒）：7 天
define('ONYX_SESSION_TTL', 86400 * 7);


// 后台登录账号密码（admin.php 用）
define('ONYX_ADMIN_USER', 'wzr114514');
define('ONYX_ADMIN_PASS', '123456');
