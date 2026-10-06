# Onyx_Build 1.0 加固方案

> 目标：把常规逆向破解时间从「分钟级」提升到「数小时级」。
> 声明：加固只能提高破解成本，无法阻止所有攻击。以下方案面向「会用 jadx / apktool / Frida / IDA 的常规攻击者」，足以把他们拖到数小时；面对专业逆向工程师仍会被攻破，只能不断迭代。

---

## 0. 当前破解成本（逆向实测结论）

| 弱点 | 实测表现 | 攻击者当前成本 |
|---|---|---|
| Java 类名未混淆 | 43 个业务类全部可读，包名 `com.kong.buildtool` / `com.vdl.kong520` 原样保留 | 秒级 |
| 字符串明文 | `https://pw.5w.pw/onyx_build/api.php`、`onyx_auth`、`token`、设备指纹算法全在 dex 里 | 秒级 |
| 登录决策在 Java | `OnyxAuth.ensureAuthenticated` → `if (result.ok) 放行`，可直接改字节码绕过 | 分钟级 |
| native 签名密钥弱保护 | `AuthGuard.nativeSign` 内 86 字节密钥仅与 `0x5A` 单字节异或，rodata 可直接提取还原 | 分钟级 |
| so 导出符号全暴露 | `nm -D` 可见全部 `Java_com_vdl_kong520_TpModule_*`，功能边界一目了然 | 秒级 |
| so 内嵌 Python 明文 | 30KB ModSDK RPC 脚本可 `strings` 整段提取 | 秒级 |
| 无完整性校验 | 改一行 Java / smali 重打包即可运行 | 分钟级 |
| Manifest 明文 | `xposedmodule=true`、入口 `BuildToolsHookInit` 直接可见 | 秒级 |

目标：上述每一项都处理掉，让攻击者必须同时对抗混淆、加密、反调试、完整性校验四层，总耗时 ≥ 数小时。

---

## 1. 加固层次与预期收益

| 层 | 手段 | 单独收益 |
|---|---|---|
| L1 编译期 | R8 全混淆 + 资源混淆 + 字符串加密 | 秒级 → 10~30 分钟 |
| L2 登录授权 | 决策 native 化 + 服务端挑战应答 + 证书固定 | 分钟级 → 1~2 小时 |
| L3 native | 符号隐藏 + 内嵌资源加密 + 控制流混淆 | 分钟级 → 1 小时+ |
| L4 完整性 | APK 签名校验 + DEX/so 自校验 + 防重打包 | 分钟级（重打包被直接拒绝） |
| L5 动态对抗 | 反调试 + Frida/Xposed 检测 | 数小时（攻击者需先过检测） |

四层叠加后，常规攻击路径「jadx 读源码 → 改逻辑 → 重打包」每一步都被打断，总时长即可达到数小时级。

---

## 2. 具体措施（逐项对应实测弱点）

### L1-1 R8 全混淆（必做，收益最高）

现状：[C0927R.java](reversed/Onyx_Build_1.0/java/kong/buildtool/C0927R.java)、类名、方法名全部保留，说明 `minifyEnabled` 未开或规则过宽。

做法：
- `build.gradle`：`minifyEnabled true` + `shrinkResources true` + 自定义混淆字典（特殊字符/拼音字典，提高阅读成本）。
- 只保留三处不混淆（用 `@Keep` 或 proguard 规则）：
  1. `assets/xposed_init` 指向的入口类（Xposed 框架按名字加载，必须保留最终混淆名）；
  2. Manifest 中声明的组件（`MainActivity`）；
  3. JNI 相关类/方法（若仍用符号绑定；切到动态注册后此项可去掉）。
- 其余全部混淆，包括 `com.vdl.kong520` 整个包。混淆后 `OnyxAuth` / `BuildToolsLauncher` / `TpModule` 的名字全部消失。

验收：重新用 jadx 反编译，业务类名应变成 `a.b.c` 形式且不可读。

### L1-2 字符串加密（必做）

现状：端点、SharedPreferences 键、设备指纹盐全部明文，见 [OnyxAuth.java:126](reversed/Onyx_Build_1.0/java/kong/buildtool/OnyxAuth.java:126) 附近。

做法：
- 引入字符串加密（自写模板生成器即可，不必上商业库）：所有敏感字符串编译期加密、运行时解密。
- 优先覆盖：
  - API 端点 `https://pw.5w.pw/onyx_build/api.php`（拆成多段拼接 + 运行时解密）
  - `onyx_auth`、`token`、`username`、`device_id`、`sign` 等键名
  - `sha256` 盐 `|onyx`
  - 登录/verify 的 action 名 `login` / `verify`
  - 水印/面板里的标识性文案（`Onyx_build`、`ONYX_BUILD`、`— By 萌面雕 —`）
