package com.kong.buildtool

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Sidebar + content panel shown inside the hooked game process (Pianaixel style). */
object BuildToolsPanelPopup {
    private const val INK = "#101828"
    private const val MUTED = "#667085"
    private const val LINE = "#E4E7EC"
    private const val BLUE = "#2563EB"

    private class ToolEntry(
        val tool: BuildToolsLauncher.Tool,
        val label: String,
        val subtitle: String,
        val points: List<String>
    )

    private val tools = listOf(
        ToolEntry(BuildToolsLauncher.Tool.IMPORT, "建筑导入", "导入建筑模型文件", listOf(
            "支持 BDX / Litematic / Schematic 格式",
            "可设置放置速度与坐标偏移",
            "支持撤销与续传恢复"
        )),
        ToolEntry(BuildToolsLauncher.Tool.PIXEL_ART, "图片导入", "将图片转换为方块画", listOf(
            "支持 PNG / JPG 图片",
            "自动映射方块颜色",
            "导入后生成地图画或实体方块"
        )),
        ToolEntry(BuildToolsLauncher.Tool.EXPORT, "建筑导出", "导出工程文件数据", listOf(
            "框选区域导出建筑结构",
            "自动扫描容器物品",
            "支持大区域分批导出"
        )),
        ToolEntry(BuildToolsLauncher.Tool.PROJECTION, "建筑投影", "生成建筑投影图纸", listOf(
            "在游戏内渲染半透明投影",
            "可调整图层范围与透明度",
            "支持投影打印机自动放置"
        ))
    )

    private var activeDialog: Dialog? = null

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        activeDialog?.let { if (it.isShowing) return }

        val metrics = activity.resources.displayMetrics
        val density = metrics.density
        fun dp(value: Float) = (value * density + .5f).toInt()

        val panelWidth = minOf(dp(600f), (metrics.widthPixels * .94f).toInt())
        val panelHeight = minOf(dp(360f), (metrics.heightPixels * .9f).toInt())
        val narrow = panelWidth < dp(440f)
        val short = panelHeight < dp(280f)

        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(panelWidth, panelHeight)
            background = rounded("#FFFFFF", dp(22f), LINE)
            elevation = dp(12f).toFloat()
        }

        // ---- 左侧白色导航栏 ----
        val side = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#FFFFFF", dp(22f), null)
            setPadding(dp(14f), dp(16f), dp(14f), dp(14f))
        }
        root.addView(side, LinearLayout.LayoutParams(dp(if (narrow) 96f else 118f), ViewGroup.LayoutParams.MATCH_PARENT))

        side.addView(TextView(activity).apply {
            text = "ONYX_BUILD"
            textSize = 8.5f
            letterSpacing = .16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#101828"))
            maxLines = 1
        })

        val navList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        side.addView(navList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12f) })

        val navLabels = tools.map { it.label }
        val navViews = mutableListOf<TextView>()
        // Bound lazily after the views they need are built.
        var refreshSelection: (Int) -> Unit = { }
        var renderContent: () -> Unit = { }
        var selectedIndex = 0

        for (index in navLabels.indices) {
            val item = TextView(activity).apply {
                text = navLabels[index]
                textSize = if (narrow) 11.5f else 12.5f
                gravity = Gravity.CENTER_VERTICAL
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.parseColor("#101828"))
                setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFFFFF"))
                    cornerRadius = dp(12f).toFloat()
                    setStroke(dp(1f), Color.parseColor("#E4E7EC"))
                }
            }
            navList.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f) })
            navViews.add(item)
            item.setOnClickListener {
                selectedIndex = index
                refreshSelection(index)
                renderContent()
            }
        }

        side.addView(TextView(activity).apply {
            text = "By 萌面雕"
            textSize = 8.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#101828"))
        })

        // ---- 右侧白色内容区 ----
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(16f))
        }
        root.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val contentTitle = TextView(activity).apply {
            textSize = if (narrow) 16f else 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(INK))
        }
        content.addView(contentTitle)

        val contentSubtitle = TextView(activity).apply {
            textSize = 10.5f
            setTextColor(Color.parseColor(MUTED))
        }
        content.addView(contentSubtitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2f) })

        val detailCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = rounded("#F8FAFC", dp(14f), null)
        }
        content.addView(detailCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12f) })

        refreshSelection = { index ->
            for (i in navViews.indices) {
                val selected = i == index
                navViews[i].setTextColor(Color.parseColor("#101828"))
                navViews[i].typeface = Typeface.create(
                    "sans-serif-medium",
                    if (selected) Typeface.BOLD else Typeface.NORMAL
                )
                navViews[i].background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFFFFF"))
                    cornerRadius = dp(12f).toFloat()
                    setStroke(
                        dp(if (selected) 2f else 1f),
                        Color.parseColor(if (selected) "#2563EB" else "#E4E7EC")
                    )
                }
            }
        }
        renderContent = {
            val entry = tools[selectedIndex]
            contentTitle.text = entry.label
            contentSubtitle.text = entry.subtitle
            detailCard.removeAllViews()
            for (point in entry.points) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(activity).apply {
                    text = "●"
                    textSize = 7f
                    setTextColor(Color.parseColor(BLUE))
                    setPadding(dp(2f), 0, dp(2f), 0)
                })
                row.addView(TextView(activity).apply {
                    text = point
                    textSize = 11f
                    setTextColor(Color.parseColor("#344054"))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                detailCard.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(if (short) 3f else 7f) })
            }
        }

        val openButton = TextView(activity).apply {
            text = "打 开"
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadius = dp(13f).toFloat()
            }
            isClickable = true
        }
        content.addView(openButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)
        ).apply { topMargin = dp(12f) })
        openButton.setOnClickListener {
            dialog.dismiss()
            BuildToolsLauncher.open(activity, tools[selectedIndex].tool)
        }

        val closeButton = TextView(activity).apply {
            text = "×"
            contentDescription = "关闭"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.parseColor(MUTED))
            background = rounded("#F2F4F7", dp(10f), null)
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        content.addView(closeButton, LinearLayout.LayoutParams(
            dp(30f), dp(30f)
        ).apply { topMargin = dp(6f); gravity = Gravity.END })

        refreshSelection(0)
        renderContent()

        dialog.setContentView(root)
        dialog.setOnDismissListener { if (activeDialog === dialog) activeDialog = null }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .5f }
            setLayout(panelWidth, panelHeight)
        }
        activeDialog = dialog
    }

    private fun rounded(fill: String, radius: Int, stroke: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = radius.toFloat()
        stroke?.let { setStroke(1, Color.parseColor(it)) }
    }
}
