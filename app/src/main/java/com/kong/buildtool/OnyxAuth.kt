package com.kong.buildtool

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
 * Onyx_build 登录门禁。白色卡片 + 蓝色主题，与工具面板同风格。
 * 每次打开工具面板都会向服务端复核会话；登录时上报 IP 与设备码。
 */
object OnyxAuth {
    private const val API = "https://pw.5w.pw/onyx_build/api.php"
    private const val PREFS = "onyx_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    // 白色卡片 + 蓝色主题
    private const val SURFACE = "#FFFFFF"
    private const val INK = "#101828"
    private const val MUTED = "#667085"
    private const val LINE = "#DDE3EC"
    private const val BLUE = "#2563EB"
    private const val BLUE_DEEP = "#1E40AF"
    private const val BLUE_SOFT = "#EFF6FF"
    private const val RED = "#DC2626"
    private const val RED_SOFT = "#FEF2F2"
    private const val GREEN = "#16A34A"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var sessionValid = false

    fun deviceId(context: Context): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return sha256(raw + "|onyx").substring(0, 32)
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
            background = rounded(SURFACE, dp(26f), LINE)
        }

        // 深蓝渐变头部
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), dp(28f), dp(26f), dp(24f))
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#1E40AF"), Color.parseColor("#2563EB"))
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadii = floatArrayOf(
                    dp(26f).toFloat(), dp(26f).toFloat(),
                    dp(26f).toFloat(), dp(26f).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            addView(TextView(activity).apply {
                text = "ONYX_BUILD"
                textSize = 10f
                letterSpacing = .2f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.parseColor("#BFDBFE"))
            })
            addView(TextView(activity).apply {
                text = "登录 Onyx_build 以使用"
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) })
            addView(TextView(activity).apply {
                text = "账号与设备绑定，首次登录后仅可在当前设备使用"
                textSize = 11.5f
                setTextColor(Color.parseColor("#DBEAFE"))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // 白色表单区
        val formArea = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), dp(20f), dp(26f), dp(22f))
        }
        root.addView(formArea, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val noticeView = TextView(activity).apply {
            textSize = 12f
            visibility = View.GONE
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        formArea.addView(noticeView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        fun notice(text: String, error: Boolean) {
            noticeView.text = text
            noticeView.setTextColor(Color.parseColor(if (error) RED else BLUE_DEEP))
            noticeView.background = rounded(if (error) RED_SOFT else BLUE_SOFT, dp(10f), null)
            noticeView.visibility = View.VISIBLE
        }
        if (notice != null) {
            notice(notice, true)
        } else {
            formArea.addView(View(activity), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2f)
            ))
        }

        val userInput = field(activity, { v -> dp(v) }, "账号", false)
        val passInput = field(activity, { v -> dp(v) }, "密码", true)
        formArea.addView(userInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)
        ).apply { topMargin = dp(14f) })
        formArea.addView(passInput.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)
        ).apply { topMargin = dp(10f) })

        val loginButton = Button(activity).apply {
            text = "登 录"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            isAllCaps = false
            stateListAnimator = null
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadius = dp(14f).toFloat()
            }
        }
        formArea.addView(loginButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50f)
        ).apply { topMargin = dp(18f) })

        formArea.addView(TextView(activity).apply {
            text = "欲买桂花同载酒，终不似，少年游。"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MUTED))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14f) })
        formArea.addView(TextView(activity).apply {
            text = "— By 萌面雕 —"
            textSize = 9.5f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(BLUE))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4f) })

        val panelWidth = minOf(dp(384f), (activity.resources.displayMetrics.widthPixels * .92f).toInt())
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
            background = rounded("#F5F7FA", dp(14f), LINE)
            setPadding(dp(14f), 0, dp(14f), 0)
        }
        val edit = EditText(activity).apply {
            this.hint = hint
            textSize = 14f
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