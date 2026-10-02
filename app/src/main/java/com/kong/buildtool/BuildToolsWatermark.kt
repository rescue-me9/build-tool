package com.kong.buildtool

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * In-game floating switch. It carries the small app artwork inside a white
 * square frame, and opens the Kotlin building-tools panel when tapped.
 */
object BuildToolsWatermark {
    private const val VIEW_TAG = "build-tools-local-watermark"
    private const val PREFS = "build_tools_watermark_position"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    // 96x96 RGBA PNG, base64-encoded so the icon survives inside the hooked
    // host process where module resources cannot be resolved.
    private const val ICON_BASE64 = "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"

    @Volatile private var iconCache: Bitmap? = null

    private fun icon(): Bitmap? {
        iconCache?.let { return it }
        val decoded = runCatching {
            val bytes = Base64.decode(ICON_BASE64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
        iconCache = decoded
        return decoded
    }

    fun install(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<ImageView>(VIEW_TAG) != null) return

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val preferences = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val watermark = ImageView(activity).apply {
            tag = VIEW_TAG
            contentDescription = "打开建筑工具"
            // 白色圆角方形外壳，图标居中带内边距
            val frame = dp(52)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), Color.rgb(190, 205, 235))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(icon())
            layoutParams = FrameLayout.LayoutParams(frame, frame)
            setOnClickListener { BuildToolsLauncher.openPanel(activity) }
        }

        var positionX = 0
        var positionY = 0
        var positioned = false
        fun moveTo(x: Int, y: Int, save: Boolean) {
            val maxX = (root.width - watermark.width).coerceAtLeast(0)
            val maxY = (root.height - watermark.height).coerceAtLeast(0)
            positionX = x.coerceIn(0, maxX)
            positionY = y.coerceIn(0, maxY)
            val params = watermark.layoutParams as? FrameLayout.LayoutParams ?: return
            params.gravity = Gravity.TOP or Gravity.START
            params.setMargins(positionX, positionY, 0, 0)
            watermark.layoutParams = params
            positioned = true
            if (save) {
                preferences.edit().putInt(KEY_X, positionX).putInt(KEY_Y, positionY).apply()
            }
        }

        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
        watermark.setOnTouchListener(object : View.OnTouchListener {
            private var tracking = false
            private var dragging = false
            private var cancelled = false
            private var downRawX = 0f
            private var downRawY = 0f
            private var downTime = 0L
            private var startX = 0
            private var startY = 0
            private val beginDrag = Runnable {
                if (tracking && !cancelled && watermark.isAttachedToWindow) {
                    dragging = true
                    watermark.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tracking = true
                        dragging = false
                        cancelled = false
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downTime = event.eventTime
                        // The initial layout is BOTTOM|END. Convert its actual
                        // location to TOP|START only when moving it, without a jump.
                        startX = if (positioned) positionX else watermark.left
                        startY = if (positioned) positionY else watermark.top
                        view.isPressed = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.postDelayed(beginDrag, longPressTimeout)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (tracking) {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                                // A swipe before long press is neither a drag nor a click.
                                cancelled = true
                                view.isPressed = false
                                view.removeCallbacks(beginDrag)
                            }
                            if (dragging) {
                                moveTo(startX + dx.toInt(), startY + dy.toInt(), save = false)
                            }
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                        cancelled = true
                        view.removeCallbacks(beginDrag)
                        if (dragging) moveTo(positionX, positionY, save = true)
                        dragging = false
                        view.isPressed = false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.removeCallbacks(beginDrag)
                        view.isPressed = false
                        val wasDragging = dragging
                        if (wasDragging) moveTo(positionX, positionY, save = true)
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        val click = tracking && !wasDragging && !cancelled &&
                            event.actionMasked == MotionEvent.ACTION_UP &&
                            event.eventTime - downTime < longPressTimeout &&
                            dx * dx + dy * dy <= touchSlop * touchSlop
                        tracking = false
                        dragging = false
                        if (click) view.performClick()
                    }
                }
                return true
            }
        })
        root.addView(
            watermark,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(dp(12), dp(12), dp(18), dp(38))
            }
        )
        // Keep the original bottom-right anchor until the view has a real
        // size. A posted callback can run before its first layout and would
        // otherwise restore the default position as (0, 0).
        if (preferences.contains(KEY_X) && preferences.contains(KEY_Y)) {
            watermark.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    view: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    if (root.width <= 0 || right <= left || bottom <= top) return
                    view.removeOnLayoutChangeListener(this)
                    moveTo(
                        preferences.getInt(KEY_X, left),
                        preferences.getInt(KEY_Y, top),
                        save = false
                    )
                }
            })
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom ->
            if (positioned && (right - left != oldRight - oldLeft ||
                    bottom - top != oldBottom - oldTop)) {
                moveTo(positionX, positionY, save = false)
            }
        }
        if (isBigWatermarkEnabled(activity.applicationContext)) {
            installBigWatermark(activity, currentAccount(activity.applicationContext))
        }
    }

    private const val BIG_PREFS = "build_tools_watermark_state"
    private const val KEY_BIG = "big_watermark_enabled"
    private const val BIG_TAG = "build-tools-big-watermark"

    fun maskAccount(raw: String): String {
        if (raw.isEmpty()) return "未登录"
        if (raw.length == 1) return raw
        return raw.first() + "*****" + raw.last()
    }

    fun currentAccount(context: Context): String {
        val raw = context.getSharedPreferences("onyx_auth", Context.MODE_PRIVATE)
            .getString("username", "") ?: ""
        return maskAccount(raw)
    }

    fun isBigWatermarkEnabled(context: Context): Boolean =
        context.getSharedPreferences(BIG_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIG, false)

    fun setBigWatermark(activity: Activity, enabled: Boolean) {
        val context = activity.applicationContext
        context.getSharedPreferences(BIG_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BIG, enabled).apply()
        if (enabled) installBigWatermark(activity, currentAccount(context)) else removeBigWatermark(activity)
    }

    fun installBigWatermark(activity: Activity, account: String) {
        val root = activity.window.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<TextView>(BIG_TAG) != null) return
        val tv = TextView(activity).apply {
            tag = BIG_TAG
            text = "onyx_build用户\n$account"
            textSize = 54f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.argb(80, 0, 0, 0))
            setLineSpacing(0f, 1f)
            isClickable = false
        }
        root.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))
    }

    fun removeBigWatermark(activity: Activity) {
        val root = activity.window.decorView as? ViewGroup ?: return
        root.findViewWithTag<View>(BIG_TAG)?.let { root.removeView(it) }
    }
}
