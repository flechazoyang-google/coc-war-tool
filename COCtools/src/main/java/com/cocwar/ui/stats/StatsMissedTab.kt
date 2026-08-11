package com.cocwar.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.domain.RecentMissedRank
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel

// ==================== Tab 2: 未进攻排行 ====================

@Composable
internal fun MissedTab(
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
