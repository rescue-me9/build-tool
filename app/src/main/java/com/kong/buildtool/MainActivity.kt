package com.kong.buildtool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kong.buildtool.ui.theme.OnyxBuildTheme

private const val DISCORD_URL = "https://discord.gg/Y93thMHNt2"
private const val QQ_GROUP = "1081622107"

/** 模块未加载到游戏时的独立启动页。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            OnyxBuildTheme(darkTheme = false, dynamicColor = false) {
                OnyxWelcome()
            }
        }
    }
}

@Composable
private fun OnyxWelcome() {
    val context = LocalContext.current
    val ink = Color(0xFF1B2735)
    val muted = Color(0xFF6B7A90)
    val blue = Color(0xFF2E6FD8)
    val blueDeep = Color(0xFF1F4FA8)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FD)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(modifier = Modifier.widthIn(max = 460.dp)) {
                // 顶部标识
                Text(
                    text = "ONYX_BUILD",
                    color = blue,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "欢迎使用Onyx_Build",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "为建筑创作准备的导入、投影与导出工具。请通过 XP/LSPosed 加载到游戏后使用。",
                    color = muted,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(26.dp))

                // 加入官方服务器按钮
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = blue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "点击加入官方服务器",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = DISCORD_URL,
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(24.dp))

                // Q 群提示卡片
                Surface(
                    color = Color(0xFFE4EEFC),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFC9DCF6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    Brush.linearGradient(listOf(blue, blueDeep)),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Spacer(Modifier.size(13.dp))
                        Column {
                            Text(
                                text = "加入用户组件的Q群吧 $QQ_GROUP",
                                color = ink,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "获取更新通知与使用交流",
                                color = muted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // 功能速览
                FeatureRow("01", "建筑导入", "导入建筑模型与图片画，支持断点恢复与校验修复")
                Spacer(Modifier.height(9.dp))
                FeatureRow("02", "建筑投影", "预览结构与材料清单，按需开启投影打印机")
                Spacer(Modifier.height(9.dp))
                FeatureRow("03", "建筑导出", "选取角点保存建筑，支持分区扫描与进度跟踪")
            }
        }
    }
}

@Composable
private fun FeatureRow(index: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F9FE), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFD8E2F0), RoundedCornerShape(14.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = index,
            color = Color(0xFF2E6FD8),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 2.dp, end = 14.dp)
        )
        Column {
            Text(
                text = title,
                color = Color(0xFF1B2735),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = Color(0xFF6B7A90),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
