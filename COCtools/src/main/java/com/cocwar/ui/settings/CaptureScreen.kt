package com.cocwar.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cocwar.service.FloatingBallService
import com.cocwar.service.ScreenCaptureService
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SettingsRow
import com.cocwar.ui.theme.cocColors
import kotlinx.coroutines.launch

/**
 * 设置-截图工具页：悬浮球开关 / 滑动步长 / 自动清理 / 查看截图 / 清理全部
 * （原工具页「截图工具」区块迁移）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 截图设置
    val prefs = remember { context.getSharedPreferences("cocwar_capture", Context.MODE_PRIVATE) }
    var stepPercent by remember { mutableFloatStateOf(prefs.getFloat("swipe_step_percent", 30f)) }
    var cleanDays by remember { mutableIntStateOf(prefs.getInt("clean_days", 7)) }

    // 弹窗状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showScreenshotGallery by remember { mutableStateOf(false) }
    var showCleanScreenshotsConfirm by remember { mutableStateOf(false) }

    // 悬浮球与权限状态：用响应式 state，并在返回页面时（onResume）重新读取，
    // 解决「开启后状态不刷新」「授予权限后弹窗不消失」的问题。
    var isBallRunning by remember { mutableStateOf(FloatingBallService.isRunning()) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yEnabled by remember { mutableStateOf(ScreenCaptureService.isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val og = Settings.canDrawOverlays(context)
                val ae = ScreenCaptureService.isAccessibilityServiceEnabled(context)
                overlayGranted = og
                a11yEnabled = ae
                isBallRunning = FloatingBallService.isRunning()
                // 若权限引导弹窗仍在、且权限已齐备，则自动开启悬浮球并关闭弹窗
                if (showPermissionDialog && og && ae) {
                    FloatingBallService.start(context)
                    isBallRunning = true
                    showPermissionDialog = false
                    Toast.makeText(context, "悬浮球已开启", Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleBall(want: Boolean) {
        if (want) {
            if (!overlayGranted || !a11yEnabled) {
                showPermissionDialog = true
            } else {
                FloatingBallService.start(context)
                isBallRunning = true
                Toast.makeText(context, "悬浮球已开启", Toast.LENGTH_SHORT).show()
            }
        } else {
            FloatingBallService.stop(context)
            isBallRunning = false
            Toast.makeText(context, "悬浮球已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("截图工具", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SectionTitle("悬浮球")
            CocCard(Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Filled.CameraAlt,
                    iconColor = if (isBallRunning) MaterialTheme.cocColors.accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "悬浮球",
                    subtitle = if (isBallRunning) "运行中" else "未启动",
                    onClick = { toggleBall(!isBallRunning) },
                    trailing = {
                        Switch(
                            checked = isBallRunning,
                            onCheckedChange = { toggleBall(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.cocColors.accent,
                                checkedThumbColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    showDivider = false
                )
            }

            SectionTitle("滑动与清理")
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingSlider(
                        label = "滑动步长",
                        valueText = "${stepPercent.toInt()}%",
                        value = stepPercent,
                        onValueChange = { stepPercent = it },
                        onValueChangeFinished = {
                            prefs.edit().putFloat("swipe_step_percent", stepPercent).apply()
                        },
                        valueRange = 10f..55f,
                        steps = 8
                    )
                    SettingSlider(
                        label = "自动清理",
                        valueText = "${cleanDays} 天",
                        value = cleanDays.toFloat(),
                        onValueChange = { cleanDays = it.toInt() },
                        onValueChangeFinished = {
                            prefs.edit().putInt("clean_days", cleanDays).apply()
                        },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
            }

            SectionTitle("截图操作")
            CocCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScreenshotGallery = true },
                        modifier = Modifier.weight(1f),
                        shape = CocShape.field,
                        border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
                    ) { Text("查看截图") }
                    OutlinedButton(
                        onClick = { showCleanScreenshotsConfirm = true },
                        modifier = Modifier.weight(1f),
                        shape = CocShape.field,
                        border = BorderStroke(1.dp, MaterialTheme.cocColors.danger.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(15.dp),
                            tint = MaterialTheme.cocColors.danger)
                        Spacer(Modifier.width(5.dp))
                        Text("清理全部", color = MaterialTheme.cocColors.danger)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // ── 清理全部截图：确认 ──
    if (showCleanScreenshotsConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanScreenshotsConfirm = false },
            title = { Text("清理全部截图") },
            text = { Text("将删除所有已保存的截图，且不可恢复。\n\n确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showCleanScreenshotsConfirm = false
                    scope.launch {
                        ScreenCaptureService.cleanAllScreenshotsAsync(context)
                        Toast.makeText(context, "截图已全部清理", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("清理", color = MaterialTheme.cocColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showCleanScreenshotsConfirm = false }) { Text("取消") }
            }
        )
    }

    // ── 权限引导弹窗 ──
    if (showPermissionDialog) {
        val overlayGranted = Settings.canDrawOverlays(context)
        val a11yEnabled = ScreenCaptureService.isAccessibilityServiceEnabled(context)
        val missingParts = buildList {
            if (!overlayGranted) add("悬浮窗权限")
            if (!a11yEnabled) add("无障碍服务")
        }
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要开启以下权限") },
            text = {
                Text("使用截图功能需要开启：${missingParts.joinToString("、")}\n\n" +
                    "1. 悬浮窗权限：允许在游戏上方显示截图按钮\n" +
                    "2. 无障碍服务：允许自动截图和滑动")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!a11yEnabled) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else if (!overlayGranted) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")))
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val stillOverlay = Settings.canDrawOverlays(context)
                    val stillA11y = ScreenCaptureService.isAccessibilityServiceEnabled(context)
                    val stillMissing = buildList {
                        if (!stillOverlay) add("悬浮窗权限")
                        if (!stillA11y) add("无障碍服务")
                    }
                    if (stillMissing.isNotEmpty()) {
                        Toast.makeText(context, "仍缺少：${stillMissing.joinToString("、")}，请先开启后再试",
                            Toast.LENGTH_LONG).show()
                    }
                    showPermissionDialog = false
                }) { Text("取消") }
            }
        )
    }

    // ── 截图查看弹窗 ──
    if (showScreenshotGallery) {
        ScreenshotGalleryDialog(onDismiss = { showScreenshotGallery = false })
    }
}

/** 设置滑杆：默认收起，点击标签行展开/收起；展开后显示滑杆 */
@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(valueText, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.cocColors.accent)
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (expanded) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.cocColors.accent,
                    activeTrackColor = MaterialTheme.cocColors.accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
