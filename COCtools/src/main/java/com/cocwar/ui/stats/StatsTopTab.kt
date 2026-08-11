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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cocwar.domain.TopMemberScore
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SoftTag
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor

// ===== 积分排行：独立视图，展示全部成员得分（按得分降序） =====

@Composable
internal fun TopMembersTab(
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
