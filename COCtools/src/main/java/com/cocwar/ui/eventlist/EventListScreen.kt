package com.cocwar.ui.eventlist

import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.cocwar.data.db.WarEventEntity
import com.cocwar.di.warViewModel
import com.cocwar.ui.util.parseEventDisplayName
import com.cocwar.ui.util.parseEventTypeFromName
import com.cocwar.ui.util.parseMonthFromName
import com.cocwar.ui.util.parseYearFromName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onStats: () -> Unit,
    onMembers: () -> Unit,
    onSync: () -> Unit = {}
) {
    val viewModel: EventListViewModel = warViewModel { EventListViewModel(it) }
    val events by viewModel.events.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<WarEventEntity?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("部落战数据管家", style = MaterialTheme.typography.headlineMedium)
                },
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "成员统计")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("成员管理") },
                            leadingIcon = { Icon(Icons.Filled.ManageAccounts, contentDescription = null) },
                            onClick = { menuExpanded = false; onMembers() }
                        )
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
                            text = { Text("云端同步") },
                            leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                            onClick = { menuExpanded = false; onSync() }
                        )
                        DropdownMenuItem(
                            text = { Text("JSON 格式示例") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuExpanded = false; showFormatDialog = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImport) {
                Icon(Icons.Filled.Add, contentDescription = "导入数据")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
