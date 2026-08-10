package com.cocwar.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.BuildConfig
import com.cocwar.data.update.UpdateChecker
import com.cocwar.data.update.UpdateInfo
import kotlinx.coroutines.launch

/**
 * 更新提示弹窗：版本 / 更新说明（预览版带「（预览版）」标识）/ 立即更新（下载并安装）/ 以后再说。
 * 工具页、更新设置页、启动自动检查共用。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("当前版本：${BuildConfig.VERSION_NAME}")
                Text(
                    "最新版本：${info.version}${if (info.isPrerelease) "（预览版）" else ""}",
                    fontWeight = FontWeight.Bold
                )
                if (info.body.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("更新内容：", style = MaterialTheme.typography.labelMedium)
                    Text(info.body, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (downloading) {
                    Spacer(Modifier.height(8.dp))
                    Text("正在下载，请查看通知栏进度…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                downloading = true
                scope.launch {
                    val result = UpdateChecker.downloadAndInstall(context, info)
                    result.fold(
                        onSuccess = { onDismiss() },
                        onFailure = { e ->
                            downloading = false
                            Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }, enabled = !downloading) {
                Text(if (downloading) "下载中…" else "立即更新")
            }
        },
        dismissButton = { TextButton(onClick = { if (!downloading) onDismiss() }) { Text("以后再说") } }
    )
}
