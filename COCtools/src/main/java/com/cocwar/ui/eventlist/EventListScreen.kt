package com.cocwar.ui.eventlist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.BuildConfig
import com.cocwar.data.db.WarEventEntity
import com.cocwar.di.warViewModel
import com.cocwar.ui.util.parseEventDisplayName
import com.cocwar.ui.util.parseEventTypeFromName
import com.cocwar.ui.util.parseMonthFromName
import com.cocwar.ui.util.parseYearFromName
import com.cocwar.data.update.UpdateChecker
import com.cocwar.data.update.UpdateInfo
import com.cocwar.service.FloatingBallService
import com.cocwar.ui.components.checkCapturePermissions
import com.cocwar.ui.components.PermissionGuideDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onOpen: (String) -> Unit,
    onSync: () -> Unit = {},
    onAiImport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val viewModel: EventListViewModel = warViewModel { EventListViewModel(it) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<WarEventEntity?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSwipeSettingDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 筛选状态
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var yearFilter by remember { mutableStateOf<Int?>(null) }
    var monthFilter by remember { mutableStateOf<Int?>(null) }

    // 下拉展开状态
    var typeExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    val years = remember(events) {
        events.mapNotNull { parseYearFromName(it.eventName) }.distinct().sorted()
    }
    val months = remember(events) {
        events.mapNotNull { parseMonthFromName(it.eventName) }.distinct().sorted()
    }

    val filtered = remember(events, typeFilter, yearFilter, monthFilter) {
        events.filter { event ->
            val t = parseEventTypeFromName(event.eventName)
            val y = parseYearFromName(event.eventName)
            val m = parseMonthFromName(event.eventName)
            (typeFilter == null || t == typeFilter) &&
            (yearFilter == null || y == yearFilter) &&
            (monthFilter == null || m == monthFilter)
        }
    }

        Column(Modifier.fillMaxSize()) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "部落战数据管家",
                    style = MaterialTheme.typography.headlineMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 悬浮窗截图按钮
                    val isBallRunning = FloatingBallService.isRunning()
                    IconButton(onClick = {
                        if (isBallRunning) {
                            FloatingBallService.stop(context)
                            Toast.makeText(context, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                        } else {
                            val permState = checkCapturePermissions(context)
                            if (!permState.allReady) {
                                showPermissionDialog = true
                            } else {
                                FloatingBallService.start(context)
                                Toast.makeText(context, "悬浮窗已开启，正在打开游戏...", Toast.LENGTH_SHORT).show()
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        Toast.makeText(context, "未找到部落冲突应用", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开游戏：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "截图悬浮窗",
                            tint = if (isBallRunning) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("导出所有数据") },
                            leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                scope.launch {
                                    val app = context.applicationContext as CocWarApplication
                                    val json = app.repository.exportAllDataJson()
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        putExtra(Intent.EXTRA_SUBJECT, "COC战报数据备份")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "导出备份"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("检查更新") },
                            leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                checkingUpdate = true
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
                                    checkingUpdate = false
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("云端同步") },
                            leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                            onClick = { menuExpanded = false; onSync() }
                        )
                        DropdownMenuItem(
                            text = { Text("AI 识别导入") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            onClick = { menuExpanded = false; onAiImport() }
                        )
                        DropdownMenuItem(
                            text = { Text("截图设置") },
                            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                            onClick = { menuExpanded = false; showSwipeSettingDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("JSON 格式示例") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuExpanded = false; showFormatDialog = true }
                        )
                    }
                }
            }

            // 筛选栏 —— 胶囊风格下拉框
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 类型
                FilterDropdown(
                    label = when (typeFilter) { "0" -> "部落战"; "1" -> "联赛"; else -> "类型" },
                    isActive = typeFilter != null,
                    expanded = typeExpanded,
                    onToggle = { typeExpanded = true },
                    onDismiss = { typeExpanded = false },
                    modifier = Modifier.weight(1f)
                ) {
                    DropdownMenuItem(text = { Text("全部") }, onClick = { typeFilter = null; typeExpanded = false })
                    DropdownMenuItem(text = { Text("部落战") }, onClick = { typeFilter = "0"; typeExpanded = false })
                    DropdownMenuItem(text = { Text("联赛") }, onClick = { typeFilter = "1"; typeExpanded = false })
                }
                // 年份
                FilterDropdown(
                    label = yearFilter?.let { "${it}年" } ?: "年份",
                    isActive = yearFilter != null,
                    expanded = yearExpanded,
                    onToggle = { yearExpanded = true },
                    onDismiss = { yearExpanded = false },
                    modifier = Modifier.weight(1f)
                ) {
                    DropdownMenuItem(text = { Text("全部") }, onClick = { yearFilter = null; yearExpanded = false })
                    years.forEach { y ->
                        DropdownMenuItem(text = { Text("${y}年") }, onClick = { yearFilter = y; yearExpanded = false })
                    }
                }
                // 月份
                FilterDropdown(
                    label = monthFilter?.let { "${it}月" } ?: "月份",
                    isActive = monthFilter != null,
                    expanded = monthExpanded,
                    onToggle = { monthExpanded = true },
                    onDismiss = { monthExpanded = false },
                    modifier = Modifier.weight(1f)
                ) {
                    DropdownMenuItem(text = { Text("全部") }, onClick = { monthFilter = null; monthExpanded = false })
                    months.forEach { m ->
                        DropdownMenuItem(text = { Text("${m}月") }, onClick = { monthFilter = m; monthExpanded = false })
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (events.isEmpty()) "还没有战报数据\n点击右下角 + 导入 JSON"
                        else "没有匹配的战报",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(filtered, key = { it.eventId }) { event ->
                        EventCard(
                            event = event,
                            onClick = { onOpen(event.eventId) },
                            onDelete = { toDelete = event }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

    toDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除战报") },
            text = { Text("确定删除「${parseEventDisplayName(event.eventName)}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(event.eventId)
                    toDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消") }
            }
        )
    }

    if (showFormatDialog) {
        val jsonSample = """{
  "members": [
    {
      "rank": 1,
      "player_name": "陈平安",
      "role": "elder",
      "total_stars": 6,
      "attacks": [
        {
          "attack_order": 1,
          "status": "used",
          "destruction_percentage": 100
        }
      ]
    }
  ]
}"""
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("JSON 数据格式") },
            text = {
                Text(
                    jsonSample,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(jsonSample))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showFormatDialog = false
                }) { Text("复制") }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("关闭") }
            }
        )
    }

    // 滑动步长设置弹窗
    if (showSwipeSettingDialog) {
        val prefs = context.getSharedPreferences("cocwar_capture", android.content.Context.MODE_PRIVATE)
        var stepPercent by remember {
            mutableStateOf(prefs.getFloat("swipe_step_percent", 30f))
        }
        AlertDialog(
            onDismissRequest = { showSwipeSettingDialog = false },
            title = { Text("截图滑动步长") },
            text = {
                Column {
                    Text(
                        "每次上滑的距离（屏幕高度的百分比）\n当前：${stepPercent.toInt()}%（约 ${(stepPercent / 100 * context.resources.displayMetrics.heightPixels).toInt()}px）\n\n" +
                        "• 15-25%：精细模式，重叠多不漏行\n" +
                        "• 30%：标准（推荐）\n" +
                        "• 40-50%：快速模式，适合长列表",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = stepPercent,
                        onValueChange = { stepPercent = it },
                        valueRange = 10f..55f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putFloat("swipe_step_percent", stepPercent).apply()
                    showSwipeSettingDialog = false
                    Toast.makeText(context, "滑动步长已设为 ${stepPercent.toInt()}%", Toast.LENGTH_SHORT).show()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSwipeSettingDialog = false }) { Text("取消") }
            }
        )
    }

    // 权限引导弹窗
    if (showPermissionDialog) {
        val permState = checkCapturePermissions(context)
        if (permState.allReady) {
            // 权限已就绪，直接启动
            showPermissionDialog = false
            FloatingBallService.start(context)
            Toast.makeText(context, "悬浮窗已开启，正在打开游戏...", Toast.LENGTH_SHORT).show()
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
                if (intent != null) context.startActivity(intent)
            } catch (_: Exception) {}
        } else {
            PermissionGuideDialog(
                state = permState,
                onDismiss = {
                    showPermissionDialog = false
                    // 再次检查，如果权限已就绪则启动
                    val newState = checkCapturePermissions(context)
                    if (newState.allReady) {
                        FloatingBallService.start(context)
                        Toast.makeText(context, "权限已就绪，悬浮窗已开启", Toast.LENGTH_SHORT).show()
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.supercell.clashofclans")
                            if (intent != null) context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            )
        }
    }

    // 检查更新对话框
    updateInfo?.let { info ->
        var downloading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text("当前版本：${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text("最新版本：${info.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (info.body.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("更新内容：", style = MaterialTheme.typography.labelMedium)
                        Text(info.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (downloading) {
                        Spacer(Modifier.height(8.dp))
                        Text("正在下载，请查看通知栏进度…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
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
                    },
                    enabled = !downloading
                ) {
                    Text(if (downloading) "下载中…" else "立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("以后再说") }
            }
        )
    }
}

/**
 * 胶囊风格筛选下拉框。
 */
@Composable
private fun FilterDropdown(
    label: String,
    isActive: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(20.dp),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = if (isActive) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            content()
        }
    }
}

@Composable
private fun EventCard(
    event: WarEventEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isWar = parseEventTypeFromName(event.eventName) != "1"
    val accent = if (isWar) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧色带
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxWidth()
                    .background(accent, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            )
            Box {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            parseEventDisplayName(event.eventName),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (event.isSample) {
                            AssistChip(onClick = {}, label = { Text("示例") })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${event.clanTotalStars}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
