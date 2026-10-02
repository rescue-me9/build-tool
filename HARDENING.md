# Onyx_build 加固说明

## 本次加固做了什么

### 1. 签名密钥下沉 Native 层
- 之前：`SIGN_SECRET` 以明文写在 Kotlin 里，反编译 APK 用字符串搜索就能拿到。
- 现在：密钥每字节与 `0x5A` 异或后再叠加以字节序扰动，硬编码进 `libweibosdkcore.so`（`app/src/main/cpp/auth_guard.cpp` 的 `kObfuscatedSecret` 数组），运行时在 C++ 内解密并计算 SHA-256 签名。
- Java 层只剩 `AuthGuard.sign()` 桥接调用，APK 的 DEX 里搜不到密钥明文。
- so 里还内置了一份纯 C 实现 SHA-256（`sha256.cpp`），不依赖 Java 层，逆向者需要反汇编 ARM64 机器码才能还原逻辑。
- 混淆算法：`encoded[i] = (secret[i] ^ 0x5A) + i*7`，解码 `((encoded[i] - i*7) & 0xFF) ^ 0x5A`。比单层 XOR 更难静态还原。

### 2. Release 构建开启 R8 压缩混淆
- `app/build.gradle.kts` 的 release 变体已设置 `isMinifyEnabled = true` + `isShrinkResources = true`（Debug 同样开启）。
- `proguard-rules.pro` 保留 Xposed 入口（`BuildToolsHookInit`、`NativeCore`）与 JNI 静态注册类（`AuthGuard`、`OnyxAuth`）的类名/方法名，其余代码由 R8 做方法级优化与压缩。

### 3. 敏感字符串运行时解密
- `OnyxAuth.kt` 中的 API 地址、SharedPreferences 名、设备盐值不再以明文出现在 DEX，改为 XOR 混淆的十六进制串，运行时用 `deobfuscate()` 还原。
- 防 jadx/字符串搜索直接定位登录接口与本地存储键名。

### 4. 相关文件
| 文件 | 作用 |
|---|---|
| `app/src/main/cpp/auth_guard.cpp` | JNI 签名接口 + 混淆密钥存储 |
| `app/src/main/cpp/sha256.cpp` | 纯 C SHA-256 实现 |
| `app/src/main/java/com/kong/buildtool/AuthGuard.kt` | Java 桥接 |
| `app/src/main/java/com/kong/buildtool/OnyxAuth.kt` | 登录逻辑，改为调用 `AuthGuard.sign()` |

### 5. 修改密钥的方法
密钥存储在两处，必须同步修改：
- 客户端：`auth_guard.cpp` 里的 `kObfuscatedSecret` 数组 = 每字节 `真实密钥 UTF-8 字节 XOR 0x5A`
- 服务端：`server/config.php` 的 `ONYX_SIGN_SECRET` = 真实密钥明文

生成混淆数组：
```python
secret = "你的新密钥"
print([hex(b ^ 0x5A) for b in secret.encode('utf-8')])
```

## 服务端加固（api.php）
- 登录失败 6 次 → 锁定该 用户名+IP 组合 2 分钟，成功登录自动清零
- 接口限流：login 每 IP 每分钟 15 次、verify 每分钟 30 次
- 基于服务器临时文件实现，无需额外组件

## 仍然存在的边界（如实说明）
- Xposed 模块运行在别人进程里，客户端逻辑理论上可被内存 patch 绕过，纯客户端防线没有100%。
- 本方案的防线逻辑：密钥藏在 so 中（提高伪造请求门槛）+ 服务端限流（防爆破）+ 单设备绑定（泄号影响面可控）。
- 若需对抗专业逆向，可再加商业加固壳（腾讯乐固/梆梆），但需实测与 Xposed 注入的兼容性——加固壳有概率破坏模块初始化，暂未启用。
