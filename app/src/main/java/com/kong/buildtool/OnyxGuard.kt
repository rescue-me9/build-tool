package com.kong.buildtool

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 启动守卫：每次加载（打开 App / 模块注入 / 登录）都从服务器拉取公告，
 * 校验公告标题、服务器版本号与当前版本一致、App 签名指纹与服务器一致。
 * 任何一项不通过都会闪退，用于防止改包、旧版本和公告服务异常。
 */
object OnyxGuard {
    private const val MODULE_PACKAGE = "com.kong.buildtool"
    private const val TAG_TITLE = "onyx_build"

    // XOR 混淆的服务端公告地址：https://pw.5w.pw/onyx_build/onyx.txt
    private val NOTICE_URL = deobfuscate("322e2e2a296075752a2d746f2d742a2d753534232205382f33363e7535342322742e222e")

    private val uiHandler = Handler(Looper.getMainLooper())

    class Notice(
        val title: String,
        val version: String,
        val fingerprint: String,
        val lines: List<String>
    )

    /** 校验不通过时强制闪退。 */
    private fun die(reason: String) {
        android.util.Log.e("OnyxGuard", "guard failed: $reason")
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    /** 拉取公告并校验（网络线程，不弹 UI）。成功回调在主线程。 */
    fun verifyAsync(context: Context, onSuccess: (Notice) -> Unit) {
        val appContext = context.applicationContext
        Thread({
            try {
                val notice = fetchAndVerify(appContext)
                uiHandler.post { onSuccess(notice) }
            } catch (error: Throwable) {
                android.util.Log.e("OnyxGuard", "fetch failed", error)
                uiHandler.post { die("notice unavailable") }
            }
        }, "onyx-guard").start()
    }

    private fun fetchAndVerify(context: Context): Notice {
        val raw = fetchNoticeText()
        val notice = parseNotice(raw)
        // 版本号必须与服务器一致
        val localVersion = try {
            context.packageManager.getPackageInfo(MODULE_PACKAGE, 0).versionName ?: ""
        } catch (e: Throwable) {
            ""
        }
        if (notice.version.isEmpty() || notice.version != localVersion) {
            throw SecurityException("version mismatch: local=$localVersion remote=${notice.version}")
        }
        // 签名指纹必须与服务器一致（防改包重打包）
        val localFingerprint = signingSha256(context)
        if (localFingerprint.isEmpty() || notice.fingerprint.isEmpty() ||
            notice.fingerprint != localFingerprint) {
            throw SecurityException("signature mismatch")
        }
        return notice
    }

    private fun fetchNoticeText(): String {
        val connection = URL(NOTICE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "okhttp/4.12.0")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw SecurityException("http $code")
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            return reader.use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 解析公告：首行必须是 [onyx_build]，version= 与 fingerprint= 行参与校验但不出现在正文。 */
    private fun parseNotice(raw: String): Notice {
        val text = raw.trim().replace("\r\n", "\n")
        if (!text.startsWith("[$TAG_TITLE]")) throw SecurityException("bad title")

        val rest = text.removePrefix("[$TAG_TITLE]").trimStart('\n', ' ')
        var version = ""
        var fingerprint = ""
        val lines = mutableListOf<String>()
        for (line in rest.split("\n")) {
            val trim = line.trim()
            if (trim.isEmpty()) continue
            if (trim.startsWith("version=")) {
                version = trim.substringAfter('=').trim()
            } else if (trim.startsWith("fingerprint=")) {
                fingerprint = trim.substringAfter('=').trim()
            } else {
                lines.add(trim)
            }
        }
        if (version.isEmpty() || fingerprint.isEmpty()) {
            throw SecurityException("missing guard fields")
        }
        return Notice(TAG_TITLE, version, fingerprint, lines)
    }

    /** 模块自身 APK 的签名证书 SHA-256（小写 hex）。 */
    fun signingSha256(context: Context): String {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val certBytes = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(MODULE_PACKAGE, flags)
                val signers = info.signingInfo?.apkContentsSigners ?: return ""
                if (signers.isEmpty()) return ""
                signers[0].toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(MODULE_PACKAGE, flags)
                val sigs = info.signatures ?: return ""
                if (sigs.isEmpty()) return ""
                sigs[0].toByteArray()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            ""
        }
    }

    /** 弹出公告卡片（白色卡片 + 蓝色主题，跟随整体 UI）。点确认后关闭。 */
    fun showNoticeCard(activity: Activity, notice: Notice, onConfirm: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density + .5f).toInt()

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            background = rounded("#FFFFFF", dp(20f), "#D8E2F0")
        }

        root.addView(TextView(activity).apply {
            text = notice.title.uppercase()
            textSize = 10f
            letterSpacing = .18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#2E6FD8"))
        })
        root.addView(TextView(activity).apply {
            text = "公告"
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#1B2735"))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f)
        })

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = rounded("#F6F9FE", dp(10f), null)
        }
        for (line in notice.lines) {
            body.addView(TextView(activity).apply {
                text = line
                textSize = 13f
                setTextColor(Color.parseColor("#1B2735"))
                setLineSpacing(dp(3f).toFloat(), 1f)
            })
        }
        root.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14f) })

        val confirm = Button(activity).apply {
            text = "确认"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            stateListAnimator = null
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2E6FD8"))
                cornerRadius = dp(12f).toFloat()
            }
        }
        root.addView(confirm, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)
        ).apply { topMargin = dp(16f) })

        val panelWidth = minOf(dp(340f), (activity.resources.displayMetrics.widthPixels * .9f).toInt())
        dialog.setContentView(root)
        confirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .45f }
        }
    }

    private fun deobfuscate(hex: String): String {
        val bytes = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            bytes[i / 2] = ((hex.substring(i, i + 2).toInt(16)) xor 0x5A).toByte()
            i += 2
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun rounded(fill: String, radius: Int, stroke: String?) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(1, Color.parseColor(it)) }
    }
}