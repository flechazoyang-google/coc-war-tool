package com.cocwar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import com.cocwar.ui.importflow.MatchOption
import com.cocwar.ui.util.parseEventRoundFromName
import com.cocwar.ui.importflow.MemberMatchPreview
import com.cocwar.ui.importflow.MemberMatchState
import com.cocwar.ui.importflow.WarNameField
import com.cocwar.ui.importflow.WarPreviewCard
import com.cocwar.ui.importflow.WarTypeRoundSection
import com.cocwar.ui.importflow.buildMatchStates
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardImportDialog(
    parsed: WarJsonParser.ParsedEvent, repo: WarRepository,
    onSaved: (String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf(parsed.event.eventType) }
    var nameError by remember { mutableStateOf(false) }
    var matchStates by remember { mutableStateOf<List<MemberMatchState>>(emptyList()) }
    var roster by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val todayMillis = remember {
        Calendar.getInstance(TimeZone.getDefault()).run {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
    var selectedDateMillis by remember { mutableStateOf<Long?>(todayMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var adjustedParsed by remember { mutableStateOf(parsed) }

    LaunchedEffect(parsed) {
        val dateMs = selectedDateMillis ?: todayMillis
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        name = repo.generateEventName(parsed.event.eventType, parsed.event.eventRound, cal)
        val loadedRoster = repo.getRoster()
        roster = loadedRoster
        matchStates = buildMatchStates(parsed, loadedRoster)
        adjustedParsed = adjustParsedDate(parsed, dateMs)
    }

    fun onDateSelected(dateMs: Long) {
        selectedDateMillis = dateMs
        adjustedParsed = adjustParsedDate(parsed, dateMs)
        scope.launch {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
            name = repo.generateEventName(eventType, 0, cal)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isBlank()) { nameError = true; return@TextButton }
                val editedMembers = parsed.members.mapIndexed { i, m ->
                    val state = matchStates.getOrNull(i)
                    if (state != null) {
                        val finalName = when (state.matchOption) {
                            MatchOption.USE_SUGGESTION -> state.suggestion ?: state.editedName
                            MatchOption.PICK_FROM_ROSTER -> state.selectedRosterName ?: state.editedName
                            MatchOption.AS_NEW_MEMBER -> state.editedName
                        }
                        if (finalName != m.playerName) m.copy(playerName = finalName) else m
                    } else m
                }
                // 类型切换后按最终类型重新填充进攻槽位（部落战2槽/联赛1槽）
                val finalSlotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2
                val finalMembers = editedMembers.map { m ->
                    val existing = m.attacks.filter { it.destructionPercentage > 0 }
                    val padded = existing + (1..finalSlotCount)
                        .filterNot { order -> existing.any { it.attackOrder == order } }
                        .map { com.cocwar.data.model.Attack(attackOrder = it, destructionPercentage = 0) }
                    m.copy(attacks = padded)
                }
                // 若最终 eventType 与解析时不同，重新生成 eventId（及成员外键），确保类型一致
                val newEventId = if (eventType != adjustedParsed.event.eventType) {
                    "${eventType}_${adjustedParsed.event.createdAt}_${System.nanoTime()}"
                } else adjustedParsed.event.eventId
                val adjusted = adjustedParsed.copy(
                    event = adjustedParsed.event.copy(
                        eventId = newEventId,
                        eventName = name.trim(),
                        eventType = eventType,
                        eventRound = parseEventRoundFromName(name.trim())
                    ),
                    // 同步更新成员的 eventId 外键
                    members = adjustedParsed.members.map { it.copy(eventId = newEventId, id = newEventId + "#" + it.id.substringAfter("#")) }
                )
                scope.launch {
                    val roster = repo.getRoster()
                    val newNames = editedMembers.map { it.playerName }.filter { it !in roster }.distinct()
                    if (newNames.isNotEmpty()) repo.addToRoster(newNames)
                    repo.importEvent(adjusted); onSaved(adjusted.event.eventId)
                }
            }) { Text("确认导入", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("检测到剪切板中的战报数据") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已自动识别剪切板里的部落战/联赛 JSON，请确认成员名称后导入。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WarNameField(name = name, onNameChange = { name = it; nameError = false }, isError = nameError, errorText = "请填写战报名称")
                WarPreviewCard(parsed = parsed)
                MemberMatchPreview(
                    matchStates = matchStates,
                    roster = roster,
                    onNameEdit = { i, n -> matchStates = matchStates.toMutableList().also { it[i] = it[i].copy(editedName = n) } },
                    onOptionChange = { i, opt ->
                        matchStates = matchStates.toMutableList().also {
                            it[i] = it[i].copy(matchOption = opt, selectedRosterName = null)
                        }
                    },
                    onRosterPick = { i, name ->
                        matchStates = matchStates.toMutableList().also {
                            it[i] = it[i].copy(selectedRosterName = name)
                        }
                    },
                    onToggleDropdown = { i ->
                        matchStates = matchStates.toMutableList().also {
                            it[i] = it[i].copy(dropdownExpanded = !it[i].dropdownExpanded)
                        }
                    }
                )
                WarTypeRoundSection(eventType = eventType, onTypeChange = {
                    eventType = it
                    val dateMs = selectedDateMillis ?: todayMillis
                    scope.launch {
                        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
                        name = repo.generateEventName(it, parseEventRoundFromName(name), cal)
                    }
                    adjustedParsed = adjustParsedDate(parsed, dateMs)
                })
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DateRange, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatClipboardWarDate(selectedDateMillis ?: todayMillis), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: todayMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val WEEKDAY_LABELS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

private fun formatClipboardWarDate(dateMillis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getDefault()).apply { this.timeInMillis = dateMillis }
    val weekday = WEEKDAY_LABELS[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 $weekday"
}

private fun adjustParsedDate(
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
