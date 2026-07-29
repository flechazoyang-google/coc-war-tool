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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.cocwar.ui.util.parseYearFromName

@Composable
fun EventListScreen(
    onOpen: (String) -> Unit,
    onImport: () -> Unit = {},
) {
    val viewModel: EventListViewModel = warViewModel { EventListViewModel(it) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<WarEventEntity?>(null) }
    val context = LocalContext.current

    // 剪切板读取状态（由右上角按钮触发，不再自动检测）
    var clipboardParsed by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // 筛选状态 —— rememberSaveable 保证切换页面或退出应用后筛选条件不丢失
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
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
            (typeFilter == null || t == typeFilter) &&
            (yearFilter == null || y == yearFilter) &&
            (monthFilter == null || m == monthFilter)
        }
    }

    val warCount = remember(events) { events.count { it.eventType != "league" } }
    val leagueCount = events.size - warCount

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
                        when (val result = WarJsonParser.parse(text)) {
                            is WarJsonParser.ParseResult.Success -> clipboardParsed = result.data
                            is WarJsonParser.ParseResult.Error -> Toast.makeText(context, "战报解析失败：${result.message}", Toast.LENGTH_LONG).show()
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
                label = when (typeFilter) { "0" -> "部落战"; "1" -> "联赛"; else -> "类型" },
                isActive = typeFilter != null,
                expanded = typeExpanded,
                onToggle = { typeExpanded = true },
                onDismiss = { typeExpanded = false }
            ) {
                DropdownMenuItem(text = { Text("全部") }, onClick = { typeFilter = null; typeExpanded = false })
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
            // 无卡片列表：发丝线分隔的编辑式条目
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered, key = { _, e -> e.eventId }) { index, event ->
                    EventRow(
                        event = event,
                        onClick = { onOpen(event.eventId) },
                        onDelete = { toDelete = event }
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
                item { Spacer(Modifier.height(24.dp)) }
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
                }) { Text("删除", color = MaterialTheme.cocColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消") }
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
