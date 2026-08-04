package com.cocwar.ui.season

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.domain.LeagueRoundSummary
import com.cocwar.domain.LeagueSeasonStats
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.InfoRow
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.StatTile
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel

/**
 * 联赛赛季视图：某一整场联赛（月初场或月中场）的 7 轮聚合。
 * 展示每轮星数/满星/出手情况 + 成员出战轮换统计（谁打了几轮、得了几星、缺阵几轮）。
 */
@Composable
fun LeagueSeasonScreen(
    year: Int,
    month: Int,
    match: Int,
    onBack: () -> Unit
) {
    val viewModel: LeagueSeasonViewModel = warViewModel { LeagueSeasonViewModel(it) }
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(year, month, match) {
        viewModel.load(year, month, match)
    }

    val matchLabel = if (match == 1) "月初场" else "月中场"

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${year}年${month}月 · $matchLabel 联赛",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (stats != null) "赛季聚合 · ${stats!!.rounds.size}/7 轮已记录" else "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            loading && stats == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            stats == null || stats!!.rounds.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "该场联赛暂无数据\n（名称需为 SAABBCC 且 CC 属于该场次）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> SeasonContent(stats = stats!!)
        }
    }
}

@Composable
private fun SeasonContent(stats: LeagueSeasonStats) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 总览
        CocCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatTile("赛季总星", "${stats.totalStars}", Modifier.weight(1f))
                StatTile("理论最大", "${stats.maxStars}", Modifier.weight(1f))
                StatTile("满星轮数", "${stats.fullRoundCount}/${stats.rounds.size}", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))

        // 每轮摘要
        SectionTitle("轮次总览")
        CocCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                stats.rounds.forEachIndexed { index, round ->
                    RoundRow(round)
                    if (index < stats.rounds.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // 成员出战轮换
        SectionTitle("出战轮换")
        CocCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("成员", Modifier.weight(2.2f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("出战", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("出手", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("星数", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("三星", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("缺阵", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                stats.members.forEachIndexed { index, m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        roleColor(m.role),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                m.playerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Text("${m.playedRounds}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${m.attackedRounds}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Star, null,
                                tint = MaterialTheme.cocColors.star,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("${m.totalStars}", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Text("${m.threeStarCount}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${m.absentRounds}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (m.absentRounds > 0) MaterialTheme.cocColors.danger
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < stats.members.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.cocColors.hairline)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundRow(round: LeagueRoundSummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "第${round.round}轮",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (round.isFullStar) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "满星",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.cocColors.star
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${round.attackerCount}/${round.totalMembers} 人出手",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Star, null,
                tint = MaterialTheme.cocColors.star,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "${round.clanTotalStars}/${round.maxStars}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
