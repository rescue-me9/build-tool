package com.vdl.kong520.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.vdl.kong520.ShortcutsModule
import java.util.Locale

object ShortcutsUi {
    private const val UI_BG = "#F4F6FA"
    private const val UI_SURFACE = "#FFFFFF"
    private const val UI_ACCENT = "#2563EB"
    private const val UI_ACCENT_DARK = "#1E40AF"
    private const val UI_ACCENT_SOFT = "#EFF6FF"
    private const val UI_INK = "#101828"
    private const val UI_MUTED = "#667085"
    private const val UI_LINE = "#E4E7EC"
    private const val UI_GREEN = "#16A34A"
    private const val UI_RED = "#DC2626"
    private const val UI_CATEGORY_BG = "#EFF6FF"
    private const val PREFS = "shortcuts_ui"

    private data class Param(
        val label: String,
        val intIndex: Int = -1,
        val floatIndex: Int = -1,
        val min: Int = 0,
        val max: Int = 100,
        val default: Int = 0,
        val isString: Boolean = false
    )

    private data class Feature(
        val id: Int,
        val name: String,
        val category: String,
        val params: List<Param> = emptyList(),
        val actionLabel: String = ""
    )

    private val features = listOf(
        Feature(0, "自动搭路", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=2),
            Param("方向(0下方/1前方)", intIndex=1, min=0, max=1),
            Param("方块", isString=true)
        )),
        Feature(1, "自动建塔", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=2),
            Param("最大高度(0无限)", intIndex=1, min=0, max=320, default=256),
            Param("方块", isString=true)
        )),
        Feature(2, "斜向楼梯", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=3),
            Param("方向(0自动/1南/2西/3东/4北)", intIndex=1, min=0, max=4),
            Param("方块", isString=true)
        )),
        Feature(3, "自动清障", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=3),
            Param("范围", intIndex=1, min=1, max=5, default=2)
        )),
        Feature(4, "自动地板", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=5),
            Param("范围", intIndex=1, min=1, max=10, default=3),
            Param("方块", isString=true)
        )),
        Feature(5, "自动围栏", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=3),
            Param("高度", intIndex=1, min=1, max=5, default=3),
            Param("方块", isString=true)
        )),
        Feature(6, "自动填平", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=10),
            Param("范围", intIndex=1, min=1, max=8, default=4)
        )),
        Feature(7, "水桥", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=5)
        )),
        Feature(8, "TNT清场", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=200, default=40),
            Param("范围", intIndex=1, min=1, max=4, default=2)
        )),
        Feature(9, "自动出生点", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=600, default=200)
        )),
        Feature(10, "自动修复", "移动建造", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=20),
            Param("范围", intIndex=1, min=1, max=4, default=3)
        )),
        Feature(11, "自动喂食", "自动增益", listOf(
            Param("间隔tick", intIndex=0, min=1, max=600, default=100),
            Param("饱食阈值(0-20)", intIndex=1, min=0, max=20, default=10)
        )),
        Feature(12, "自动拾取", "自动增益", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=10),
            Param("范围", intIndex=1, min=1, max=32, default=8)
        )),
        Feature(13, "安全穹顶", "自动增益", listOf(
            Param("间隔tick", intIndex=0, min=1, max=100, default=10),
            Param("血量阈值", intIndex=1, min=1, max=20, default=10),
            Param("半径", intIndex=2, min=1, max=10, default=5),
            Param("方块", isString=true)
        )),
        Feature(14, "坐标HUD", "信息显示", emptyList()),
        Feature(15, "高度色阶", "渲染效果", listOf(
            Param("模式(0彩虹/1渐变/2热力图)", intIndex=0, min=0, max=2, default=0)
        )),
        Feature(16, "移动轨迹", "渲染效果", listOf(
            Param("最大点数", intIndex=0, min=10, max=500, default=100),
            Param("红R(0-255)", floatIndex=0, min=0, max=255, default=102),
            Param("绿G(0-255)", floatIndex=1, min=0, max=255, default=204),
            Param("蓝B(0-255)", floatIndex=2, min=0, max=255, default=255),
            Param("透明度(0-255)", floatIndex=3, min=0, max=255, default=153)
        )),
        Feature(17, "回家导航", "渲染效果", listOf(
            Param("红R", floatIndex=0, min=0, max=255, default=255),
            Param("绿G", floatIndex=1, min=0, max=255, default=204),
            Param("蓝B", floatIndex=2, min=0, max=255, default=0),
            Param("透明度", floatIndex=3, min=0, max=255, default=204)
        ), actionLabel = "设置家"),
        Feature(18, "区块边界", "渲染效果", listOf(
            Param("范围(区块)", intIndex=0, min=1, max=16, default=4),
            Param("红R", floatIndex=0, min=0, max=255, default=128),
            Param("绿G", floatIndex=1, min=0, max=255, default=128),
            Param("蓝B", floatIndex=2, min=0, max=255, default=255),
            Param("透明度", floatIndex=3, min=0, max=255, default=102)
        )),
        Feature(19, "安全区域", "渲染效果", listOf(
            Param("半径", intIndex=0, min=1, max=50, default=10),
            Param("红R", floatIndex=0, min=0, max=255, default=255),
            Param("绿G", floatIndex=1, min=0, max=255, default=51),
            Param("蓝B", floatIndex=2, min=0, max=255, default=51),
            Param("透明度", floatIndex=3, min=0, max=255, default=128)
        ), actionLabel = "设区域")
    )

    private var dialog: Dialog? = null
    private var hudDialog: Dialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hudTicker: Runnable? = null

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        dialog?.let { if (it.isShowing) return }
        hudDialog?.let { if (it.isShowing) { hudDialog = null; hudTicker = null } }

        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density + .5f).toInt()

        val panelW = minOf(dp(440f), (activity.resources.displayMetrics.widthPixels * .94f).toInt())
        val panelH = minOf(dp(560f), (activity.resources.displayMetrics.heightPixels * .88f).toInt())

        val d = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(UI_BG, dp(20f))
            layoutParams = ViewGroup.LayoutParams(panelW, panelH)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), dp(16f), dp(16f), dp(12f))
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor(UI_ACCENT), Color.parseColor(UI_ACCENT_DARK))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadii = floatArrayOf(dp(20f).toFloat(), dp(20f).toFloat(), 0f, 0f, 0f, 0f, 0f, 0f)
            }
        }
        root.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        header.addView(TextView(activity).apply {
            text = "快捷方式"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = TextView(activity).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded("#FFFFFF22", dp(8f))
            isClickable = true
            setOnClickListener { d.dismiss() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(32f), dp(32f)))

        val scroll = ScrollView(activity).apply {
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val categories = features.groupBy { it.category }
        val featureRows = mutableMapOf<Int, CheckBox>()
        val paramInputs = mutableMapOf<Int, MutableMap<String, View>>()

        for ((category, catFeatures) in categories) {
            val catHeader = TextView(activity).apply {
                text = category
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.parseColor(UI_ACCENT_DARK))
                background = rounded(UI_CATEGORY_BG, dp(8f))
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            }
            content.addView(catHeader, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f); topMargin = dp(4f) })

            for (feat in catFeatures) {
                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
                    background = rounded(UI_SURFACE, dp(12f), UI_LINE)
                }
                content.addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6f) })

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                card.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val cb = CheckBox(activity).apply {
                    text = feat.name
                    textSize = 13.5f
                    setTextColor(Color.parseColor(UI_INK))
                    isChecked = prefs.getBoolean("f${feat.id}", false)
                }
                featureRows[feat.id] = cb
                row.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                if (feat.actionLabel.isNotEmpty()) {
                    val actionBtn = TextView(activity).apply {
                        text = feat.actionLabel
                        textSize = 11f
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor(UI_ACCENT))
                            cornerRadius = dp(8f).toFloat()
                        }
                        setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                        isClickable = true
                        setOnClickListener {
                            try {
                                when (feat.id) {
                                    17 -> ShortcutsModule.setShortcutHome()
                                    19 -> ShortcutsModule.setShortcutSafeZone()
                                }
                                Toast.makeText(activity, feat.actionLabel + " 已设置", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Toast.makeText(activity, "操作失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    row.addView(actionBtn)
                }

                val paramContainer = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = if (cb.isChecked) View.VISIBLE else View.GONE
                }
                card.addView(paramContainer, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6f) })

                val inputMap = mutableMapOf<String, View>()
                paramInputs[feat.id] = inputMap

                for (param in feat.params) {
                    val prow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    prow.addView(TextView(activity).apply {
                        text = param.label
                        textSize = 11f
                        setTextColor(Color.parseColor(UI_MUTED))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                    if (param.isString) {
                        val et = EditText(activity).apply {
                            inputType = InputType.TYPE_CLASS_TEXT
                            textSize = 12f
                            setSingleLine(true)
                            val saved = prefs.getString("f${feat.id}_s", "")
                            setText(if (saved.isNullOrEmpty()) "minecraft:stone" else saved)
                            setTextColor(Color.parseColor(UI_INK))
                            background = rounded("#F8FAFC", dp(6f), UI_LINE)
                            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                        }
                        inputMap["s"] = et
                        prow.addView(et, LinearLayout.LayoutParams(
                            dp(140f), ViewGroup.LayoutParams.WRAP_CONTENT
                        ))
                    } else if (param.intIndex >= 0) {
                        val et = EditText(activity).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            textSize = 12f
                            setSingleLine(true)
                            val saved = prefs.getInt("f${feat.id}_i${param.intIndex}", param.default)
                            setText(saved.toString())
                            setTextColor(Color.parseColor(UI_INK))
                            background = rounded("#F8FAFC", dp(6f), UI_LINE)
                            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                        }
                        inputMap["i${param.intIndex}"] = et
                        prow.addView(et, LinearLayout.LayoutParams(
                            dp(80f), ViewGroup.LayoutParams.WRAP_CONTENT
                        ))
                    } else if (param.floatIndex >= 0) {
                        val et = EditText(activity).apply {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            textSize = 12f
                            setSingleLine(true)
                            val saved = prefs.getInt("f${feat.id}_f${param.floatIndex}", param.default)
                            setText(saved.toString())
                            setTextColor(Color.parseColor(UI_INK))
                            background = rounded("#F8FAFC", dp(6f), UI_LINE)
                            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                        }
                        inputMap["f${param.floatIndex}"] = et
                        prow.addView(et, LinearLayout.LayoutParams(
                            dp(70f), ViewGroup.LayoutParams.WRAP_CONTENT
                        ))
                    }
                    paramContainer.addView(prow, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(4f) })
                }

                cb.setOnCheckedChangeListener { _, isChecked ->
                    paramContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
                    prefs.edit().putBoolean("f${feat.id}", isChecked).apply()
                    try {
                        ShortcutsModule.setShortcutEnabled(feat.id, isChecked)
                        if (isChecked) applyParams(feat.id, inputMap)
                        if (feat.id == 14) {
                            if (isChecked) showHud(activity) else hideHud()
                        }
                    } catch (e: Throwable) {
                        Toast.makeText(activity, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                if (cb.isChecked) {
                    try {
                        ShortcutsModule.setShortcutEnabled(feat.id, true)
                        applyParams(feat.id, inputMap)
                        if (feat.id == 14) showHud(activity)
                    } catch (e: Throwable) { }
                }
            }
        }

        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(16f))
        }
        root.addView(bottomBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val disableAllBtn = TextView(activity).apply {
            text = "全部关闭"
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(UI_RED))
                cornerRadius = dp(10f).toFloat()
            }
            isClickable = true
            setOnClickListener {
                for (feat in features) {
                    try { ShortcutsModule.setShortcutEnabled(feat.id, false) } catch (e: Throwable) {}
                    featureRows[feat.id]?.isChecked = false
                    prefs.edit().putBoolean("f${feat.id}", false).apply()
                }
                hideHud()
                Toast.makeText(activity, "已全部关闭", Toast.LENGTH_SHORT).show()
            }
        }
        bottomBar.addView(disableAllBtn, LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
            marginEnd = dp(6f)
        })

        val doneBtn = TextView(activity).apply {
            text = "完成"
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor(UI_ACCENT), Color.parseColor(UI_ACCENT_DARK))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadius = dp(10f).toFloat()
            }
            isClickable = true
            setOnClickListener {
                for (feat in features) {
                    val inputMap = paramInputs[feat.id]
                    if (inputMap != null) applyParams(feat.id, inputMap)
                }
                d.dismiss()
            }
        }
        bottomBar.addView(doneBtn, LinearLayout.LayoutParams(0, dp(40f), 1f).apply {
            marginStart = dp(6f)
        })

        d.setContentView(root)
        d.setOnDismissListener {
            dialog = null
        }
        d.show()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .4f }
            setLayout(panelW, panelH)
        }
        dialog = d
    }

    private fun applyParams(featureId: Int, inputMap: Map<String, View>) {
        for ((key, view) in inputMap) {
            if (view !is EditText) continue
            val text = view.text.toString().trim()
            if (key == "s") {
                try { ShortcutsModule.setShortcutStringParam(featureId, text) } catch (e: Throwable) {}
            } else if (key.startsWith("i")) {
                val idx = key.substring(1).toIntOrNull() ?: continue
                val value = text.toIntOrNull() ?: 0
                try { ShortcutsModule.setShortcutIntParam(featureId, idx, value) } catch (e: Throwable) {}
            } else if (key.startsWith("f")) {
                val idx = key.substring(1).toIntOrNull() ?: continue
                val value = (text.toIntOrNull() ?: 0).toFloat() / 255f
                try { ShortcutsModule.setShortcutFloatParam(featureId, idx, value) } catch (e: Throwable) {}
            }
        }
    }

    private fun showHud(activity: Activity) {
        if (hudDialog?.isShowing == true) return
        if (activity.isFinishing || activity.isDestroyed) return
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density + .5f).toInt()

        val hudText = TextView(activity).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#80000000"))
                cornerRadius = dp(10f).toFloat()
            }
        }

        val d = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        d.setContentView(hudText)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            attributes = attributes.apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(8f)
                y = dp(8f)
            }
        }
        d.show()
        hudDialog = d

        hudTicker = object : Runnable {
            override fun run() {
                if (d.isShowing) {
                    try {
                        val info = ShortcutsModule.getShortcutPlayerInfo()
                        if (info != null && info.isNotEmpty()) {
                            val parts = info.split(",")
                            if (parts.size >= 3) {
                                val x = parts[0]
                                val y = parts[1]
                                val z = parts[2]
                                val yaw = if (parts.size >= 7) parts[6] else "?"
                                hudText.text = "XYZ: $x / $y / $z\n朝向: ${yaw}°"
                            }
                        }
                    } catch (e: Throwable) {}
                    handler.postDelayed(this, 350)
                }
            }
        }
        handler.post(hudTicker!!)
    }

    private fun hideHud() {
        hudTicker?.let { handler.removeCallbacks(it) }
        hudTicker = null
        hudDialog?.dismiss()
        hudDialog = null
    }

    private fun rounded(fill: String, radius: Int, stroke: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(1, Color.parseColor(it)) }
    }
}
