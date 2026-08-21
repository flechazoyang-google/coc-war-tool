package com.cocwar.ui.eventlist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.db.PendingImportEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import com.cocwar.service.FloatingBallService
import com.cocwar.service.OcrBatchService
import com.cocwar.service.ScreenCaptureService
import com.cocwar.ui.ClipboardImportDialog
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.RefreshableBox
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.looksLikeWarJson
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.util.compareLeagueRound
import com.cocwar.ui.util.compareWarEventsBySeq
import com.cocwar.ui.util.FilterPrefs
import com.cocwar.ui.util.parseEventDisplayName
import com.cocwar.ui.util.parseEventTypeFromName
import com.cocwar.ui.util.parseLeagueMatchFromName
import com.cocwar.ui.util.parseMonthFromName
import com.cocwar.ui.util.parseYearFromName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onOpen: (String) -> Unit,
    onImport: () -> Unit = {},
    onOpenSeason: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onOpenPendingImport: (String) -> Unit = {},
) {
    val viewModel: EventListViewModel = warViewModel { EventListViewModel(it) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 截图辅助（悬浮球）快捷开关：右上角按钮一键开启/关闭
    var isBallRunning by remember { mutableStateOf(FloatingBallService.isRunning()) }
    var showCapturePermissionDialog by remember { mutableStateOf(false) }
    // 从其他页面/设置回来时刷新悬浮球运行状态（避免按钮状态过期）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isBallRunning = FloatingBallService.isRunning()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 进程被杀兜底：启动无服务运行却残留 processing 草稿时，置为 failed 便于重试/删除
    LaunchedEffect(Unit) {
        if (!OcrBatchService.isRunning()) viewModel.markStaleProcessingFailed()
    }

    // 一键开关截图悬浮球；权限缺失时弹窗引导（与设置-截图工具页口径一致）
    fun toggleCaptureHelper() {
        if (FloatingBallService.isRunning()) {
            FloatingBallService.stop(context)
            isBallRunning = false
            Toast.makeText(context, "截图悬浮球已关闭", Toast.LENGTH_SHORT).show()
        } else {
            val a11y = ScreenCaptureService.isAccessibilityServiceEnabled(context)
            val overlay = Settings.canDrawOverlays(context)
            if (!a11y || !overlay) {
                showCapturePermissionDialog = true
            } else {
                FloatingBallService.start(context)
                isBallRunning = true
                Toast.makeText(context, "截图悬浮球已开启", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 删除战报：立即落库删除 → Snackbar 提供撤销（重插快照），防误触
    fun deleteEventWithUndo(event: WarEventEntity) {
        scope.launch {
            val snapshot = viewModel.deleteEventWithSnapshot(event.eventId)
            if (snapshot == null) {
                Toast.makeText(context, "删除失败：战报不存在", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${parseEventDisplayName(event.eventName)}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(snapshot)
            }
        }
    }

    // 长按菜单选择「删除」后待确认的战报（确认框弹出前先记录）
    var pendingDeleteEvent by remember { mutableStateOf<WarEventEntity?>(null) }

    // 剪切板读取状态（由右上角按钮触发，不再自动检测）
    var clipboardParsed by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // 筛选状态 —— rememberSaveable + SharedPreferences 双保险：
    // rememberSaveable 覆盖旋转屏幕/页面切换等进程内重建；用户从最近任务划掉应用
    // （删除后台）后系统会清除 SavedState，此时由 FilterPrefs 兜底恢复上次的选择。
    // 部落战和联赛完全独立，类型筛选无「全部」选项，默认部落战
    var typeFilter by rememberSaveable { mutableStateOf(FilterPrefs.eventType(context)) }
    // 跨版本恢复保护：旧版 typeFilter 可为 null（全部），恢复后收敛到默认「部落战」
    if (typeFilter != "0" && typeFilter != "1") typeFilter = "0"
    LaunchedEffect(typeFilter) { FilterPrefs.saveEventType(context, typeFilter) }
    var yearFilter by rememberSaveable { mutableStateOf(FilterPrefs.eventYear(context)) }
    LaunchedEffect(yearFilter) { FilterPrefs.saveEventYear(context, yearFilter) }
    var monthFilter by rememberSaveable { mutableStateOf(FilterPrefs.eventMonth(context)) }
    LaunchedEffect(monthFilter) { FilterPrefs.saveEventMonth(context, monthFilter) }

    // 下拉展开状态（仅 UI 临时状态，无需持久化）
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
            // 名称无法解析时回退到 entity.eventType，避免非标准名称在筛选时凭空消失
            val t = parseEventTypeFromName(event.eventName)
                ?: if (event.eventType == "league") "1" else "0"
            val y = parseYearFromName(event.eventName)
            val m = parseMonthFromName(event.eventName)
            (t == typeFilter) &&
            (yearFilter == null || y == yearFilter) &&
            (monthFilter == null || m == monthFilter)
        }
    }

    val warCount = remember(events) { events.count { it.eventType != "league" } }
    val leagueCount = events.size - warCount

    // 联赛视图按「年月 + 月初/月中场」分组，体现同一场联赛 7 轮的相关性；
    // 组内按轮次升序（第 1 轮在前），组间按年月倒序（最新月份在前）。
    // 非标准名称（无法解析年月/场次）归入末尾的「其他」组。
    val leagueGroups = remember(filtered, typeFilter) {
        if (typeFilter != "1") emptyList()
        else filtered.groupBy { event ->
            Triple(
                parseYearFromName(event.eventName),
                parseMonthFromName(event.eventName),
                parseLeagueMatchFromName(event.eventName)
            )
        }.map { (key, list) ->
            Triple(
                key,
                // 组内轮次排序：名称 C2 解析优先（与列表页展示 parseEventDisplayName 口径一致），
                // 失败回退实体 eventRound，再失败（0/无效）排组内末尾
                list.sortedWith(compareLeagueRound()),
                list.size
            )
        }.sortedWith(
            // 年月倒序（null 归最后：descending 下用 MIN_VALUE 哨兵反转后垫底），
            // 场次归属升序（月初场 1 在月中场 2 前），无法解析场次归最后
            compareByDescending<Triple<Triple<Int?, Int?, Int?>, List<WarEventEntity>, Int>> {
                it.first.first ?: Int.MIN_VALUE
            }.thenByDescending { it.first.second ?: Int.MIN_VALUE }
                .thenBy { it.first.third ?: Int.MAX_VALUE }
        )
    }

    // 部落战视图排序：最新月份在前，同月内按场次序号（CC）升序（第 1 场在前）；
    // 名称无法解析年月/序号的排最后。
    val warSorted = remember(filtered, typeFilter) {
        if (typeFilter == "1") emptyList()
        else filtered.sortedWith(compareWarEventsBySeq())
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "战报",
                overline = "战报档案",
                subtitle = if (events.isEmpty()) "尚无归档"
                else "共 ${events.size} 份 · 部落战 $warCount · 联赛 $leagueCount",
                actions = {
                    CocIconButton(
                        icon = Icons.Filled.CameraAlt,
                        contentDescription = if (isBallRunning) "截图悬浮球运行中，点击关闭" else "截图辅助（悬浮球）",
                        onClick = { toggleCaptureHelper() },
                        filled = isBallRunning
                    )
                    CocIconButton(
                        icon = Icons.Filled.ContentPaste,
                        contentDescription = "读取剪切板",
                        onClick = {
                            val text = clipboardManager.getText()?.text ?: ""
                            if (text.isBlank()) {
                                Toast.makeText(context, "剪切板为空，没有可读取的内容", Toast.LENGTH_SHORT).show()
                                return@CocIconButton
                            }
                            if (!looksLikeWarJson(text)) {
                                Toast.makeText(context, "剪切板中没有检测到战报数据", Toast.LENGTH_SHORT).show()
                                return@CocIconButton
                            }
                            // 大 JSON 解析放到 IO 线程，避免阻塞主线程
                            scope.launch {
                                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    viewModel.parseWarJson(text)
                                }
                                when (result) {
                                    is WarJsonParser.ParseResult.Success -> clipboardParsed = result.data
                                    is WarJsonParser.ParseResult.Error -> Toast.makeText(context, "战报解析失败：${result.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                    CocIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "导入战报",
                        onClick = onImport,
                        filled = true
                    )
                }
            )
    
            // 待确认识图（后台批量识图结果）
            if (pending.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "待确认识图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.cocColors.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    pending.forEach { item ->
                        PendingImportRow(
                            item = item,
                            onOpen = { onOpenPendingImport(item.id) },
                            onDelete = { viewModel.deletePending(item.id) },
                            onRetry = {
                                scope.launch {
                                    val paths = viewModel.pendingImagePaths(item.id)
                                    if (paths.isEmpty()) {
                                        Toast.makeText(context, "重试失败：截图路径已失效", Toast.LENGTH_SHORT).show()
                                    } else {
                                        OcrBatchService.start(context, paths, replaceId = item.id)
                                        Toast.makeText(context, "已重新开始后台识图", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onCancel = {
                                context.sendBroadcast(
                                    Intent(OcrBatchService.ACTION_CANCEL).setPackage(context.packageName)
                                )
                            }
                        )
                    }
                }
            }

            // 筛选栏 —— 细线胶囊下拉
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterDropdown(
                    label = if (typeFilter == "1") "联赛" else "部落战",
                    // 类型筛选永远有选中值（部落战/联赛二选一），始终处于「已激活」态，
                    // 避免把默认的「部落战」误显示成未选中（白底）
                    isActive = true,
                    expanded = typeExpanded,
                    onToggle = { typeExpanded = true },
                    onDismiss = { typeExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("部落战") }, onClick = { typeFilter = "0"; typeExpanded = false })
                    DropdownMenuItem(text = { Text("联赛") }, onClick = { typeFilter = "1"; typeExpanded = false })
                }
                FilterDropdown(
                    label = yearFilter?.let { "${it}年" } ?: "年份",
                    isActive = yearFilter != null,
                    expanded = yearExpanded,
                    onToggle = { yearExpanded = true },
                    onDismiss = { yearExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("全部") }, onClick = { yearFilter = null; yearExpanded = false })
                    years.forEach { y ->
                        DropdownMenuItem(text = { Text("${y}年") }, onClick = { yearFilter = y; yearExpanded = false })
                    }
                }
                FilterDropdown(
                    label = monthFilter?.let { "${it}月" } ?: "月份",
                    isActive = monthFilter != null,
                    expanded = monthExpanded,
                    onToggle = { monthExpanded = true },
                    onDismiss = { monthExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("全部") }, onClick = { monthFilter = null; monthExpanded = false })
                    months.forEach { m ->
                        DropdownMenuItem(text = { Text("${m}月") }, onClick = { monthFilter = m; monthExpanded = false })
                    }
                }
            }
    
            Spacer(Modifier.height(6.dp))

            // 下拉刷新：列表由 Room Flow 自动保持最新，下拉触发手动重读并提供状态反馈
            RefreshableBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (events.isEmpty()) {
                            EmptyState(
                                title = "还没有战报",
                                body = "点击右上角 + 导入 JSON\n复制战报 JSON 后打开 App 会自动识别"
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Filled.SearchOff,
                                title = "没有匹配的战报",
                                body = "试试调整筛选条件\n或点击右上角 + 添加战报"
                            )
                        }
                    }
                } else {
                    // 无卡片列表：发丝线分隔的编辑式条目；联赛视图按场次分组展示
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (leagueGroups.isNotEmpty()) {
                            leagueGroups.forEach { (key, group, count) ->
                                item(key = "lg-header-${key.first}-${key.second}-${key.third}") {
                                    LeagueGroupHeader(
                                        year = key.first, month = key.second, match = key.third, size = count,
                                        onClick = {
                                            val y = key.first; val m = key.second; val mt = key.third
                                            if (y != null && m != null && mt != null) onOpenSeason(y, m, mt)
                                        }
                                    )
                                }
                                group.forEachIndexed { index, event ->
                                    item(key = event.eventId) {
                                        EventRow(
                                            event = event,
                                            onClick = { onOpen(event.eventId) },
                                            onDeleteRequest = { pendingDeleteEvent = event }
                                        )
                                        if (index < group.lastIndex) {
                                            Box(
                                                Modifier
                                                    .padding(start = 20.dp)
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(MaterialTheme.cocColors.hairline)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(warSorted, key = { _, e -> e.eventId }) { index, event ->
                                EventRow(
                                    event = event,
                                    onClick = { onOpen(event.eventId) },
                                    onDeleteRequest = { pendingDeleteEvent = event }
                                )
                                if (index < warSorted.lastIndex) {
                                    Box(
                                        Modifier
                                            .padding(start = 20.dp)
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.cocColors.hairline)
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 删除战报确认框：长按条目 → 菜单「删除战报」→ 确认 → 执行删除（仍可 Snackbar 撤销）
    pendingDeleteEvent?.let { ev ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEvent = null },
            title = { Text("删除战报") },
            text = { Text("确定删除「${parseEventDisplayName(ev.eventName)}」？删除后可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteEvent = null
                    deleteEventWithUndo(ev)
                }) { Text("删除", color = MaterialTheme.cocColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEvent = null }) { Text("取消") }
            }
        )
    }

    // 剪切板战报导入对话框
    clipboardParsed?.let { parsed ->
        val app = context.applicationContext as CocWarApplication
        ClipboardImportDialog(
            parsed = parsed,
            repo = app.repository,
            onSaved = { eventId ->
                clipboardParsed = null
                onOpen(eventId)
            },
            onDismiss = { clipboardParsed = null }
        )
    }

    // 截图辅助权限引导：悬浮球需要「悬浮窗 + 无障碍」两项权限，缺失时弹窗引导开启
    if (showCapturePermissionDialog) {
        val a11yEnabled = ScreenCaptureService.isAccessibilityServiceEnabled(context)
        val overlayGranted = Settings.canDrawOverlays(context)
        val missingParts = buildList {
            if (!overlayGranted) add("悬浮窗权限")
            if (!a11yEnabled) add("无障碍服务")
        }
        AlertDialog(
            onDismissRequest = { showCapturePermissionDialog = false },
            title = { Text("需要开启以下权限") },
            text = {
                Text("使用截图辅助需要开启：${missingParts.joinToString("、")}\n\n" +
                    "1. 悬浮窗权限：允许在游戏上方显示截图按钮\n" +
                    "2. 无障碍服务：允许自动截图和滑动")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!a11yEnabled) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else if (!overlayGranted) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 用户可能已去系统设置完成授权后返回：重新读取，齐备则直接开启
                    val stillOverlay = Settings.canDrawOverlays(context)
                    val stillA11y = ScreenCaptureService.isAccessibilityServiceEnabled(context)
                    val stillMissing = buildList {
                        if (!stillOverlay) add("悬浮窗权限")
                        if (!stillA11y) add("无障碍服务")
                    }
                    if (stillMissing.isEmpty()) {
                        FloatingBallService.start(context)
                        isBallRunning = true
                        Toast.makeText(context, "截图悬浮球已开启", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "仍缺少：${stillMissing.joinToString("、")}，请先开启后再试",
                            Toast.LENGTH_LONG).show()
                    }
                    showCapturePermissionDialog = false
                }) { Text("取消") }
            }
        )
    }
}

/**
 * 待确认识图草稿行：processing / ready / failed 三态。
 */
@Composable
private fun PendingImportRow(
    item: PendingImportEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    CocCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                when (item.status) {
                    "processing" -> Text(
                        "识图中 (" + item.processedImages + "/" + item.totalImages + ")",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    "ready" -> {
                        Text("待确认 · 战报", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "识图完成，点击进入导入确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            "识图失败",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.cocColors.danger
                        )
                        Text(
                            item.errorMessage.ifBlank { "识别失败" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            when (item.status) {
                "processing" -> TextButton(onClick = onCancel) { Text("取消") }
                "ready" -> TextButton(onClick = onOpen) { Text("去确认", fontWeight = FontWeight.SemiBold) }
                else -> {
                    TextButton(onClick = onRetry) { Text("重试") }
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.cocColors.danger) }
                }
            }
        }
    }
}

/**
 * 细线胶囊筛选下拉框。
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
            shape = com.cocwar.ui.components.CocShape.chip,
            color = if (isActive) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.secondary
                else MaterialTheme.cocColors.hairline
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
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

/**
 * 联赛分组组头：显示 年月 · 月初/月中场（已导入轮数/7 轮）。
 */
@Composable
private fun LeagueGroupHeader(year: Int?, month: Int?, match: Int?, size: Int, onClick: () -> Unit) {
    val matchLabel = when (match) {
        1 -> "月初场"
        2 -> "月中场"
        else -> "其他"
    }
    val ym = if (year != null && month != null) "${year}年${month}月 · " else ""
    val clickable = year != null && month != null && match != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$ym$matchLabel（$size/7 轮）",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (clickable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看赛季",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 战报条目：编辑式排版 —— 左侧名称与元信息，右侧星数大数字。
 * 点击打开详情；长按弹出操作菜单（删除）；删除前由页面层弹确认框。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(
    event: WarEventEntity,
    onClick: () -> Unit,
    onDeleteRequest: (WarEventEntity) -> Unit
) {
    // 名称无法解析时回退到 entity.eventType，避免非标准名称被错归类
    val isWar = when (parseEventTypeFromName(event.eventName)) {
        "1" -> false
        "0" -> true
        else -> event.eventType != "league"
    }
    val typeColor = if (isWar) MaterialTheme.cocColors.accent else MaterialTheme.cocColors.star

    // 长按操作菜单的展开状态（仅 UI 临时状态）
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(start = 20.dp, end = 20.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parseEventDisplayName(event.eventName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.isSample) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "示例",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    // 元信息行附带月份，区分同名战报（名称解析不出年月时不追加）
                    buildString {
                        append(if (isWar) "部落战" else "联赛")
                        val y = parseYearFromName(event.eventName)
                        val m = parseMonthFromName(event.eventName)
                        if (y != null && m != null) append(" · ${y}年${m}月")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = typeColor
                )
            }

            // 星数大数字（表格化）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.cocColors.star,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${event.clanTotalStars}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 长按操作菜单：删除入口（确认框由页面层负责）
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("删除战报", color = MaterialTheme.cocColors.danger) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.cocColors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuOpen = false
                    onDeleteRequest(event)
                }
            )
        }
    }
}
