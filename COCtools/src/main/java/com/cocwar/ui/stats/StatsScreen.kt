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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.domain.StatsOverview
import com.cocwar.domain.TopMemberScore
import com.cocwar.domain.TypeStats
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.FilterPill
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
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

@Composable
private fun progressBarColor(progress: Float): Color = when {
    progress >= 0.8f -> MaterialTheme.cocColors.accent
    progress >= 0.5f -> MaterialTheme.cocColors.star
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

    var tab by rememberSaveable { mutableIntStateOf(0) }  // 0=总览, 1=排名, 2=预警, 3=本月最佳

    // 视图标签
    val viewLabels = listOf("总览", "排名", "预警", "本月最佳")

    // 筛选持久化 —— rememberSaveable
    var typeFilterIndex by rememberSaveable { mutableIntStateOf(0) }  // 0=部落战(默认), 1=联赛
    var savedMonthLabel by rememberSaveable { mutableStateOf("") }   // 持久化月份标签
    var showFilterDialog by remember { mutableStateOf(false) }

    // 筛选对话框的编辑状态（级联选择用）
    var editViewTab by rememberSaveable { mutableIntStateOf(0) }
    var editTypeIndex by rememberSaveable { mutableIntStateOf(0) }
    var editSortByIndex by remember { mutableIntStateOf(0) }
    var editRecentN by rememberSaveable { mutableIntStateOf(0) }
    var editMonthLabel by rememberSaveable { mutableStateOf("") }

    // 打开对话框时用当前值初始化编辑状态
    LaunchedEffect(showFilterDialog) {
        if (showFilterDialog) {
            editViewTab = tab
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
    val isFilterActive = typeFilterIndex != 0 || (selectedMonth != null && selectedMonth != defaultMonth) || tab != 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "统计·${viewLabels[tab]}",
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
            else -> when (tab) {
                0 -> OverviewTab(overview, Modifier.weight(1f))
                1 -> MembersTab(memberStats, Modifier.weight(1f))
                2 -> MissedTab(recentMissed, Modifier.weight(1f))
                3 -> TopMembersTab(topMembers, Modifier.weight(1f))
            }
        }
    }

    // 筛选对话框：级联选择（视图 → 类型/排序/时间段 → 月份）
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("筛选") },
            text = {
                Column {
                    // 视图区（始终显示）
                    Text("视图", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewLabels.forEachIndexed { index, label ->
                            FilterPill(
                                label = label,
                                selected = editViewTab == index,
                                onClick = { editViewTab = index }
                            )
                        }
                    }

                    // 类型区（仅排名/预警时显示；总览与本月最佳固定展示全量数据）
                    if (editViewTab == 1 || editViewTab == 2) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("类型", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TypeFilter.entries.forEach { filter ->
                                FilterPill(
                                    label = filter.label,
                                    selected = editTypeIndex == filter.ordinal,
                                    onClick = { editTypeIndex = filter.ordinal }
                                )
                            }
                        }
                    }

                    // 排序方式（仅排名时显示）
                    if (editViewTab == 1) {
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
                    if (editViewTab == 2) {
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
                    tab = editViewTab
                    // 类型筛选仅对排名/预警生效（总览与本月最佳固定全量数据）
                    if (editViewTab == 1 || editViewTab == 2) {
                        typeFilterIndex = editTypeIndex.coerceIn(0, TypeFilter.entries.lastIndex)
                        viewModel.setTypeFilter(TypeFilter.entries[typeFilterIndex])
                    }

                    // 排序方式仅对排名生效
                    if (editViewTab == 1) {
                        val newSort = MemberSortBy.entries[editSortByIndex]
                        viewModel.setSortBy(newSort)
                    }

                    // 时间段仅对预警生效
                    if (editViewTab == 2) {
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
}

// ==================== Tab 0: 总览 ====================

@Composable
private fun OverviewTab(
    overview: StatsOverview?,
    modifier: Modifier = Modifier
) {
    if (overview == null || overview.totalEvents == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无战报", body = "切换月份查看历史复盘")
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // === 月度总览平面卡片 ===
        item { HeroCard(overview) }

        // === 部落战区块 ===
        item { SectionTitle("部落战") }
        overview.war?.let { war ->
            item { TypeStatsCard(war) }
        } ?: item { EmptyTypeHint("本月无部落战") }

        // === 联赛区块 ===
        item { SectionTitle("联赛") }
        overview.league?.let { league ->
            item { TypeStatsCard(league) }
        } ?: item { EmptyTypeHint("本月无联赛") }

        item { Spacer(Modifier.height(24.dp)) }
    }
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
                    "总星数", "${overview.totalStars}", Modifier.weight(1f),
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

            // 部落战 vs 联赛对比条
            if (overview.war != null || overview.league != null) {
                val warStars = overview.war?.totalStars ?: 0
                val leagueStars = overview.league?.totalStars ?: 0
                val totalTypeStars = warStars + leagueStars
                val warRatio = if (totalTypeStars > 0) warStars.toFloat() / totalTypeStars else 0.5f

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "部落战 ${overview.war?.eventCount ?: 0}场 · ${warStars}星",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.accent
                    )
                    Text(
                        "联赛 ${overview.league?.eventCount ?: 0}场 · ${leagueStars}星",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.star
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                ) {
                    Box(
                        Modifier
                            .weight(warRatio.coerceIn(0.04f, 0.96f))
                            .height(5.dp)
                            .background(MaterialTheme.cocColors.accent)
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .weight((1f - warRatio).coerceIn(0.04f, 0.96f))
                            .height(5.dp)
                            .background(MaterialTheme.cocColors.star)
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

@Composable
private fun EmptyTypeHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

// ===== 单类型统计卡片 =====

@Composable
private fun TypeStatsCard(stats: TypeStats) {
    CocCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                CountStat("场次", "${stats.eventCount}", Modifier.weight(1f))
                CountStat("总星数", "${stats.totalStars}", Modifier.weight(1f),
                    valueColor = MaterialTheme.cocColors.star)
                CountStat("均星", "%.1f".format(stats.avgStarsPerEvent), Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.cocColors.hairline)
            )
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth()) {
                RingStatTile("进攻率", stats.attackRate, attackRateColor(stats.attackRate), Modifier.weight(1f))
                RingStatTile("三星率", stats.threeStarRate, threeStarRateColor(stats.threeStarRate), Modifier.weight(1f))
                RingStatTile(
                    "均摧毁", stats.avgDestruction / 100f,
                    if (stats.avgDestruction >= 90f) MaterialTheme.cocColors.accent
                    else if (stats.avgDestruction >= 70f) MaterialTheme.cocColors.star
                    else MaterialTheme.cocColors.danger,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

/** 环形进度 + 标签 */
@Composable
private fun RingStatTile(
    label: String,
    rate: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        RingStat(rate = rate, color = color, size = 50.dp, strokeWidth = 4.dp)
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 大数字 + 标签 */
@Composable
private fun CountStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
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

// ===== 本月最佳：表格化序号行 =====

@Composable
private fun TopScoreRow(index: Int, score: TopMemberScore) {
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
            color = if (index == 1) MaterialTheme.cocColors.star
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Start
        )
        Column(Modifier.weight(1f)) {
            Text(
                score.playerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = roleColor(score.role)
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "${roleLabel(score.role)} · 参战 ${score.attacked}/${score.totalWarEvents} · 三星 ${score.threeStarCount}次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${"%.1f".format(score.score)}分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (index == 1) MaterialTheme.cocColors.star
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "${score.totalStars}星 · 三星率 ${formatPercent(score.threeStarRate * 100)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== Tab 1: 成员统计 ====================

@Composable
private fun MembersTab(
    stats: List<MemberMonthlyStat>,
    modifier: Modifier = Modifier
) {
    if (stats.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "本月暂无成员数据")
        }
        return
    }

    Column(modifier) {
        // 表头
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 6.dp)
        ) {
            Text("成员", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
            Text("参战", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
            Text("星数", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
            Text("三星率", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stats, key = { it.playerName }) { stat -> MemberStatCard(stat) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MemberStatCard(stat: MemberMonthlyStat) {
    val progress = if (stat.totalEvents > 0) stat.attacked.toFloat() / stat.totalEvents else 0f
    val barColor = progressBarColor(progress)

    CocCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.5f)) {
                    Text(
                        stat.playerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColor(stat.role)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        roleLabel(stat.role),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${stat.participated}/${stat.totalEvents}",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.8f))
                Text("${stat.totalStars}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text(formatPercent(stat.threeStarRate * 100),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = threeStarRateColor(stat.threeStarRate),
                    modifier = Modifier.weight(0.9f))
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("均星 ${"%.1f".format(stat.avgStars)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("均摧毁 ${formatPercent(stat.avgDestruction)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("三星 ${stat.threeStarCount}次", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stat.missedCount > 0) {
                    Text("未进攻 ${stat.missedCount}次", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.cocColors.danger)
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

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
