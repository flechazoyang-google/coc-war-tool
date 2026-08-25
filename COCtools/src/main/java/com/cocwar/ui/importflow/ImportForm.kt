package com.cocwar.ui.importflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.util.Calendar
import java.util.TimeZone

/** 未匹配成员的处理方式 */
enum class MatchOption(val label: String) {
    USE_SUGGESTION("使用建议"),
    PICK_FROM_ROSTER("从名单中选择"),
    AS_NEW_MEMBER("作为新成员导入")
}

data class MemberMatchState(
    val member: MemberEntity,
    val editedName: String,
    val matched: Boolean,
    val suggestion: String?,
    val matchOption: MatchOption,
    val selectedRosterName: String? = null,
    val dropdownExpanded: Boolean = false
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
 * 成员匹配预览 — 显示匹配/未匹配列表，未匹配项可通过下拉选择处理方式。
 */
@Composable
fun MemberMatchPreview(
    matchStates: List<MemberMatchState>,
    roster: List<String>,
    onNameEdit: (Int, String) -> Unit,
    onOptionChange: (Int, MatchOption) -> Unit,
    onRosterPick: (Int, String) -> Unit,
    onToggleDropdown: (Int) -> Unit
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
                    Text("未匹配 ${unmatched.size} 人，请选择处理方式",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.danger)
                }
                Spacer(Modifier.height(8.dp))
                unmatched.forEach { state ->
                    val origIdx = matchStates.indexOf(state)
                    UnmatchedRow(
                        state = state,
                        index = origIdx,
                        roster = roster,
                        onNameEdit = onNameEdit,
                        onOptionChange = onOptionChange,
                        onRosterPick = onRosterPick,
                        onToggleDropdown = onToggleDropdown
                    )
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
    roster: List<String>,
    onNameEdit: (Int, String) -> Unit,
    onOptionChange: (Int, MatchOption) -> Unit,
    onRosterPick: (Int, String) -> Unit,
    onToggleDropdown: (Int) -> Unit
) {
    val nameColor = roleColor(state.member.role)
    val isExpanded = state.dropdownExpanded

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 主行：序号 + 名字 + 展开按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${state.member.rank}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )

            // 名字区域：根据选项显示不同状态
            val displayName = when (state.matchOption) {
                MatchOption.USE_SUGGESTION -> state.suggestion ?: state.editedName
                MatchOption.PICK_FROM_ROSTER -> state.selectedRosterName ?: "请选择成员…"
                MatchOption.AS_NEW_MEMBER -> state.editedName
            }
            val isEditable = state.matchOption == MatchOption.AS_NEW_MEMBER

            OutlinedTextField(
                value = displayName,
                onValueChange = { if (isEditable) onNameEdit(index, it) },
                readOnly = !isEditable,
                enabled = isEditable || state.matchOption != MatchOption.PICK_FROM_ROSTER ||
                        (state.matchOption == MatchOption.PICK_FROM_ROSTER && state.selectedRosterName != null),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = if (isExpanded && !isEditable)
                        MaterialTheme.colorScheme.primary
                    else nameColor,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = CocShape.chip,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.cocColors.hairline,
                    cursorColor = MaterialTheme.cocColors.accent,
                    disabledBorderColor = if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.cocColors.hairline,
                    disabledTextColor = if (isExpanded) MaterialTheme.colorScheme.primary
                    else nameColor
                )
            )

            Spacer(Modifier.width(4.dp))
            // 展开/收起按钮
            TextButton(
                onClick = { onToggleDropdown(index) },
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 展开的下拉选项区
        if (isExpanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 选项1：使用建议
                    if (state.suggestion != null) {
                        OptionRow(
                            label = "使用建议 \"${state.suggestion}\"",
                            selected = state.matchOption == MatchOption.USE_SUGGESTION,
                            onClick = { onOptionChange(index, MatchOption.USE_SUGGESTION) }
                        )
                    }

                    // 选项2：从名单中选择
                    OptionRow(
                        label = "从名单中选择",
                        selected = state.matchOption == MatchOption.PICK_FROM_ROSTER,
                        onClick = { onOptionChange(index, MatchOption.PICK_FROM_ROSTER) }
                    )

                    // 从名单选择的子列表
                    if (state.matchOption == MatchOption.PICK_FROM_ROSTER && roster.isNotEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 2.dp, bottom = 2.dp)
                                .heightIn(max = 140.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            LazyColumn {
                                items(roster) { name ->
                                    val isPicked = name == state.selectedRosterName
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onRosterPick(index, name) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPicked) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.cocColors.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isPicked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isPicked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 选项3：作为新成员导入
                    OptionRow(
                        label = "作为新成员导入（可修改名称）",
                        selected = state.matchOption == MatchOption.AS_NEW_MEMBER,
                        onClick = { onOptionChange(index, MatchOption.AS_NEW_MEMBER) }
                    )
                }
            }
        }
    }
}

/** 下拉菜单中的单选行 */
@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.cocColors.accent
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 构建匹配状态列表。roster 为正式名单。 */
fun buildMatchStates(parsed: WarJsonParser.ParsedEvent, roster: List<String>): List<MemberMatchState> =
    parsed.members.map { m ->
        val matched = m.playerName in roster
        val suggestion = if (!matched) {
            StringMatcher.bestMatch(m.playerName, roster, 0.6f)?.first
        } else null
        val defaultOption = if (suggestion != null) MatchOption.USE_SUGGESTION else MatchOption.AS_NEW_MEMBER
        MemberMatchState(
            member = m,
            editedName = m.playerName,
            matched = matched,
            suggestion = suggestion,
            matchOption = defaultOption
        )
    }

/** 导入 diff 摘要：总数 / 已在名单 / 名单外新成员（RULES §4.12）。 */
data class MemberDiffSummary(
    val total: Int,
    val inRoster: Int,
    val newNames: Int
)

/**
 * 构建导入 diff 摘要：按「最终保存名」判定——与名单匹配的名字计入已在名单，
 * 其余为名单外新成员（保存时自动加入花名册）。
 */
fun buildDiffSummary(
    matchStates: List<MemberMatchState>,
    roster: List<String>
): MemberDiffSummary {
    val finalNames = matchStates.map { state ->
        when (state.matchOption) {
            MatchOption.USE_SUGGESTION -> state.suggestion ?: state.editedName
            MatchOption.PICK_FROM_ROSTER -> state.selectedRosterName ?: state.editedName
            MatchOption.AS_NEW_MEMBER -> state.editedName
        }
    }
    val inRoster = finalNames.count { it in roster }
    return MemberDiffSummary(
        total = finalNames.size,
        inRoster = inRoster,
        newNames = finalNames.size - inRoster
    )
}

internal val WEEKDAY_LABELS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

internal fun formatWarDate(dateMillis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getDefault()).apply { this.timeInMillis = dateMillis }
    val weekday = WEEKDAY_LABELS[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 $weekday"
}

internal fun adjustParsedDate(
    parsed: WarJsonParser.ParsedEvent,
    dateMillis: Long
): WarJsonParser.ParsedEvent {
    val newId = "${parsed.event.eventType}_${dateMillis}_${System.nanoTime()}"
    return parsed.copy(
        event = parsed.event.copy(createdAt = dateMillis, eventId = newId),
        members = parsed.members.map {
            it.copy(eventId = newId, id = "$newId#${it.id.substringAfter("#")}")
        }
    )
}

fun looksLikeWarJson(text: String): Boolean {
    if (text.length > 200_000) return false
    return text.contains("\"members\"") && text.contains("player_name")
}
