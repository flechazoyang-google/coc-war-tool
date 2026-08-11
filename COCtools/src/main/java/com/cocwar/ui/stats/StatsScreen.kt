package com.cocwar.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.FilterPill
import com.cocwar.ui.components.RefreshableBox
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.util.FilterPrefs

// === 阈值配色（取自语义令牌） ===
@Composable
internal fun threeStarRateColor(rate: Float): Color = when {
    rate >= 0.5f -> MaterialTheme.cocColors.accent
    rate >= 0.3f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

// ==================== 主屏幕 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit
) {
    val viewModel: StatsViewModel = warViewModel { StatsViewModel(it) }
    val context = LocalContext.current
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val memberStats by viewModel.memberStats.collectAsStateWithLifecycle()
    val recentMissed by viewModel.recentMissed.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val recentMissedWindow by viewModel.recentMissedWindow.collectAsStateWithLifecycle()
    // 预警时间段（近N次）随进程销毁丢失，重开后从 prefs 恢复；变化时同步落盘。
    // 恢复值在组合期同步读取（remember），避免与下方保存 effect 交错时 prefs 被初始值覆盖。
    val savedRecentN = remember { FilterPrefs.statsRecentN(context) }
    LaunchedEffect(Unit) { viewModel.setRecentMissedWindow(savedRecentN) }
    LaunchedEffect(recentMissedWindow) { FilterPrefs.saveStatsRecentN(context, recentMissedWindow) }
    val topMembers by viewModel.topMembers.collectAsStateWithLifecycle()
    val eventSummaries by viewModel.eventSummaries.collectAsStateWithLifecycle()
    val leagueMatch by viewModel.leagueMatch.collectAsStateWithLifecycle()

    var currentView by rememberSaveable {
        mutableStateOf(StatsView.entries.getOrElse(FilterPrefs.statsView(context)) { StatsView.OVERVIEW })
    }
    LaunchedEffect(currentView) { FilterPrefs.saveStatsView(context, currentView.ordinal) }

    // 排名页成员详情弹窗：点击成员行后展示本月逐场数据
    var detailPlayer by remember { mutableStateOf<MemberMonthlyStat?>(null) }

    // 筛选状态 —— rememberSaveable + SharedPreferences 双保险：
    // rememberSaveable 覆盖旋转/页面切换等进程内重建；删除后台后 SavedState 被系统
    // 清除，由 FilterPrefs 兜底恢复上次的筛选选择。
    var typeFilterIndex by rememberSaveable { mutableIntStateOf(FilterPrefs.statsType(context)) }  // 0=部落战(默认), 1=联赛
    LaunchedEffect(typeFilterIndex) { FilterPrefs.saveStatsType(context, typeFilterIndex) }
    var savedMonthLabel by rememberSaveable { mutableStateOf(FilterPrefs.statsMonth(context)) }   // 持久化月份标签
    LaunchedEffect(savedMonthLabel) { FilterPrefs.saveStatsMonth(context, savedMonthLabel) }
    var savedLeagueMatchLabel by rememberSaveable { mutableStateOf(FilterPrefs.statsLeagueMatch(context)) } // 持久化联赛场次归属标签
    LaunchedEffect(savedLeagueMatchLabel) { FilterPrefs.saveStatsLeagueMatch(context, savedLeagueMatchLabel) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // 筛选对话框的编辑状态（级联选择用）
    var editView by rememberSaveable { mutableStateOf(StatsView.OVERVIEW) }
    var editTypeIndex by rememberSaveable { mutableIntStateOf(0) }
    var editLeagueMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var editRecentN by rememberSaveable { mutableIntStateOf(0) }
    var editMonthLabel by rememberSaveable { mutableStateOf("") }

    // 打开对话框时用当前值初始化编辑状态
    LaunchedEffect(showFilterDialog) {
        if (showFilterDialog) {
            editView = currentView
            editTypeIndex = typeFilterIndex
            editLeagueMatchIndex = LeagueMatch.entries.indexOf(leagueMatch)
            editRecentN = recentMissedWindow
            editMonthLabel = selectedMonth?.label ?: ""
        }
    }

    // 跨版本恢复保护：旧版枚举含 ALL(0,1,2)，新版仅 WAR/LEAGUE(0,1)。
    // rememberSaveable 恢复旧值 2 会越界崩溃、旧值 1 会静默错位，这里统一收敛到合法范围。
    val safeTypeIndex = typeFilterIndex.coerceIn(0, TypeFilter.entries.lastIndex)
    if (typeFilterIndex != safeTypeIndex) typeFilterIndex = safeTypeIndex
    val safeEditTypeIndex = editTypeIndex.coerceIn(0, TypeFilter.entries.lastIndex)
    if (editTypeIndex != safeEditTypeIndex) editTypeIndex = safeEditTypeIndex

    val currentTypeFilter = TypeFilter.entries[typeFilterIndex]

    // 类型变化或状态恢复后，若当前视图不属于该类型，回落到总览
    val availableViews = StatsView.forType(currentTypeFilter)
    if (currentView !in availableViews) currentView = StatsView.OVERVIEW

    // 同步筛选到 ViewModel
    LaunchedEffect(typeFilterIndex) {
        viewModel.setTypeFilter(currentTypeFilter)
    }

    // 恢复持久化的月份选择
    LaunchedEffect(availableMonths, savedMonthLabel) {
        if (availableMonths.isNotEmpty() && savedMonthLabel.isNotBlank()) {
            val match = availableMonths.find { it.label == savedMonthLabel }
            if (match != null && match != selectedMonth) {
                viewModel.selectMonth(match)
            }
        }
    }

    // 恢复持久化的联赛场次归属选择（仅当目标月份仍存在该场次时恢复）
    LaunchedEffect(availableMonths, savedMonthLabel, savedLeagueMatchLabel) {
        if (availableMonths.isNotEmpty() && savedLeagueMatchLabel.isNotBlank()) {
            val options = viewModel.leagueMatchOptions(savedMonthLabel)
            val match = options.find { it.label == savedLeagueMatchLabel }
            if (match != null && match != leagueMatch) {
                viewModel.setLeagueMatch(match)
            }
        }
    }

    // 判断筛选是否激活
    val defaultMonth = availableMonths.firstOrNull()
    val isFilterActive = selectedMonth != null && selectedMonth != defaultMonth

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "统计·${currentView.label}",
            overline = "月度复盘",
            subtitle = selectedMonth?.label ?: "选择月份",
            actions = {
                CocIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新数据",
                    onClick = { viewModel.refresh() },
                )
                CocIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "分享月度报告",
                    onClick = {
                        if (memberStats.isEmpty() && overview == null) return@CocIconButton
                        // 月度成绩单（B2，RULES §4.16）：口径复用 StatsCalculator 已有结果
                        val title = "${selectedMonth?.label ?: ""}${currentTypeFilter.label}月度报告"
                        val csv = com.cocwar.data.csv.CsvExporter.exportMonthlyReportCsv(
                            title = title,
                            stats = memberStats,
                            overview = overview
                        )
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                            putExtra(android.content.Intent.EXTRA_TEXT, csv)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(sendIntent, "分享月度报告")
                        )
                    }
                )
                CocIconButton(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "筛选",
                    onClick = { showFilterDialog = true },
                    filled = isFilterActive
                )
            }
        )

        // 下拉刷新：数据已由 ViewModel 响应式跟随数据库，下拉仅提供手动重载与状态反馈
        RefreshableBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            doneText = "统计已更新",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 仅首次加载（无数据）显示居中加载提示；刷新时保持内容可见
            if (availableMonths.isEmpty() && loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (availableMonths.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "暂无战报数据", body = "导入战报后这里会生成月度复盘")
                }
            } else {
                when (currentView) {
                    StatsView.OVERVIEW -> OverviewTab(
                        overview, eventSummaries, currentTypeFilter,
                        onEventClick = { eventId -> onOpenEvent(eventId) },
                        modifier = Modifier.fillMaxSize()
                    )
                    StatsView.RANKING -> MembersTab(memberStats, Modifier.fillMaxSize()) { stat ->
                        detailPlayer = stat
                    }
                    StatsView.WARNING -> MissedTab(recentMissed, Modifier.fillMaxSize())
                    StatsView.TOP -> TopMembersTab(topMembers, Modifier.fillMaxSize())
                }
            }
        }
    }

    // 筛选对话框：级联选择（类型 → 视图 → 排序/时间段 → 月份）
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("筛选") },
            text = {
                Column {
                    // 类型区（一级筛选，始终显示）
                    Text("类型", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeFilter.entries.forEach { filter ->
                            FilterPill(
                                label = filter.label,
                                selected = editTypeIndex == filter.ordinal,
                                onClick = {
                                    editTypeIndex = filter.ordinal
                                    val views = StatsView.forType(filter)
                                    if (editView !in views) editView = StatsView.OVERVIEW
                                }
                            )
                        }
                    }

                    // 视图区（二级筛选，随类型变化）
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("视图", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsView.forType(TypeFilter.entries[editTypeIndex]).forEach { view ->
                            FilterPill(
                                label = view.label,
                                selected = editView == view,
                                onClick = { editView = view }
                            )
                        }
                    }

                    // 场次归属（仅联赛成员数据时显示；选项随编辑态月份动态变化）
                    if (editView == StatsView.RANKING &&
                        TypeFilter.entries[editTypeIndex] == TypeFilter.LEAGUE
                    ) {
                        val matchOptions = viewModel.leagueMatchOptions(editMonthLabel)
                        if (matchOptions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.cocColors.hairline)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("场次归属", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                matchOptions.forEachIndexed { index, match ->
                                    FilterPill(
                                        label = match.label,
                                        selected = editLeagueMatchIndex == index,
                                        onClick = { editLeagueMatchIndex = index }
                                    )
                                }
                            }
                        }
                    }

                    // 时间段（仅预警时显示）
                    if (editView == StatsView.WARNING) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("时间段", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val timeOptions = listOf(0 to "当月全部", 3 to "近3次", 7 to "近7次")
                            timeOptions.forEach { (n, label) ->
                                FilterPill(
                                    label = label,
                                    selected = editRecentN == n,
                                    onClick = { editRecentN = n }
                                )
                            }
                        }
                    }

                    // 分隔线 + 月份区（始终显示）
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline)
                    )
                    Spacer(Modifier.height(18.dp))

                    Text("月份", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    if (availableMonths.isEmpty()) {
                        Text("暂无可用月份", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column {
                            availableMonths.forEach { month ->
                                val isSelected = month.label == editMonthLabel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editMonthLabel = month.label
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        month.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.FilterList,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 应用所有选择
                    // 类型为一级筛选，始终生效
                    typeFilterIndex = editTypeIndex.coerceIn(0, TypeFilter.entries.lastIndex)
                    viewModel.setTypeFilter(TypeFilter.entries[typeFilterIndex])
                    currentView = editView

                    // 场次归属仅对联赛成员数据生效（选项随目标月份动态变化，越界回落首个选项）
                    if (editView == StatsView.RANKING &&
                        TypeFilter.entries[typeFilterIndex] == TypeFilter.LEAGUE
                    ) {
                        val matchOptions = viewModel.leagueMatchOptions(editMonthLabel)
                        if (matchOptions.isNotEmpty()) {
                            val match = matchOptions.getOrNull(
                                editLeagueMatchIndex.coerceIn(0, matchOptions.lastIndex)
                            )
                            if (match != null) {
                                viewModel.setLeagueMatch(match)
                                savedLeagueMatchLabel = match.label
                            }
                        }
                    }

                    // 时间段仅对预警生效
                    if (editView == StatsView.WARNING) {
                        viewModel.setRecentMissedWindow(editRecentN)
                    }

                    val newMonth = availableMonths.find { it.label == editMonthLabel }
                    if (newMonth != null && newMonth != selectedMonth) {
                        savedMonthLabel = editMonthLabel
                        viewModel.selectMonth(newMonth)
                    }

                    showFilterDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 排名页成员详情弹窗（表格展示本月逐场战报）
    detailPlayer?.let { stat ->
        MemberDetailDialog(
            stat = stat,
            details = viewModel.memberEventDetails(stat.playerName),
            onDismiss = { detailPlayer = null }
        )
    }
}
