package com.cocwar.ui.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.ui.util.roleColor
import com.cocwar.ui.util.StringMatcher

data class MemberMatchState(
    val member: MemberEntity,
    val editedName: String,
    val matched: Boolean,
    val suggestion: String?,
    val acceptSuggestion: Boolean = false
)

@Composable
fun WarNameField(
    name: String, onNameChange: (String) -> Unit,
    isError: Boolean = false, errorText: String? = null
) {
    Column {
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("战报名称（必填）") },
            placeholder = { Text("自动生成，可修改") },
            modifier = Modifier.fillMaxWidth(), isError = isError,
            singleLine = true, shape = RoundedCornerShape(12.dp)
        )
        if (isError && errorText != null)
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp))
    }
}

@Composable
fun WarTypeRoundSection(eventType: String, onTypeChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = eventType == EVENT_TYPE_WAR, onClick = { onTypeChange(EVENT_TYPE_WAR) }, label = { Text("部落战") })
            FilterChip(selected = eventType == EVENT_TYPE_LEAGUE, onClick = { onTypeChange(EVENT_TYPE_LEAGUE) }, label = { Text("联赛") })
        }
    }
}

@Composable
fun WarPreviewCard(parsed: WarJsonParser.ParsedEvent) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${parsed.event.clanTotalStars}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text("总星数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${parsed.members.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text("成员数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 成员匹配预览 — 显示匹配/未匹配列表，未匹配项可编辑+模糊建议+复选框。
 */
@Composable
fun MemberMatchPreview(
    matchStates: List<MemberMatchState>,
    onNameEdit: (Int, String) -> Unit,
    onToggleSuggestion: (Int, Boolean) -> Unit
) {
    val matched = matchStates.filter { it.matched }
    val unmatched = matchStates.filter { !it.matched }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            if (matched.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("已匹配 ${matched.size} 人", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (unmatched.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("未匹配 ${unmatched.size} 人，请确认或修正", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(6.dp))
                unmatched.forEachIndexed { idx, state ->
                    val origIdx = matchStates.indexOf(state)
                    UnmatchedRow(state, origIdx, onNameEdit, onToggleSuggestion)
                }
            }
            if (unmatched.isEmpty() && matched.isNotEmpty()) {
                Text("所有成员均已匹配 ✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun UnmatchedRow(
    state: MemberMatchState,
    index: Int,
    onNameEdit: (Int, String) -> Unit,
    onToggleSuggestion: (Int, Boolean) -> Unit
) {
    val nameColor = roleColor(state.member.role)
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${state.member.rank}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
            OutlinedTextField(
                value = state.editedName,
                onValueChange = { onNameEdit(index, it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = nameColor, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(8.dp)
            )
            if (state.suggestion != null) {
                Spacer(Modifier.width(4.dp))
                Checkbox(
                    checked = state.acceptSuggestion,
                    onCheckedChange = { onToggleSuggestion(index, it) },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (state.suggestion != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                "→ ${state.suggestion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}

/** 构建匹配状态列表。roster 为正式名单。 */
fun buildMatchStates(parsed: WarJsonParser.ParsedEvent, roster: List<String>): List<MemberMatchState> =
    parsed.members.map { m ->
        val matched = m.playerName in roster
        val suggestion = if (!matched) {
            StringMatcher.bestMatch(m.playerName, roster, 0.6f)?.first
        } else null
        MemberMatchState(m, m.playerName, matched, suggestion)
    }
