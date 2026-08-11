package com.cocwar.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.Attack
import com.cocwar.data.model.isUsed
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.formatPercent
import com.cocwar.ui.util.roleLabel

// ==================== Tab 1: 成员统计 ====================

@Composable
internal fun MembersTab(
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
internal fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 28.dp) {
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
            "${stat.attacked}/${stat.participated}",
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
internal fun MemberDetailDialog(
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
                    "本月 ${roleLabel(stat.role)} · 参战 ${stat.attacked}/${stat.participated} · 三星率 ${formatPercent(stat.threeStarRate * 100)}",
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
