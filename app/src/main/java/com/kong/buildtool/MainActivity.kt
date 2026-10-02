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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kong.buildtool.ui.theme.OnyxBuildTheme

private const val DISCORD_URL = "https://discord.gg/34cWFHTqkr"
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
        // 打开 App 即过启动守卫：公告加载失败/版本不符/签名被改都会闪退。
        OnyxGuard.verifyAsync(applicationContext) { notice ->
            OnyxGuard.showNoticeCard(this, notice) {
                showWelcome()
            }
        }
    }

    private fun showWelcome() {
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
    val ink = Color(0xFF101828)
    val muted = Color(0xFF667085)
    val blue = Color(0xFF2563EB)
    val blueDeep = Color(0xFF1E40AF)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(modifier = Modifier.widthIn(max = 480.dp)) {
                // 顶部蓝色形象区
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(blueDeep, blue)),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Text(
                            text = "ONYX_BUILD",
                            color = Color(0xFFBFDBFE),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "欢迎使用 Onyx_Build",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "为建筑创作准备的导入、投影与导出工具。",
                            color = Color(0xFFDBEAFE),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "请通过 XP/LSPosed 加载到游戏后使用。",
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(22.dp))

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
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE4E7EC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Brush.linearGradient(listOf(blueDeep, blue)),
                                    RoundedCornerShape(12.dp)
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
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "— By 萌面雕 —",
                    color = blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}