package com.cocwar.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.data.model.Attack
import com.cocwar.data.model.isUsed
import com.cocwar.di.warViewModel
import com.cocwar.domain.EventStatSummary
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.domain.StatsOverview
import com.cocwar.domain.TopMemberScore
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.FilterPill
import com.cocwar.ui.components.RefreshableBox
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SoftTag
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.formatPercent
import com.cocwar.ui.util.roleLabel

// === 阈值配色（取自语义令牌） ===
@Composable
private fun threeStarRateColor(rate: Float): Color = when {
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
    val topMembers by viewModel.topMembers.collectAsStateWithLifecycle()
    val eventSummaries by viewModel.eventSummaries.collectAsStateWithLifecycle()
    val leagueMatch by viewModel.leagueMatch.collectAsStateWithLifecycle()

    var currentView by rememberSaveable { mutableStateOf(StatsView.OVERVIEW) }

    // 排名页成员详情弹窗：点击成员行后展示本月逐场数据
    var detailPlayer by remember { mutableStateOf<MemberMonthlyStat?>(null) }

    // 筛选持久化 —— rememberSaveable
    var typeFilterIndex by rememberSaveable { mutableIntStateOf(0) }  // 0=部落战(默认), 1=联赛
    var savedMonthLabel by rememberSaveable { mutableStateOf("") }   // 持久化月份标签
    var savedLeagueMatchLabel by rememberSaveable { mutableStateOf("") } // 持久化联赛场次归属标签
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

// ==================== Tab 0: 总览 ====================

@Composable
private fun OverviewTab(
    overview: StatsOverview?,
    eventSummaries: List<EventStatSummary>,
    typeFilter: TypeFilter,
    modifier: Modifier = Modifier,
    onEventClick: (String) -> Unit
) {
    if (overview == null || overview.totalEvents == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无${typeFilter.label}数据", body = "切换月份或类型查看历史复盘")
        }
        return
    }

    // 战报情况表格数据：按创建时间升序，序号 01、02… 即第几场
    val sortedSummaries = remember(eventSummaries) {
        eventSummaries.sortedBy { it.createdAt }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // === 月度总览渐变卡片 ===
        item { HeroCard(overview, typeFilter.label) }

        // === 战报情况表格 ===
        item { SectionTitle("战报情况") }
        item {
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    EventSummaryHeader()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline)
                    )
                    if (sortedSummaries.isEmpty()) {
                        Text(
                            "本月暂无${typeFilter.label}战报",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        sortedSummaries.forEachIndexed { index, summary ->
                            EventSummaryRow(
                                summary = summary,
                                index = index,
                                onClick = { onEventClick(summary.eventId) }
                            )
                            if (index < sortedSummaries.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.cocColors.hairline.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ===== 战报情况表格 =====

/** 表头：战报 / 总星数 / 三星次数 / 使用进攻次数 / 三星率 / 参与率 */
@Composable
private fun EventSummaryHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("战报", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
        Text("总星数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
        Text("三星次数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
        Text("使用进攻次数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1.3f))
        Text("三星率", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
        Text("参与率", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
    }
}

/** 参与率配色：90% 以上强调色，70% 以上警示色，否则危险色 */
@Composable
private fun participationRateColor(rate: Float): Color = when {
    rate >= 0.9f -> MaterialTheme.cocColors.accent
    rate >= 0.7f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

/** 单行：序号 + 总星数 + 三星次数 + 使用进攻次数 + 三星率 + 参与率，点击跳转战报详情 */
@Composable
private fun EventSummaryRow(
    summary: EventStatSummary,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(index + 1), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
        Text("${summary.totalStars}", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
        Text("${summary.threeStarCount}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
        Text("${summary.totalUsedAttacks}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1.3f))
        Text(formatPercent(summary.threeStarRate * 100),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = threeStarRateColor(summary.threeStarRate),
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
        Text(formatPercent(summary.participationRate * 100),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = participationRateColor(summary.participationRate),
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
    }
}

// ===== 积分排行：独立视图，展示全部成员得分（按得分降序） =====

@Composable
private fun TopMembersTab(
    topMembers: List<TopMemberScore>,
    modifier: Modifier = Modifier
) {
    if (topMembers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无部落战数据", body = "积分排行仅统计部落战")
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { SectionTitle("积分排行 · 积分制") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp, end = 2.dp, top = 6.dp, bottom = 4.dp)
            ) {
                Text("排名", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("得分", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
        // 全部成员按得分降序展示（computeTopMembers 已按 score 排序）
        itemsIndexed(topMembers, key = { _, s -> s.playerName }) { index, score ->
            TopScoreRow(index = index + 1, score = score)
            if (index < topMembers.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 44.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ===== 月度总览：渐变大卡（类型胶囊 + 场次大数字 + 进攻率环 + 底部指标行） =====

@Composable
private fun HeroCard(overview: StatsOverview, typeLabel: String) {
    val isDark = isSystemInDarkTheme()
    val accent = MaterialTheme.cocColors.accent
    val accentSoft = MaterialTheme.cocColors.accentSoft
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    // 柔和渐变：强调色淡染 → 透明，叠加在卡片面上（比实色渐变克制，不突兀）
    val brush = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = if (isDark) 0.20f else 0.14f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(Modifier.background(brush)) {
            // 装饰：右上角半透明同心圆环（部落徽章意象）
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .size(96.dp)
                    .border(12.dp, accent.copy(alpha = 0.10f), CircleShape)
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 类型胶囊
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentSoft
                    ) {
                        Text(
                            typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "月度总览",
                        style = MaterialTheme.typography.labelSmall,
                        color = inkSoft
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 场次大数字 + 满星率
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${overview.totalEvents}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "场战报",
                                style = MaterialTheme.typography.labelMedium,
                                color = inkSoft,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "满星率",
                                style = MaterialTheme.typography.labelSmall,
                                color = inkSoft
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                formatPercent(overview.fullStarRate * 100),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                    // 进攻率环
                    RingStat(
                        rate = overview.overallAttackRate,
                        color = accent,
                        size = 62.dp,
                        strokeWidth = 5.dp,
                        labelColor = ink,
                        trackColor = accentSoft
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
                Spacer(Modifier.height(12.dp))
                // 底部指标行：三星率 / 场均摧毁 / 场均星数
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroMiniStat("三星率", formatPercent(overview.threeStarRate * 100), ink)
                    HeroMiniStat("场均摧毁", formatPercent(overview.avgDestruction), ink)
                    HeroMiniStat("场均星数", "%.1f".format(overview.avgStarsPerEvent), ink)
                }
            }
        }
    }
}

/** 渐变卡底部小指标：值 + 标签，垂直居中 */
@Composable
private fun HeroMiniStat(label: String, value: String, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.7f)
        )
    }
}

// ===== 环形进度（圆头描边，平面轨道） =====

@Composable
private fun RingStat(
    rate: Float,
    color: Color,
    size: Dp = 52.dp,
    strokeWidth: Dp = 4.5.dp,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            progress = { rate.coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round
        )
        Text(
            formatPercent(rate * 100),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )
    }
}

// ===== 积分排行：精简行（排名 + 昵称 + 军衔 + 得分） =====

/** 前三名军衔：1 上将 / 2 中将 / 3 少将 */
private fun rankTitle(index: Int): String? = when (index) {
    1 -> "上将"
    2 -> "中将"
    3 -> "少将"
    else -> null
}

@Composable
private fun TopScoreRow(index: Int, score: TopMemberScore) {
    var showDetail by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
            RankBadge(rank = index, size = 28.dp)
        }
        // 昵称：职位色区分，点击查看得分来源明细
        Text(
            score.playerName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = roleColor(score.role),
            modifier = Modifier.clickable { showDetail = true }
        )
        rankTitle(index)?.let { title ->
            Spacer(Modifier.width(7.dp))
            SoftTag(
                text = title,
                fg = MaterialTheme.cocColors.star,
                bg = MaterialTheme.cocColors.starSoft
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "${formatScore(score.score)}分",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (index == 1) MaterialTheme.cocColors.star
            else MaterialTheme.colorScheme.onSurface
        )
    }
    if (showDetail) {
        ScoreDetailDialog(score = score, onDismiss = { showDetail = false })
    }
}

private fun formatScore(v: Float): String = "%.1f".format(v)

/** 得分来源明细弹窗：逐项列出积分规则与该成员的实际数值 */
@Composable
private fun ScoreDetailDialog(score: TopMemberScore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${score.playerName} · ${formatScore(score.score)} 分")
                Text(
                    "积分制仅统计部落战",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                ScoreRuleRow("每获得一颗星", "+1/颗", score.totalStars, per = 1)
                ScoreRuleRow("每次 100% 摧毁率", "+1/次", score.threeStarCount, per = 1)
                ScoreRuleRow("单场拿满 6 星", "+2/场", score.fullStarEvents, per = 2)
                ScoreRuleRow("参战但空 1 个进攻机会", "-3/场", score.missedAttackCount, per = -3)
                ScoreRuleRow("两次进攻全空", "-10/场", score.noAttackCount, per = -10)
                ScoreRuleRow("名单成员未参与该场", "-4/场", score.absentCount, per = -4)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "合计得分",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${formatScore(score.score)} 分",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.cocColors.star
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

/** 积分规则行：规则名 + 单位说明×次数 + 小计 */
@Composable
private fun ScoreRuleRow(label: String, unit: String, count: Int, per: Int) {
    val points = per * count
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$unit × $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            when {
                points > 0 -> "+$points"
                points < 0 -> "$points"
                else -> "±0"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                points > 0 -> MaterialTheme.cocColors.accent
                points < 0 -> MaterialTheme.cocColors.danger
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

// ==================== Tab 1: 成员统计 ====================

@Composable
private fun MembersTab(
    stats: List<MemberMonthlyStat>,
    modifier: Modifier = Modifier,
    onMemberClick: (MemberMonthlyStat) -> Unit
) {
    if (stats.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无成员数据")
        }
        return
    }

    Column(modifier) {
        // 表头：名次 / 成员 / 参战 / 星数 / 三星率
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
        ) {
            Text("名次", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
            Text("成员", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("参战", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
            Text("星数", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
            Text("三星率", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        }

        // 无卡片行列表，发丝线分隔；点击行弹出成员详情
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(stats, key = { _, s -> s.playerName }) { index, stat ->
                MemberStatRow(stat = stat, rank = index + 1, onClick = { onMemberClick(stat) })
                if (index < stats.lastIndex) {
                    Box(
                        Modifier
                            .padding(start = 56.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline)
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 领奖台排名徽章：前三名金/银/铜圆片（随明暗模式调色），其余名次为弱化序号 */
@Composable
private fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val isDark = isSystemInDarkTheme()
    val podium = when (rank) {
        1 -> Color(0xFFC9A227)   // 金
        2 -> Color(0xFF9AA3AD)   // 银
        3 -> Color(0xFFB87333)   // 铜
        else -> null
    }
    if (podium == null) {
        Text(
            "%02d".format(rank),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = modifier
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isDark) podium.copy(alpha = 0.26f)
                else podium.copy(alpha = 0.14f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isDark) podium else lerp(podium, Color.Black, 0.3f)
        )
    }
}

@Composable
private fun MemberStatRow(
    stat: MemberMonthlyStat,
    rank: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            RankBadge(rank = rank, size = 28.dp)
        }
        Text(
            stat.playerName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = roleColor(stat.role),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${stat.attacked}/${stat.totalEvents}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(40.dp)
        )
        Text(
            "${stat.totalStars}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
        Text(
            formatPercent(stat.threeStarRate * 100),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = threeStarRateColor(stat.threeStarRate),
            modifier = Modifier.width(56.dp)
        )
    }
}

/** 排名页成员详情弹窗：表格展示本月逐场战报数据。 */
@Composable
private fun MemberDetailDialog(
    stat: MemberMonthlyStat,
    details: List<MemberEventDetail>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${stat.playerName} · ${stat.totalStars}★", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "本月 ${roleLabel(stat.role)} · 参战 ${stat.attacked}/${stat.totalEvents} · 三星率 ${formatPercent(stat.threeStarRate * 100)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // 表头：战报 / 进攻1 / 进攻2 / 星数
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("战报", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.4f))
                    Text("进攻1", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
                    Text("进攻2", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
                    Text("星数", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
                if (details.isEmpty()) {
                    Text(
                        "本月无参战记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else {
                    details.forEachIndexed { index, detail ->
                        MemberDetailRow(detail)
                        if (index < details.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.cocColors.hairline.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 弹窗内单行：战报名 + 两次进攻摧毁率 + 该场星数。 */
@Composable
private fun MemberDetailRow(detail: MemberEventDetail) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val displayName = detail.eventName.ifBlank {
            if (detail.eventType == "league") "联赛" else "部落战"
        }
        Text(
            displayName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f)
        )
        Text(
            attackCell(detail.attacks.getOrNull(0)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            attackCell(detail.attacks.getOrNull(1)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            "${detail.stars}★",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = when {
                detail.stars >= 6 -> MaterialTheme.cocColors.accent
                detail.stars >= 3 -> MaterialTheme.cocColors.star
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(0.4f)
        )
    }
}

/** 进攻单元格：已使用显示摧毁率百分比，未使用显示 —。 */
private fun attackCell(attack: Attack?): String =
    if (attack != null && attack.isUsed()) "${attack.destructionPercentage}%" else "—"

// ==================== Tab 2: 未进攻排行 ====================

@Composable
private fun MissedTab(
    ranks: List<RecentMissedRank>,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 20.dp)) {

        if (ranks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(title = "全员均有进攻", body = "统计范围内无人缺席")
            }
        } else {
            LazyColumn {
                itemsIndexed(ranks, key = { _, r -> r.playerName }) { index, rank ->
                    MissedRankRow(rank = rank, index = index + 1)
                    if (index < ranks.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 44.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedRankRow(rank: RecentMissedRank, index: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(index),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (index <= 3) MaterialTheme.cocColors.danger
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(34.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                rank.playerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = roleColor(rank.role)
            )
            Spacer(Modifier.height(1.dp))
            Text(
                roleLabel(rank.role),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${rank.missedCount} 次未进攻",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.cocColors.danger
        )
    }
}
