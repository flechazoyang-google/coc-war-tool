package com.cocwar.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.cocwar.service.ScreenCaptureService
import com.cocwar.service.FloatingBallService

/**
 * 检查所有必要权限和服务的状态。
 */
data class CapturePermissionState(
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false
) {
    val allReady: Boolean get() = overlayGranted && accessibilityEnabled
}

fun checkCapturePermissions(context: Context): CapturePermissionState {
    val overlayGranted = Settings.canDrawOverlays(context)
    val a11yEnabled = ScreenCaptureService.isAccessibilityServiceEnabled(context)
    return CapturePermissionState(overlayGranted, a11yEnabled)
}

/**
 * 权限引导弹窗：当悬浮窗权限或无障碍服务未开启时弹出。
 */
@Composable
fun PermissionGuideDialog(
    state: CapturePermissionState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val missingParts = buildList {
        if (!state.overlayGranted) add("悬浮窗权限")
        if (!state.accessibilityEnabled) add("无障碍服务")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要开启以下权限") },
        text = {
            Text("使用截图功能需要开启：${missingParts.joinToString("、")}\n\n" +
                    "1. 悬浮窗权限：允许在游戏上方显示截图按钮\n" +
                    "2. 无障碍服务：允许自动截图和滑动")
        },
        confirmButton = {
            TextButton(onClick = {
                // 跳转系统设置，不关闭弹窗 — 用户返回后 recompose 会刷新状态
                if (!state.accessibilityEnabled) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else if (!state.overlayGranted) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }) {
                Text("去开启")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // 检查权限状态并给出反馈
                val current = checkCapturePermissions(context)
                if (!current.allReady) {
                    val stillMissing = buildList {
                        if (!current.overlayGranted) add("悬浮窗权限")
                        if (!current.accessibilityEnabled) add("无障碍服务")
                    }
                    Toast.makeText(context, "仍缺少：${stillMissing.joinToString("、")}，请先开启后再试", Toast.LENGTH_LONG).show()
                }
                onDismiss()
            }) { Text("取消") }
        }
    )
}
