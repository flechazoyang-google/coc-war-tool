package com.cocwar.ui.stats

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
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
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SoftTag
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.formatPercent
import com.cocwar.ui.util.roleLabel

// === 阈值配色（取自语义令牌） ===
@Composable
private fun attackRateColor(rate: Float): Color = when {
    rate >= 0.8f -> MaterialTheme.cocColors.accent
    rate >= 0.5f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

@Composable
private fun threeStarRateColor(rate: Float): Color = when {
    rate >= 0.5f -> MaterialTheme.cocColors.accent
    rate >= 0.3f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

// ==================== 主屏幕 ====================

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel: StatsViewModel = warViewModel { StatsViewModel(it) }
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val memberStats by viewModel.memberStats.collectAsStateWithLifecycle()
    val recentMissed by viewModel.recentMissed.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val recentMissedWindow by viewModel.recentMissedWindow.collectAsStateWithLifecycle()
    val topMembers by viewModel.topMembers.collectAsStateWithLifecycle()
    val eventSummaries by viewModel.eventSummaries.collectAsStateWithLifecycle()

    var currentView by rememberSaveable { mutableStateOf(StatsView.OVERVIEW) }

    // 排名页成员详情弹窗：点击成员行后展示本月逐场数据
    var detailPlayer by remember { mutableStateOf<MemberMonthlyStat?>(null) }

    // 筛选持久化 —— rememberSaveable
    var typeFilterIndex by rememberSaveable { mutableIntStateOf(0) }  // 0=部落战(默认), 1=联赛
    var savedMonthLabel by rememberSaveable { mutableStateOf("") }   // 持久化月份标签
    var showFilterDialog by remember { mutableStateOf(false) }

    // 筛选对话框的编辑状态（级联选择用）
    var editView by rememberSaveable { mutableStateOf(StatsView.OVERVIEW) }
    var editTypeIndex by rememberSaveable { mutableIntStateOf(0) }
    var editSortByIndex by remember { mutableIntStateOf(0) }
    var editRecentN by rememberSaveable { mutableIntStateOf(0) }
    var editMonthLabel by rememberSaveable { mutableStateOf("") }

    // 打开对话框时用当前值初始化编辑状态
    LaunchedEffect(showFilterDialog) {
        if (showFilterDialog) {
            editView = currentView
            editTypeIndex = typeFilterIndex
            editSortByIndex = MemberSortBy.entries.indexOf(sortBy)
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
                    icon = Icons.Filled.FilterList,
                    contentDescription = "筛选",
                    onClick = { showFilterDialog = true },
                    filled = isFilterActive
                )
            }
        )

        when {
            loading -> {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            availableMonths.isEmpty() -> {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(title = "暂无战报数据", body = "导入战报后这里会生成月度复盘")
                }
            }
            else -> when (currentView) {
                StatsView.OVERVIEW -> OverviewTab(
                    overview, eventSummaries, currentTypeFilter, Modifier.weight(1f)
                )
                StatsView.RANKING -> MembersTab(memberStats, Modifier.weight(1f)) { stat ->
                    detailPlayer = stat
                }
                StatsView.WARNING -> MissedTab(recentMissed, Modifier.weight(1f))
                StatsView.TOP -> TopMembersTab(topMembers, Modifier.weight(1f))
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatsView.forType(TypeFilter.entries[editTypeIndex]).forEach { view ->
                            FilterPill(
                                label = view.label,
                                selected = editView == view,
                                onClick = { editView = view }
                            )
                        }
                    }

                    // 排序方式（仅排名时显示）
                    if (editView == StatsView.RANKING) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("排序方式", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemberSortBy.entries.forEachIndexed { index, sort ->
                                FilterPill(
                                    label = sort.label,
                                    selected = editSortByIndex == index,
                                    onClick = { editSortByIndex = index }
                                )
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    // 排序方式仅对排名生效
                    if (editView == StatsView.RANKING) {
                        val newSort = MemberSortBy.entries[editSortByIndex]
                        viewModel.setSortBy(newSort)
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
    modifier: Modifier = Modifier
) {
    if (overview == null || overview.totalEvents == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无${typeFilter.label}数据", body = "切换月份或类型查看历史复盘")
        }
        return
    }

    // 折线图数据：每场星数，按创建时间升序
    val chartValues = remember(eventSummaries) {
        eventSummaries.sortedBy { it.createdAt }.map { it.totalStars.toFloat() }
    }
    val chartLabels = remember(eventSummaries, typeFilter) {
        val suffix = if (typeFilter == TypeFilter.LEAGUE) "轮" else "场"
        eventSummaries.sortedBy { it.createdAt }.indices.map { "第${it + 1}$suffix" }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // === 月度总览平面卡片 ===
        item { HeroCard(overview) }

        // === 每场星数趋势（折线图） ===
        item { SectionTitle("每场星数") }
        item {
            CocCard(Modifier.fillMaxWidth()) {
                if (chartValues.isEmpty()) {
                    Text(
                        "本月暂无${typeFilter.label}战报",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    StarTrendLineChart(
                        values = chartValues,
                        xLabels = chartLabels,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }
            }
        }

        // === 整体指标雷达图 ===
        item { SectionTitle("整体指标") }
        item {
            CocCard(Modifier.fillMaxWidth()) {
                OverviewRadarChart(
                    axes = radarAxes(overview),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 雷达图五维指标：全部归一化到 0~1 */
private fun radarAxes(overview: StatsOverview): List<RadarAxis> {
    val starRate = if (overview.totalPossibleAttacks > 0)
        (overview.totalStars.toFloat() / (overview.totalPossibleAttacks * 3)).coerceIn(0f, 1f)
    else 0f
    return listOf(
        RadarAxis("进攻率", overview.overallAttackRate.coerceIn(0f, 1f)),
        RadarAxis("三星率", overview.threeStarRate.coerceIn(0f, 1f)),
        RadarAxis("均摧毁", (overview.avgDestruction / 100f).coerceIn(0f, 1f)),
        RadarAxis("星率", starRate),
        RadarAxis("满星率", overview.fullStarRate.coerceIn(0f, 1f))
    )
}

// ===== 本月最佳：独立视图，展示全部成员得分（按得分降序） =====

@Composable
private fun TopMembersTab(
    topMembers: List<TopMemberScore>,
    modifier: Modifier = Modifier
) {
    if (topMembers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无部落战数据", body = "本月最佳积分制仅统计部落战")
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { SectionTitle("本月最佳 · 积分制") }
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

// ===== 月度总览：平面三栏 + 类型对比条 =====

@Composable
private fun HeroCard(overview: StatsOverview) {
    CocCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroStat("战报", "${overview.totalEvents}", Modifier.weight(1f))
                HeroDivider()
                HeroStat(
                    "满星率", formatPercent(overview.fullStarRate * 100), Modifier.weight(1f),
                    valueColor = MaterialTheme.cocColors.star
                )
                HeroDivider()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    RingStat(
                        rate = overview.overallAttackRate,
                        color = attackRateColor(overview.overallAttackRate),
                        size = 52.dp,
                        strokeWidth = 4.5.dp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "进攻率",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.cocColors.hairline)
    )
}

// ===== 环形进度（圆头描边，平面轨道） =====

@Composable
private fun RingStat(
    rate: Float,
    color: Color,
    size: Dp = 52.dp,
    strokeWidth: Dp = 4.5.dp,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            progress = { rate.coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
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

// ===== 本月最佳：精简行（排名 + 昵称 + 军衔 + 得分） =====

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
        Text(
            "%02d".format(index),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (index == 1) MaterialTheme.cocColors.star
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Start
        )
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
        Text(
            "%02d".format(rank),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(36.dp)
        )
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
