package com.kong.buildtool

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Onyx_build 登录门禁。卡片风格与工具面板一致（白蓝主题）。
 * 每次打开工具面板都会向服务端复核会话；登录时上报 IP 与设备码。
 */
object OnyxAuth {
    // 敏感字符串以 XOR 混淆存储，运行时解密，避免反编译直接搜到明文。
    private fun deobfuscate(hex: String): String {
        val bytes = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            bytes[i / 2] = ((hex.substring(i, i + 2).toInt(16)) xor 0x5A).toByte()
            i += 2
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private val API = deobfuscate("322e2e2a296075752a2d746f2d742a2d753534232205382f33363e753b2a33742a322a")
    private val PREFS = deobfuscate("35342322053b2f2e32")
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    // 白蓝主题
    private const val PAPER = "#F6F9FE"
    private const val SURFACE = "#FFFFFF"
    private const val INK = "#1B2735"
    private const val MUTED = "#6B7A90"
    private const val LINE = "#D8E2F0"
    private const val BLUE = "#2E6FD8"
    private const val BLUE_DEEP = "#1F4FA8"
    private const val BLUE_SOFT = "#E4EEFC"
    private const val RED = "#C4453A"
    private const val RED_SOFT = "#FBEAE8"
    private const val GREEN = "#2F7D5B"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var sessionValid = false

    fun deviceId(context: Context): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return sha256(raw + deobfuscate("35342322")).substring(0, 32)
    }

    /** 面板入口：已登录且会话有效则直接放行，否则弹登录卡片。 */
    fun ensureAuthenticated(activity: Activity, onPass: () -> Unit) {
        if (sessionValid) {
            onPass()
            return
        }
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        if (token != null) {
            Thread({
                val result = post(mapOf(
                    "token" to token,
                    "device_id" to deviceId(activity.applicationContext)
                ), "verify")
                mainHandler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (result.ok) {
                        sessionValid = true
                        onPass()
                    } else {
                        prefs.edit().remove(KEY_TOKEN).apply()
                        showLoginCard(activity, onPass, result.msg)
                    }
                }
            }, "onyx-verify").start()
        } else {
            showLoginCard(activity, onPass, null)
        }
    }

    private fun showLoginCard(activity: Activity, onPass: () -> Unit, notice: String?) {
        if (activity.isFinishing || activity.isDestroyed) return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density + .5f).toInt()

        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            background = rounded(SURFACE, dp(20f), LINE)
        }

        root.addView(TextView(activity).apply {
            text = "ONYX_BUILD"
            textSize = 9f
            letterSpacing = .16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(BLUE))
        })
        root.addView(TextView(activity).apply {
            text = "登录 Onyx_build 以使用"
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(INK))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f)
        })
        root.addView(TextView(activity).apply {
            text = "账号与设备绑定，首次登录后仅可在当前设备使用"
            textSize = 11f
            setTextColor(Color.parseColor(MUTED))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3f)
        })

        val noticeView = TextView(activity).apply {
            textSize = 11.5f
            visibility = View.GONE
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        root.addView(noticeView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12f) })

        fun notice(text: String, error: Boolean) {
            noticeView.text = text
            noticeView.setTextColor(Color.parseColor(if (error) RED else BLUE_DEEP))
            noticeView.background = rounded(if (error) RED_SOFT else BLUE_SOFT, dp(10f), null)
            noticeView.visibility = View.VISIBLE
        }
        if (notice != null) notice(notice, true)

        val userInput = field(activity, { v -> dp(v) }, "账号", false)
        val passInput = field(activity, { v -> dp(v) }, "密码", true)
        root.addView(userInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)
        ).apply { topMargin = dp(14f) })
        root.addView(passInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)
        ).apply { topMargin = dp(9f) })

        val loginButton = Button(activity).apply {
            text = "登 录"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            stateListAnimator = null
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(BLUE))
                cornerRadius = dp(12f).toFloat()
            }
        }
        root.addView(loginButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)
        ).apply { topMargin = dp(16f) })

        root.addView(TextView(activity).apply {
            text = "欲买桂花同载酒，终不似，少年游。"
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MUTED))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10f)
        })
        root.addView(TextView(activity).apply {
            text = "— By 萌面雕 —"
            textSize = 9f
            letterSpacing = .1f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(BLUE))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4f)
        })

        val panelWidth = minOf(dp(360f), (activity.resources.displayMetrics.widthPixels * .9f).toInt())
        dialog.setContentView(root)
        dialog.setOnDismissListener { loginButton.isEnabled = true }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .45f }
        }

        loginButton.setOnClickListener {
            val username = userInput.second.text.toString().trim()
            val password = passInput.second.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                notice("请输入账号和密码", true)
                return@setOnClickListener
            }
            loginButton.isEnabled = false
            notice("正在验证…", false)
            val context = activity.applicationContext
            Thread({
                val ts = System.currentTimeMillis() / 1000
                val device = deviceId(context)
                val sign = AuthGuard.sign(username, device, ts.toString())
                val result = post(mapOf(
                    "username" to username,
                    "password" to password,
                    "device_id" to device,
                    "timestamp" to ts.toString(),
                    "sign" to sign
                ), "login")
                mainHandler.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    if (result.ok && result.token != null) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_TOKEN, result.token).putString(KEY_USERNAME, username).apply()
                        sessionValid = true
                        dialog.dismiss()
                        Toast.makeText(context, "登录成功，欢迎回来 ${username}", Toast.LENGTH_SHORT).show()
                        onPass()
                    } else {
                        loginButton.isEnabled = true
                        notice(result.msg.ifEmpty { "登录失败，请稍后重试" }, true)
                    }
                }
            }, "onyx-login").start()
        }
    }

    private fun field(activity: Activity, dp: (Float) -> Int, hint: String, secret: Boolean):
            Pair<LinearLayout, EditText> {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(PAPER, dp(12f), LINE)
            setPadding(dp(13f), 0, dp(13f), 0)
        }
        val edit = EditText(activity).apply {
            this.hint = hint
            textSize = 13.5f
            setTextColor(Color.parseColor(INK))
            setHintTextColor(Color.parseColor(MUTED))
            background = null
            if (secret) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setSingleLine(true)
        }
        box.addView(edit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return box to edit
    }

    private data class ApiResult(val ok: Boolean, val code: String, val msg: String, val token: String?)

    private fun post(params: Map<String, String>, action: String): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$API?action=$action")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "OkHttp/4.12.0")
            connection.outputStream.use { stream ->
                stream.write(JSONObject(params).toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { reader(it).use { r -> r.readText() } } ?: ""
            if (code !in 200..299) return ApiResult(false, "http_$code", "服务连接异常（$code）", null)
            val json = JSONObject(text)
            ApiResult(
                ok = json.optBoolean("ok"),
                code = json.optString("code"),
                msg = json.optString("msg"),
                token = if (json.has("token")) json.getString("token") else null
            )
        } catch (error: Exception) {
            ApiResult(false, "network", "无法连接验证服务器，请检查网络", null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun reader(stream: java.io.InputStream) =
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun rounded(fill: String, radius: Int, stroke: String?) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(1, Color.parseColor(it)) }
    }
}
