package com.cocwar.ui.season

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.domain.LeagueRoundSummary
import com.cocwar.domain.LeagueSeasonStats
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SoftTag
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.formatPercent

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
                if (stats != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "赛季聚合",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        SoftTag(
                            text = "${stats!!.rounds.size}/7 轮已记录",
                            fg = MaterialTheme.cocColors.star,
                            bg = MaterialTheme.cocColors.starSoft
                        )
                    }
                } else {
                    Text(
                        "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                EmptyState(
                    title = "该场联赛暂无数据",
                    body = "战报名需为 SAABBCC 格式，且 CC 属于该场次"
                )
            }
            else -> SeasonContent(stats = stats!!, matchLabel = matchLabel)
        }
    }
}

@Composable
private fun SeasonContent(stats: LeagueSeasonStats, matchLabel: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // === 赛季渐变总览卡 ===
        SeasonHeroCard(stats, matchLabel)
        Spacer(Modifier.height(4.dp))

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
                        // 缺阵徽章：有缺阵显示红色胶囊，否则弱化「0」
                        if (m.absentRounds > 0) {
                            SoftTag(
                                text = "${m.absentRounds}",
                                fg = MaterialTheme.cocColors.danger,
                                bg = MaterialTheme.cocColors.dangerSoft,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                "0",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
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

// ===== 赛季总览：渐变卡（场次胶囊 + 赛季总星 + 满星率环 + 底部指标行） =====

@Composable
private fun SeasonHeroCard(stats: LeagueSeasonStats, matchLabel: String) {
    val isDark = isSystemInDarkTheme()
    val star = MaterialTheme.cocColors.star
    val starSoft = MaterialTheme.cocColors.starSoft
    val ink = MaterialTheme.colorScheme.onSurface
    val inkSoft = MaterialTheme.colorScheme.onSurfaceVariant
    // 联赛语义色（黄铜）淡染渐变，比统计页总览更浅
    val brush = Brush.linearGradient(
        colors = listOf(
            star.copy(alpha = if (isDark) 0.16f else 0.11f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val totalRounds = stats.rounds.size.coerceAtLeast(1)
    val fullRate = (stats.fullRoundCount.toFloat() / totalRounds).coerceIn(0f, 1f)
    val attackers = stats.rounds.sumOf { it.attackerCount }
    val memberCount = stats.rounds.sumOf { it.totalMembers }
    val attackRate = if (memberCount > 0) attackers.toFloat() / memberCount else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(Modifier.background(brush)) {
            // 装饰：右上角半透明同心圆环
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .size(96.dp)
                    .border(12.dp, star.copy(alpha = 0.10f), CircleShape)
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 场次胶囊
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = starSoft
                    ) {
                        Text(
                            "联赛 · $matchLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = star,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "赛季聚合",
                        style = MaterialTheme.typography.labelSmall,
                        color = inkSoft
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 赛季总星 + 满星轮数
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${stats.totalStars}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "/ ${stats.maxStars} 理论最大",
                                style = MaterialTheme.typography.labelMedium,
                                color = inkSoft,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "满星轮数",
                                style = MaterialTheme.typography.labelSmall,
                                color = inkSoft
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${stats.fullRoundCount}/${stats.rounds.size} 轮",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = star
                            )
                        }
                    }
                    // 满星率环
                    SeasonRingStat(
                        rate = fullRate,
                        color = star,
                        size = 62.dp,
                        strokeWidth = 5.dp,
                        labelColor = ink,
                        trackColor = starSoft
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
                // 底部指标行：场均星数 / 平均出手率 / 满星率
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SeasonMiniStat("场均星数", "%.1f".format(stats.totalStars.toFloat() / totalRounds), ink)
                    SeasonMiniStat("平均出手率", formatPercent(attackRate * 100), ink)
                    SeasonMiniStat("满星率", formatPercent(fullRate * 100), ink)
                }
            }
        }
    }
}

/** 渐变卡底部小指标：值 + 标签，垂直居中 */
@Composable
private fun SeasonMiniStat(label: String, value: String, ink: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 环形进度（圆头描边），与统计页 RingStat 同款 */
@Composable
private fun SeasonRingStat(
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

@Composable
private fun RoundRow(round: LeagueRoundSummary) {
    val fraction = if (round.maxStars > 0) round.clanTotalStars.toFloat() / round.maxStars else 0f
    val barColor = if (round.isFullStar) MaterialTheme.cocColors.star else MaterialTheme.cocColors.accent
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
                    SoftTag(
                        text = "满星",
                        fg = MaterialTheme.cocColors.star,
                        bg = MaterialTheme.cocColors.starSoft
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
        Column(horizontalAlignment = Alignment.End) {
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
            Spacer(Modifier.height(4.dp))
            // 星数进度条：满星轮金色实条，未满星轮强调色
            Box(
                Modifier
                    .width(64.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.cocColors.hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(barColor)
                )
            }
        }
    }
}
