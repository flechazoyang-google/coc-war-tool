package com.cocwar.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.BuildConfig
import com.cocwar.CocWarApplication
import com.cocwar.data.migrate.DataMigrator
import com.cocwar.data.migrate.MigrationPlan
import com.cocwar.data.migrate.MigrationResult
import com.cocwar.data.update.UpdateChecker
import com.cocwar.data.update.UpdateInfo
import com.cocwar.service.FloatingBallService
import com.cocwar.service.ScreenCaptureService
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.ThemeStyle
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(
    onSync: () -> Unit = {},
    themeStyle: ThemeStyle = ThemeStyle.LEDGER,
    onThemeChange: (ThemeStyle) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 截图设置
    val prefs = remember { context.getSharedPreferences("cocwar_capture", Context.MODE_PRIVATE) }
    var stepPercent by remember { mutableFloatStateOf(prefs.getFloat("swipe_step_percent", 30f)) }
    var cleanDays by remember { mutableIntStateOf(prefs.getInt("clean_days", 7)) }

    // 弹窗状态
    var showJsonFormatDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showScreenshotGallery by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    // 待写入文件的导出 JSON（SAF 选择保存位置后写入）
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    // 待写入文件的导出 CSV（B2，SAF 选择保存位置后写入）
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    // 数据迁移修复：预览计划 / 执行结果 / 执行中标记
    var migrationPlan by remember { mutableStateOf<MigrationPlan?>(null) }
    var migrationResult by remember { mutableStateOf<MigrationResult?>(null) }
    var migrationBusy by remember { mutableStateOf(false) }

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

    // 导出备份：SAF 选择保存位置后写入 JSON 文件
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法打开输出流")
                    }
                }.onSuccess {
                    Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导出 CSV（B2）：SAF 选择保存位置后写入 CSV 文件
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri != null && csv != null) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法打开输出流")
                    }
                }.onSuccess {
                    Toast.makeText(context, "CSV 已导出", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 从备份文件导入：选文件后先校验，再完整还原
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val json = context.contentResolver.openInputStream(it)
                            ?.bufferedReader()?.use { r -> r.readText() } ?: ""
                        val app = context.applicationContext as CocWarApplication
                        if (!app.repository.validateBackupJson(json)) {
                            throw IllegalStateException("所选文件不是有效的备份 JSON")
                        }
                        app.repository.restoreFromBackupJson(json)
                    }
                }.onSuccess {
                    Toast.makeText(context, "备份导入成功", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "工具",
            overline = "设置与辅助",
            subtitle = "外观 · 数据 · 截图 · 关于"
        )

        // ── 外观：主题选择 ──
        SectionTitleWithPadding("外观")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                ThemeStyle.entries.forEach { style ->
                    ThemeChip(
                        modifier = Modifier.weight(1f),
                        style = style,
                        selected = style == themeStyle,
                        onClick = { onThemeChange(style) }
                    )
                }
            }
        }

        // ── 数据管理 ──
        SectionTitleWithPadding("数据管理")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column {
                ToolsRow(
                    icon = Icons.Filled.Cloud,
                    title = "云端同步 (WebDAV)",
                    subtitle = "上传/下载备份到坚果云等",
                    onClick = onSync
                )
                ToolsDivider()
                ToolsRow(
                    icon = Icons.Filled.SaveAlt,
                    title = "导出所有数据",
                    subtitle = "导出全量战报与名单为备份 JSON 文件",
                    onClick = {
                        scope.launch {
                            val app = context.applicationContext as CocWarApplication
                            // JSON 拼接在 IO 线程执行，避免大数据量时卡主线程
                            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                app.repository.exportAllDataJson()
                            }
                            pendingExportJson = json
                            val ts = java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss", java.util.Locale.US
                            ).format(java.util.Date())
                            exportLauncher.launch("coc_war_backup_$ts.json")
                        }
                    }
                )
                ToolsDivider()
                ToolsRow(
                    icon = Icons.Filled.GridOn,
                    title = "导出 CSV 表格",
                    subtitle = "全部战报导出为 CSV（Excel/WPS 可直接打开）",
                    onClick = {
                        scope.launch {
                            val app = context.applicationContext as CocWarApplication
                            val csv = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                app.repository.exportAllEventsCsv()
                            }
                            pendingExportCsv = csv
                            val ts = java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss", java.util.Locale.US
                            ).format(java.util.Date())
                            csvExportLauncher.launch("coc_war_events_$ts.csv")
                        }
                    }
                )
                ToolsDivider()
                ToolsRow(
                    icon = Icons.Filled.FileOpen,
                    title = "从备份导入",
                    subtitle = "选择备份 JSON 文件完整还原（会覆盖当前数据）",
                    onClick = { showRestoreConfirm = true }
                )
                ToolsDivider()
                ToolsRow(
                    icon = Icons.Filled.SystemUpdateAlt,
                    title = "数据迁移修复",
                    subtitle = "将旧版联赛战报名称升级为新编码，迁移前自动备份",
                    onClick = {
                        if (migrationBusy) return@ToolsRow
                        scope.launch {
                            migrationBusy = true
                            try {
                                val app = context.applicationContext as CocWarApplication
                                val migrator = DataMigrator(app.database.warDao(), app.repository)
                                val plan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    migrator.scan()
                                }
                                if (plan.items.isEmpty()) {
                                    Toast.makeText(context, "数据已是最新结构，无需迁移", Toast.LENGTH_SHORT).show()
                                } else {
                                    migrationPlan = plan
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "迁移扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                migrationBusy = false
                            }
                        }
                    }
                )
                ToolsDivider()
                ToolsRow(
                    icon = Icons.Filled.Info,
                    title = "JSON 格式示例",
                    subtitle = "查看并复制标准战报 JSON 格式",
                    onClick = { showJsonFormatDialog = true }
                )
            }
        }

        // ── 截图工具 ──
        SectionTitleWithPadding("截图工具")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // 悬浮球开关
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CameraAlt, null, Modifier.size(20.dp),
                        tint = if (isBallRunning) MaterialTheme.cocColors.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("悬浮球", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(1.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isBallRunning) MaterialTheme.cocColors.accent
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (isBallRunning) "运行中" else "未启动",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBallRunning) MaterialTheme.cocColors.accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isBallRunning,
                        onCheckedChange = { want ->
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
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.cocColors.accent,
                            checkedThumbColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                ToolsDivider(horizontal = 0.dp)

                // 滑动步长
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

                // 自动清理
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

                // 操作按钮行
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScreenshotGallery = true },
                        modifier = Modifier.weight(1f),
                        shape = CocShape.field,
                        border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
                    ) { Text("查看截图") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ScreenCaptureService.cleanAllScreenshotsAsync(context)
                                Toast.makeText(context, "截图已全部清理", Toast.LENGTH_SHORT).show()
                            }
                        },
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
        }

        // ── 关于 ──
        SectionTitleWithPadding("关于")
        CocCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            ToolsRow(
                icon = Icons.Filled.SystemUpdateAlt,
                title = "检查更新",
                subtitle = "当前版本 ${BuildConfig.VERSION_NAME}",
                onClick = {
                    scope.launch {
                        val result = UpdateChecker.check(context)
                        result.fold(
                            onSuccess = { info ->
                                if (info != null) updateInfo = info
                                else Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "检查失败：${e.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    // ── JSON 格式示例弹窗 ──
    if (showJsonFormatDialog) {
        val jsonSample = """{
  "members": [
    {
      "player_name": "陈平安",
      "total_stars": 6,
      "attacks": [
        { "attack_order": 1, "destruction_percentage": 100 },
        { "attack_order": 2, "destruction_percentage": 0 }
      ]
    }
  ]
}"""
        AlertDialog(
            onDismissRequest = { showJsonFormatDialog = false },
            title = { Text("JSON 数据格式") },
            text = {
                Column {
                    Text(jsonSample, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "说明：未进攻成员的攻击记录可省略，系统自动补占位；" +
                        "摧毁率为 0 视为未进攻；职位在「成员」页花名册中设置，无需填写 rank/role/status。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("json", jsonSample))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showJsonFormatDialog = false
                }) { Text("复制") }
            },
            confirmButton = {
                TextButton(onClick = { showJsonFormatDialog = false }) { Text("关闭") }
            }
        )
    }

    // ── 从备份导入确认 ──
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从备份导入") },
            text = {
                Text("将清空当前全部战报与名单，并用备份文件内容完整还原。\n\n确定继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restorePicker.launch("application/json")
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }

    // ── 数据迁移修复：确认 ──
    migrationPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { migrationPlan = null },
            title = { Text("数据迁移修复") },
            text = {
                Column {
                    Text(
                        "发现 ${plan.items.size} 条旧版联赛战报名称需要迁移" +
                            if (plan.overflowCount > 0) "（其中 ${plan.overflowCount} 条超出编码范围，将标记为无效名称）" else "" +
                            "。\n\n执行前将自动备份全部数据到应用备份目录，备份可用于「从备份导入」还原。\n\n预览："
                    )
                    plan.items.take(3).forEach { item ->
                        Text("${item.oldName}  →  ${item.newName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.items.size > 3) {
                        Text("…共 ${plan.items.size} 条", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    migrationPlan = null
                    scope.launch {
                        migrationBusy = true
                        try {
                            val app = context.applicationContext as CocWarApplication
                            val migrator = DataMigrator(app.database.warDao(), app.repository)
                            val backupDir = context.getExternalFilesDir("backups") ?: context.filesDir
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                migrator.execute(backupDir)
                            }
                            migrationResult = result
                        } catch (e: Exception) {
                            Toast.makeText(context, "迁移失败：${e.message}（数据未改动）", Toast.LENGTH_LONG).show()
                        } finally {
                            migrationBusy = false
                        }
                    }
                }) { Text("确认迁移") }
            },
            dismissButton = {
                TextButton(onClick = { migrationPlan = null }) { Text("取消") }
            }
        )
    }

    // ── 数据迁移修复：结果 ──
    migrationResult?.let { result ->
        AlertDialog(
            onDismissRequest = { migrationResult = null },
            title = { Text("迁移完成") },
            text = {
                Column {
                    Text("成功迁移 ${result.migrated} 条，跳过 ${result.skipped} 条，溢出 ${result.overflow} 条。")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "迁移前备份已保存到：\n${result.backupPath}\n\n" +
                            "该备份为迁移前的完整数据，可通过「从备份导入」随时还原。"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { migrationResult = null }) { Text("知道了") }
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

    // ── 更新对话框 ──
    updateInfo?.let { info ->
        var downloading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text("当前版本：${BuildConfig.VERSION_NAME}")
                    Text("最新版本：${info.version}", fontWeight = FontWeight.Bold)
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
                            onSuccess = { updateInfo = null },
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
            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("以后再说") } }
        )
    }

    // ── 截图查看弹窗 ──
    if (showScreenshotGallery) {
        ScreenshotGalleryDialog(onDismiss = { showScreenshotGallery = false })
    }
}

@Composable
private fun SectionTitleWithPadding(title: String) {
    SectionTitle(title, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun ToolsDivider(horizontal: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(
        Modifier
            .padding(horizontal = horizontal)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.cocColors.hairline)
    )
}

/** 主题选择 chip：双色徽章 + 名称，横向紧凑排列；选中项徽章描边 + 名称高亮 */
@Composable
private fun ThemeChip(
    modifier: Modifier = Modifier,
    style: ThemeStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(34.dp)
                .then(
                    if (selected) Modifier.border(2.dp, MaterialTheme.cocColors.accent, CocShape.panel)
                    else Modifier
                )
                .clip(CocShape.panel)
                .background(style.palette(false).accent),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(style.palette(true).accent)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            style.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.cocColors.accent
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 分组列表行：图标 + 标题/副标题 + 尾端箭头，整行可点 */
@Composable
private fun ToolsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
