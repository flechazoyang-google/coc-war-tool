package com.cocwar.ui.importflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.FilterPill
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
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
            singleLine = true, shape = CocShape.field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                cursorColor = MaterialTheme.cocColors.accent
            )
        )
        if (isError && errorText != null)
            Text(errorText, color = MaterialTheme.cocColors.danger, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 3.dp))
    }
}

@Composable
fun WarTypeRoundSection(eventType: String, onTypeChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("类型", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill(
                label = "部落战",
                selected = eventType == EVENT_TYPE_WAR,
                onClick = { onTypeChange(EVENT_TYPE_WAR) }
            )
            FilterPill(
                label = "联赛",
                selected = eventType == EVENT_TYPE_LEAGUE,
                onClick = { onTypeChange(EVENT_TYPE_LEAGUE) }
            )
        }
    }
}

/**
 * 数据预览：平面双联数字 —— 总星数(黄铜) / 成员数(墨色)，中间细线分隔。
 */
@Composable
fun WarPreviewCard(parsed: WarJsonParser.ParsedEvent) {
    CocCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null,
                        tint = MaterialTheme.cocColors.star,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${parsed.event.clanTotalStars}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("总星数", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(MaterialTheme.cocColors.hairline)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${parsed.members.size}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("成员数", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    CocCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (matched.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null,
                        tint = MaterialTheme.cocColors.accent,
                        modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("已匹配 ${matched.size} 人",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.accent)
                }
            }
            if (unmatched.isNotEmpty()) {
                if (matched.isNotEmpty()) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null,
                        tint = MaterialTheme.cocColors.danger,
                        modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("未匹配 ${unmatched.size} 人，请确认或修正",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.danger)
                }
                Spacer(Modifier.height(8.dp))
                unmatched.forEach { state ->
                    val origIdx = matchStates.indexOf(state)
                    UnmatchedRow(state, origIdx, onNameEdit, onToggleSuggestion)
                }
            }
            if (unmatched.isEmpty() && matched.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("所有成员均已匹配",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${state.member.rank}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )
            OutlinedTextField(
                value = state.editedName,
                onValueChange = { onNameEdit(index, it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = nameColor,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = CocShape.chip,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                    cursorColor = MaterialTheme.cocColors.accent
                )
            )
            if (state.suggestion != null) {
                Checkbox(
                    checked = state.acceptSuggestion,
                    onCheckedChange = { onToggleSuggestion(index, it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.cocColors.accent
                    )
                )
            }
        }
        if (state.suggestion != null) {
            Text(
                "建议改为：${state.suggestion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.cocColors.danger,
                modifier = Modifier.padding(start = 30.dp)
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
