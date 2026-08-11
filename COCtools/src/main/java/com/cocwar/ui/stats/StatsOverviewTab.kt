package com.cocwar.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cocwar.domain.EventStatSummary
import com.cocwar.domain.StatsOverview
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.util.formatPercent

// ==================== Tab 0: 总览 ====================

@Composable
internal fun OverviewTab(
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
                        // 数值列按列内最大值做相对分级（每列独立阈值），先算好传给每一行
                        val maxTotalStars = sortedSummaries.maxOfOrNull { it.totalStars } ?: 0
                        val maxThreeStar = sortedSummaries.maxOfOrNull { it.threeStarCount } ?: 0
                        val maxUsedAttacks = sortedSummaries.maxOfOrNull { it.totalUsedAttacks } ?: 0
                        sortedSummaries.forEachIndexed { index, summary ->
                            EventSummaryRow(
                                summary = summary,
                                index = index,
                                maxTotalStars = maxTotalStars,
                                maxThreeStar = maxThreeStar,
                                maxUsedAttacks = maxUsedAttacks,
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
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(0.8f))
        Text("总星数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(0.7f))
        Text("三星次数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(0.9f))
        Text("使用进攻次数", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1.3f))
        Text("三星率", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(0.8f))
        Text("参与率", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(0.8f))
    }
}

/** 参与率配色：90% 以上强调色，70% 以上警示色，否则危险色 */
@Composable
private fun participationRateColor(rate: Float): Color = when {
    rate >= 0.9f -> MaterialTheme.cocColors.accent
    rate >= 0.7f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

/**
 * 数值列相对分级：按列内最大值归一（rate = 本行值 / 列最大值），
 * 阈值每列独立设定——≥80% 强调色、≥50% 警示色、否则危险色。
 * 与三星率/参与率两列的三段色阶（绿/橙/红）语义保持一致。
 */
@Composable
private fun columnRelativeColor(rate: Float): Color = when {
    rate >= 0.8f -> MaterialTheme.cocColors.accent
    rate >= 0.5f -> MaterialTheme.cocColors.star
    else -> MaterialTheme.cocColors.danger
}

/** 单行：序号 + 总星数 + 三星次数 + 使用进攻次数 + 三星率 + 参与率，点击跳转战报详情 */
@Composable
private fun EventSummaryRow(
    summary: EventStatSummary,
    index: Int,
    maxTotalStars: Int,
    maxThreeStar: Int,
    maxUsedAttacks: Int,
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
        // 数值列按列内相对值分级配色（每列独立阈值），列最大值 0（全零）时回落默认墨色
        val starColor = if (maxTotalStars > 0) {
            columnRelativeColor(summary.totalStars.toFloat() / maxTotalStars)
        } else MaterialTheme.colorScheme.onSurface
        Text("${summary.totalStars}", style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = starColor,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
        val threeStarColor = if (maxThreeStar > 0) {
            columnRelativeColor(summary.threeStarCount.toFloat() / maxThreeStar)
        } else MaterialTheme.colorScheme.onSurface
        Text("${summary.threeStarCount}", style = MaterialTheme.typography.bodyMedium,
            color = threeStarColor,
            textAlign = TextAlign.Center, modifier = Modifier.weight(0.9f))
        val usedAttacksColor = if (maxUsedAttacks > 0) {
            columnRelativeColor(summary.totalUsedAttacks.toFloat() / maxUsedAttacks)
        } else MaterialTheme.colorScheme.onSurface
        Text("${summary.totalUsedAttacks}", style = MaterialTheme.typography.bodyMedium,
            color = usedAttacksColor,
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
                                // 数字与单位比例平衡：数字 displayMedium(57sp) → headlineMedium(28sp)，
                                // 单位 labelMedium(12sp) → titleSmall(14sp)，缩小两者字号差距
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "场战报",
                                style = MaterialTheme.typography.titleSmall,
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
                    // 进攻率环（中心标注指标含义）
                    RingStat(
                        rate = overview.overallAttackRate,
                        color = accent,
                        size = 62.dp,
                        strokeWidth = 5.dp,
                        labelColor = ink,
                        trackColor = accentSoft,
                        label = "进攻率"
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
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    label: String? = null
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
        // 环中心：百分比 + 下方小字说明该指标含义（如「进攻率」），避免数字无注解
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatPercent(rate * 100),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor
            )
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor.copy(alpha = 0.65f)
                )
            }
        }
    }
}
