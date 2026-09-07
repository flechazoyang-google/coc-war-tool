package com.cocwar.ui.importflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.cocwar.ui.theme.cocColors

/**
 * 入册确认闸弹窗：列出即将加入花名册的新名字中与在册成员疑似同名的项，
 * 逐个确认「合并到在册成员」或「确为新成员」。全部选「合并」可完全避免
 * OCR 错名进入花名册污染后续匹配。
 *
 * @param onConfirm 传入 名字 → 是否为同一人（true = 合并到 suggestion）
 */
@Composable
fun RosterConflictDialog(
    conflicts: List<RosterConflict>,
    onConfirm: (Map<String, Boolean>) -> Unit,
    onDismiss: () -> Unit
) {
    // 默认选择：相似度 ≥ 0.6（高置信，与导入预览建议的默认一致）默认合并，否则默认新成员
    val choices = remember(conflicts) {
        mutableStateOf(conflicts.associate { it.newName to (it.score >= 0.6f) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("疑似同名成员确认") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "以下新成员与在册成员名字高度相似，可能是识别错字或重名。请确认是否为同一人；确认合并不会重复加入花名册。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    conflicts.forEach { conflict ->
                        ConflictRow(
                            conflict = conflict,
                            isSamePerson = choices.value[conflict.newName] ?: false,
                            onChoice = { same ->
                                choices.value = choices.value.toMutableMap().apply {
                                    put(conflict.newName, same)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choices.value) }) {
                Text("继续保存", color = MaterialTheme.cocColors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("返回检查") }
        }
    )
}

@Composable
private fun ConflictRow(
    conflict: RosterConflict,
    isSamePerson: Boolean,
    onChoice: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "「${conflict.newName}」",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.cocColors.danger
            )
            Text(
                " 疑似与在册 ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "「${conflict.suggestion}」",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                " 为同一人",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "相似度 ${(conflict.score * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChoice(true) }
            ) {
                RadioButton(selected = isSamePerson, onClick = { onChoice(true) })
                Text(
                    "同一人，并入「${conflict.suggestion}」",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChoice(false) }
            ) {
                RadioButton(selected = !isSamePerson, onClick = { onChoice(false) })
                Text(
                    "是新成员，保留「${conflict.newName}」",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
