package com.cocwar.ui.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel

/**
 * 全局合并成员弹窗：两步流——
 * 1. 选择合并目标（搜索过滤 + 在册名单，排除自己）；
 * 2. 确认影响面（涉及 N 行战报记录），确认后执行。
 */
@Composable
fun MergeMemberDialog(
    fromName: String,
    roster: List<MemberRosterEntity>,
    loadAffectedCount: suspend (String) -> Int,
    onMerge: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf<String?>(null) }
    target?.let { to ->
        MergeConfirmDialog(
            fromName = fromName,
            toName = to,
            loadAffectedCount = loadAffectedCount,
            onConfirm = { onMerge(to) },
            onBack = { target = null }
        )
    } ?: run {
        MergeTargetPickerDialog(
            fromName = fromName,
            roster = roster,
            onPick = { target = it },
            onDismiss = onDismiss
        )
    }
}

/** 第一步：选择合并目标（按名字搜索过滤，排除被合并者本人）。 */
@Composable
private fun MergeTargetPickerDialog(
    fromName: String,
    roster: List<MemberRosterEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val candidates = remember(roster, query) {
        roster.filter { it.name != fromName && it.name.contains(query.trim()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合并「$fromName」到…") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "选择「$fromName」实际对应的成员，将修正所有战报记录并清理花名册。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索成员") },
                    singleLine = true,
                    shape = CocShape.field,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                        cursorColor = MaterialTheme.cocColors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text(
                        "没有匹配的成员",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.name }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(entry.name) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(roleColor(entry.role), CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        roleLabel(entry.role),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 第二步：确认影响面后执行合并。 */
@Composable
private fun MergeConfirmDialog(
    fromName: String,
    toName: String,
    loadAffectedCount: suspend (String) -> Int,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    var affected by remember { mutableIntStateOf(-1) }
    LaunchedEffect(fromName) { affected = loadAffectedCount(fromName) }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("确认合并") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.MergeType,
                        contentDescription = null,
                        tint = MaterialTheme.cocColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "「$fromName」 → 「$toName」",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (affected < 0) "统计影响面…"
                    else if (affected == 0) "该名字没有战报记录，仅处理花名册。"
                    else "将修正 $affected 行战报记录（同名场次自动合并进攻数据），并从花名册移除「$fromName」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = affected >= 0,
                onClick = onConfirm
            ) { Text("合并", color = MaterialTheme.cocColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("返回") }
        }
    )
}