- 注意：字符串加密后 jadx 输出是解密函数 + 密文数组，阅读成本显著上升。

### L1-3 资源混淆加固（已有，补充）

现状：res 已是短名混淆（`res/-6.webp`），但 `strings.xml`、`AndroidManifest.xml` 解码后仍可读。

做法：
- 继续使用 AndResGuard 类方案并开 `-zipalign`；
- 应用名 `app_name=Onyx_Build` 改为运行时设置或加密值；
- Manifest 的 `xposeddescription`（`Onyx_build building tools`）改为无意义字符串，真实描述放别处。

### L2-1 登录决策移入 native（重点）

现状：[OnyxAuth.java:129](reversed/Onyx_Build_1.0/java/kong/buildtool/OnyxAuth.java:129) 整段决策在 Java：攻击者只需改 `if (apiResult.getOk())` 或 hook `ensureAuthenticated`。

做法：
- 把「verify / login 结果是否有效」的最终判断移入 native（`NativeCore.verifyAuthorized()` 返回 true/false），Java 层不再出现 `getOk()` 判断；
- native 内部同时校验：服务端签名、本地 token、设备指纹、时间窗；
- Java 层只保留「请求 native → 按返回码决定 UI」的薄壳，攻击者无法通过改 Java 逻辑放行。

### L2-2 签名密钥加固（重点）

现状：[AuthGuard.java:43](reversed/Onyx_Build_1.0/java/kong/buildtool/AuthGuard.java:43) 的 `nativeSign` 在 so 内用 `0x5A` 异或存密钥，可提取。

做法：
- 废弃静态 XOR 密钥，改为：
  - **挑战-应答**：登录时服务端下发随机 nonce，native 用设备绑定密钥（Keystore 派生）签名 `username + device_id + nonce + timestamp`，服务端验证；密钥不落盘、不落 rodata；
  - 或**白盒加密/国密**：密钥分散到多个运算，不出现可直接提取的连续 86 字节。
- 同时引入时间窗（timestamp ± 5 分钟）与服务端重放防护（nonce 一次性）。

### L3-1 native 符号隐藏（必做）

现状：`nm -D` 可看到全部 `Java_com_vdl_kong520_TpModule_*`。

做法：
- CMake/ndk-build 加 `-fvisibility=hidden`，JNI 全部改为 `JNI_OnLoad` 内 `RegisterNatives` 动态注册，删除导出符号；
- 这样 `nm -D` 只剩 `JNI_OnLoad`，攻击者必须先逆向 `JNI_OnLoad` 才能找到功能函数；
- 同时把 `NativeCore` 与 `TpModule` 的方法合并到一张注册表，减少特征。

### L3-2 内嵌资源加密（重点）

现状：so 里 30KB ModSDK Python 明文脚本可 `strings` 整段提取（`embedded_modsdk_scripts.txt` 就是证据）。

做法：
- 内嵌 Python / 模板资源改为 AES-GCM 加密存储，运行时解密到内存；
- 密钥不从连续 rodata 出现（拆散 + 派生），加长度/哈希校验；
- 解密仅发生在需要执行的前一刻，降低内存 dump 可得性；
- 若可行，把 RPC 载荷改为 native 直发，不再携带明文脚本副本。

### L3-3 控制流混淆（P2，显著拉长静态分析）

- 关键函数（`JNI_OnLoad`、`nativeSign`、各 TpModule 入口）用 OLLVM / 类 LLVM pass 做控制流平坦化 + 虚假控制流；
- 目前 `JNI_OnLoad` 反汇编非常直白（见 [NativeCore.java:20](reversed/Onyx_Build_1.0/java/vdl/kong520/NativeCore.java:20) 对应实现），混淆后 IDA 跟读成本翻倍。

### L4 完整性校验（必做）

现状：无任何自校验，重打包即运行。

做法（native 内实现，不被 Java 层控制）：
- 启动时校验 APK 签名（`PackageManager.getPackageInfo(GET_SIGNATURES)` 的哈希与内置值比对）；
- 校验 `classes.dex` 的 CRC/哈希，检测 smali 改动；
- so 自身做 `__attribute__((constructor))` 自校验（文件哈希）；
- 检测到篡改：拒绝运行并上报服务端（不要直接崩溃，崩溃反而容易被定位后删掉检测）。
- 校验必须在 native 且每次关键操作前抽查，不能只在入口查一次。

### L5 动态对抗（P2，谨慎）

