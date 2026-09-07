package com.cocwar.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocwar.ui.theme.cocColors

/**
 * 数据体检弹窗：扫描识别错名残留与花名册疑似重复条目，逐项
 * 「合并」（复用全局合并，自动修正所有战报）或「忽略」（持久化）。
 */
@Composable
fun HealthCheckDialog(
    issues: List<HealthIssue>,
    onMerge: (HealthIssue) -> Unit,
    onIgnore: (HealthIssue) -> Unit,
    onDismiss: () -> Unit
) {
    // 待二次确认的合并项（显示影响面，防误触）
    var confirming by remember { mutableStateOf<HealthIssue?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据体检") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (issues.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.cocColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "未发现可疑数据，名单与战报记录一致",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "发现 ${issues.size} 项疑似同名数据（识别错名或重复条目），" +
                            "逐项确认后自动修正所有相关战报。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(issues, key = { it.name }) { issue ->
                            IssueRow(
                                issue = issue,
                                onMerge = { confirming = issue },
                                onIgnore = { onIgnore(issue) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    // 合并确认：展示两侧行数与类型说明
    confirming?.let { issue ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("确认合并") },
            text = {
                Text(
                    "将把「${issue.name}」（${issue.rowCount} 行）并入「${issue.suggestion}」" +
                        "（${issue.suggestionRowCount} 行），同名场次进攻数据自动合并。此操作修正所有战报，花名册同步清理。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onMerge(issue)
                    confirming = null
                }) { Text("合并", color = MaterialTheme.cocColors.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun IssueRow(
    issue: HealthIssue,
    onMerge: () -> Unit,
    onIgnore: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                issue.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.cocColors.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "（${issue.rowCount} 行）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "  →  ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                issue.suggestion,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "（${issue.suggestionRowCount} 行）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val kindLabel = if (issue.inRoster) "花名册低频条目，疑似同一人拆成两条"
        else "不在花名册，疑似识别错名"
        Text(
            "$kindLabel · 相似度 ${(issue.score * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Row {
            TextButton(onClick = onMerge) {
                Text("合并", color = MaterialTheme.cocColors.accent, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onIgnore) {
                Text("忽略", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
