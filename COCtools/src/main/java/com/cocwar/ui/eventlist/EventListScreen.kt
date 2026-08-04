package com.cocwar.ui.eventlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import com.cocwar.ui.ClipboardImportDialog
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.looksLikeWarJson
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.util.parseEventDisplayName
import com.cocwar.ui.util.parseEventTypeFromName
import com.cocwar.ui.util.parseMonthFromName
import kotlinx.coroutines.launch
import com.cocwar.ui.util.parseYearFromName
import com.cocwar.ui.util.parseLeagueMatchFromName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onOpen: (String) -> Unit,
    onImport: () -> Unit = {},
    onOpenSeason: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {
    val viewModel: EventListViewModel = warViewModel { EventListViewModel(it) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // 剪切板读取状态（由右上角按钮触发，不再自动检测）
    var clipboardParsed by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // 筛选状态 —— rememberSaveable 保证切换页面或退出应用后筛选条件不丢失
    // 部落战和联赛完全独立，类型筛选无「全部」选项，默认部落战
    var typeFilter by rememberSaveable { mutableStateOf("0") }
    // 跨版本恢复保护：旧版 typeFilter 可为 null（全部），恢复后收敛到默认「部落战」
    if (typeFilter != "0" && typeFilter != "1") typeFilter = "0"
    var yearFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var monthFilter by rememberSaveable { mutableStateOf<Int?>(null) }

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
                list.sortedWith(compareBy { it.eventRound.takeIf { r -> r in 1..7 } ?: 99 }),
                list.size
            )
        }.sortedWith(
            compareByDescending<Triple<Triple<Int?, Int?, Int?>, List<WarEventEntity>, Int>> {
                it.first.first ?: Int.MAX_VALUE
            }.thenByDescending { it.first.second ?: Int.MAX_VALUE }
                .thenBy { it.first.third ?: Int.MAX_VALUE }
        )
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
    
            // 筛选栏 —— 细线胶囊下拉
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterDropdown(
                    label = if (typeFilter == "1") "联赛" else "部落战",
                    isActive = typeFilter != "0",
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

            // 下拉刷新：列表由 Room Flow 自动保持最新，下拉触发手动重读并提供进度反馈
            PullToRefreshBox(
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
                            EmptyState(title = "没有匹配的战报", body = "试试调整筛选条件")
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
                                            onDelete = { deleteEventWithUndo(event) }
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
                            itemsIndexed(filtered, key = { _, e -> e.eventId }) { index, event ->
                                EventRow(
                                    event = event,
                                    onClick = { onOpen(event.eventId) },
                                    onDelete = { deleteEventWithUndo(event) }
                                )
                                if (index < filtered.lastIndex) {
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
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary
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
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary
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
 */
@Composable
private fun EventRow(
    event: WarEventEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 名称无法解析时回退到 entity.eventType，避免非标准名称被错归类
    val isWar = when (parseEventTypeFromName(event.eventName)) {
        "1" -> false
        "0" -> true
        else -> event.eventType != "league"
    }
    val typeColor = if (isWar) MaterialTheme.cocColors.accent else MaterialTheme.cocColors.star

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    parseEventDisplayName(event.eventName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
                if (isWar) "部落战" else "联赛",
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

        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