- 反调试：`ptrace` 自检、`/proc/self/status` TracerPid、调试端口扫描；
- Frida/Xposed 检测：`/proc/self/maps` 中 `frida`/`xposed` 特征、`/data/local/tmp` 常见文件、系统属性；
- **注意**：本模块本身就是 Xposed 模块，运行在网易我的世界进程里，该进程会加载大量合法 so，检测规则必须精确到自身特征，误报会砸自己；建议做成可远程下发的开关（服务端配置），出问题能关。
- 反调试与完整性校验共同把「动态调试 + 修改」路径从分钟级拖到数小时。

---

## 3. 实施顺序（按投入产出排序）

| 阶段 | 内容 | 预计工作量 | 收益 |
|---|---|---|---|
| P0-1 | R8 混淆 + 资源混淆 + 字符串加密 | 1 天 | 分钟级 → 30 分钟 |
| P0-2 | so 符号隐藏（动态注册 + visibility） | 0.5 天 | 秒级 → 30 分钟 |
| P0-3 | 完整性校验（签名 + dex + so） | 1 天 | 重打包路径直接堵死 |
| P1-1 | 签名密钥改挑战应答 / 白盒 | 1~2 天 | 授权绕过成本 1 小时+ |
| P1-2 | 登录决策 native 化 + 证书固定 | 1 天 | 改 Java 绕过失效 |
| P1-3 | 内嵌 Python 加密 | 1 天 | so strings 提取失效 |
| P2-1 | 控制流混淆 | 1~2 天 | 静态分析时间翻倍 |
| P2-2 | 反调试 + Frida/Xposed 检测（可远程开关） | 2 天 | 动态调试被拦截 |

完成 P0 全部 + P1 任意两项后，常规攻击者的完整破解（读懂 + 绕过授权 + 重打包）即可达到数小时量级。

---

## 4. 验收清单（发布前必须跑一遍）

1. `jadx -d out` 反编译加固包：业务类名不可读、无明文敏感字符串、登录决策不在 Java；
2. `strings libweibosdkcore.so`：无内嵌 Python 明文、无密钥、无 URL；
3. `nm -D libweibosdkcore.so`：无任何 `Java_com_*` 导出；
4. `apktool d` + 修改一行 smali + 重签名：安装后运行被拒（完整性校验生效）；
5. 真机 LSPosed 环境：模块能正常加载、hook 生效（**必须**，否则加固把自己锁死）；
6. 正常登录/verify/工具全流程回归通过；
7. 老设备（Android 8~10）与主流框架兼容性抽测。

---

## 5. 边界与风险（如实告知）

- **Xposed 兼容性是最大约束**：入口类名必须能被 LSPosed 按 `xposed_init` 加载，DEX 加壳（腾讯乐固/爱加密/梆梆/360）会引入自定义 ClassLoader，可能与 Xposed 加载机制冲突——**先 PoC 再决定是否上壳**；本方案 P0/P1 不依赖商业壳也能达标。
- 反调试/Frida 检测会误伤部分正常用户（模拟器、企业 MDM、部分游戏加速器），务必做成服务端可关闭的开关并埋点统计误报。
- 加固提升的是时间成本，不是绝对安全；每次发版前都要重跑第 4 节验收清单，因为新功能可能引入新的明文串/可读逻辑。
- 服务端风控（频率限制、设备指纹、异常 IP）是授权防绕过的最后一道闸，客户端加固只能延缓，不能替代。

---

## 6. 相关源码文件索引（当前反编译树）

- [OnyxAuth.java](reversed/Onyx_Build_1.0/java/kong/buildtool/OnyxAuth.java) — 登录门禁、端点、token 存储、设备指纹（L1-2/L2 主要改造点）
- [AuthGuard.java](reversed/Onyx_Build_1.0/java/kong/buildtool/AuthGuard.java) — nativeSign 签名入口（L2-2 改造点）
- [BuildToolsHookInit.java](reversed/Onyx_Build_1.0/java/kong/buildtool/BuildToolsHookInit.java) — Xposed 入口（L1-1 需 @Keep，L5 检测注入点）
- [NativeLibLoader.java](reversed/Onyx_Build_1.0/java/vdl/kong520/NativeLibLoader.java) — so 加载与 `LIB_NAME` 伪装（可一并随机化）
- [NativeCore.java](reversed/Onyx_Build_1.0/java/vdl/kong520/NativeCore.java) — native 方法声明（L3-1 动态注册清单）
- [TpModule.java](reversed/Onyx_Build_1.0/java/vdl/kong520/TpModule.java) — 功能 JNI 面（L3-1/L3-3 改造点）
- [BuildToolsWatermark.java](reversed/Onyx_Build_1.0/java/kong/buildtool/BuildToolsWatermark.java) — 内置 Base64 图标与标识文案（L1-2）
- [BuildToolsPanelPopup.java](reversed/Onyx_Build_1.0/java/kong/buildtool/BuildToolsPanelPopup.java) — 面板入口（L2-1 校验点）
