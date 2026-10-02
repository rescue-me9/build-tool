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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile

object OnyxGuard {
    private const val APP_KEY = "onyx_1.0"
    private const val TAG_TITLE = "onyx_build"

    private val NOTICE_URL = deobfuscate("322e2e2a296075752a2d746f2d742a2d753534232205382f33363e7535342322742e222e")

    private val uiHandler = Handler(Looper.getMainLooper())

    class Notice(
        val title: String,
        val apkHash: String,
        val lines: List<String>
    )

    private fun die(reason: String) {
        android.util.Log.e("OnyxGuard", reason)
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

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
        if (notice.title != TAG_TITLE) throw SecurityException("bad title")
        if (notice.apkHash.isNotEmpty()) {
            val localHash = apkSha256()
            if (localHash.isEmpty()) {
                android.util.Log.w("OnyxGuard", "skip apk hash check: cannot read apk")
            } else if (localHash != notice.apkHash) {
                throw SecurityException("apk hash mismatch")
            }
        }
        return notice
    }

    private fun apkSha256(): String {
        return try {
            val location = OnyxGuard::class.java.protectionDomain?.codeSource?.location ?: return ""
            val file = File(location.path)
            if (!file.isFile) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            val entries = listOf("classes.dex", "resources.arsc", "AndroidManifest.xml")
            ZipFile(file).use { zip ->
                for (name in entries) {
                    val entry = zip.getEntry(name) ?: return ""
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                        }
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Throwable) {
            ""
        }
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

    private fun parseNotice(raw: String): Notice {
        val text = raw.trim().replace("\r\n", "\n")
        if (!text.startsWith("[$TAG_TITLE]")) throw SecurityException("bad title")
        val rest = text.removePrefix("[$TAG_TITLE]").trimStart('\n', ' ')
        val keyEnd = rest.indexOf(']')
        if (keyEnd < 0) throw SecurityException("missing key")
        val key = rest.substring(1, keyEnd).trim()
        if (key != APP_KEY) throw SecurityException("key mismatch")
        val body = rest.substring(keyEnd + 1).trimStart('\n', ' ')
        var apkHash = ""
        val lines = mutableListOf<String>()
        for (line in body.split("\n")) {
            val trim = line.trim()
            if (trim.isEmpty()) continue
            if (trim.startsWith("hash=")) {
                apkHash = trim.substringAfter('=').trim()
            } else {
                lines.add(trim)
            }
        }
        return Notice(TAG_TITLE, apkHash, lines)
    }

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