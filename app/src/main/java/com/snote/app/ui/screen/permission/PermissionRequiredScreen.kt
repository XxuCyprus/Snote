package com.snote.app.ui.screen.permission

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snote.app.R

/**
 * 全屏权限申请界面 - 无法跳过，必须授予权限才能使用
 */
@Composable
fun PermissionRequiredScreen(
    onPermissionGranted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 禁用返回键，防止用户绕过权限检查
    BackHandler(enabled = true) { /* 阻止返回 */ }

    // 监听权限变化
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (hasFullStorageAccess()) {
                onPermissionGranted()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标 - APP 图标
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_app_icon),
                    contentDescription = "Snote",
                    modifier = Modifier.size(72.dp),
                    tint = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "需要存储权限",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Snote 需要「所有文件访问权限」来保存您的笔记数据。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "\n    此权限确保：",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                lineHeight = 24.sp
            )

            Text(
                text = "  · 笔记数据安全存储在设备上\n" +
                       "  · 卸载重装后数据可恢复\n" +
                       "  · 图片、视频、音频正常保存",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "授予权限",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "点击上方按钮前往系统设置授予权限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun hasFullStorageAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}
