package com.cocwar.ui.stats

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.ui.util.roleColor

private val Gold = Color(0xFFFFC107)
private val Silver = Color(0xFF9E9E9E)
private val Bronze = Color(0xFFCD7F32)

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel: StatsViewModel = warViewModel { StatsViewModel(it) }
    val monthly by viewModel.monthlyStats.collectAsStateWithLifecycle()
    val recentMissed by viewModel.recentMissed.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

        Column(Modifier.fillMaxSize()) {
            Text(
                "成员统计",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("月度参战") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("未进攻排行") })
            }
            when (tab) {
                0 -> MonthlyTab(monthly, loading, Modifier.weight(1f))
                1 -> RecentMissedTab(recentMissed, viewModel, Modifier.weight(1f))
            }
        }
}

@Composable
private fun MonthlyTab(
    stats: List<MemberMonthlyStat>,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    if (loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (stats.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("本月暂无战报数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("成员", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
                Text("参战", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                Text("有效", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                Text("未进攻", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.8f))
            }
        }
        items(stats) { stat ->
            MonthlyStatRow(stat)
        }
    }
}

@Composable
private fun MonthlyStatRow(stat: MemberMonthlyStat) {
    val progress = if (stat.totalEvents > 0) stat.attacked.toFloat() / stat.totalEvents else 0f
    val barColor = when {
        progress >= 0.8f -> MaterialTheme.colorScheme.primary
        progress >= 0.5f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stat.playerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = roleColor(stat.role),
                    modifier = Modifier.weight(2f)
                )
                Text("${stat.participated}/${stat.totalEvents}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                Text(
                    "${stat.attacked}/${stat.participated}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stat.attacked < stat.participated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    "${stat.missedCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (stat.missedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.8f)
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentMissedTab(
    ranks: List<RecentMissedRank>,
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    var selectedN by remember { mutableIntStateOf(0) }

    Column(modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(3 to "近3次", 7 to "近7次", 0 to "当月全部")
            options.forEach { (n, label) ->
                FilterChip(
                    selected = selectedN == n,
                    onClick = { selectedN = n; viewModel.loadRecentMissed(n) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ranks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("所有成员均有进攻，无未进攻记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ranks) { rank ->
                    RankRow(rank, ranks.indexOf(rank) + 1)
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: RecentMissedRank, index: Int) {
    val medalColor = when (index) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (index <= 3) medalColor.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$index",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = medalColor,
                modifier = Modifier.width(36.dp)
            )
            Text(
                rank.playerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = roleColor(rank.role),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${rank.missedCount} 次",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
