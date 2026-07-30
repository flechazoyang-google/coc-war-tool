package com.cocwar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import com.cocwar.ui.importflow.MatchOption
import com.cocwar.ui.importflow.MemberMatchPreview
import com.cocwar.ui.importflow.MemberMatchState
import com.cocwar.ui.importflow.WarNameField
import com.cocwar.ui.importflow.WarPreviewCard
import com.cocwar.ui.importflow.WarTypeRoundSection
import com.cocwar.ui.importflow.buildMatchStates
import kotlinx.coroutines.launch

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

    LaunchedEffect(parsed) {
        name = repo.generateEventName(parsed.event.eventType, parsed.event.eventRound)
        val loadedRoster = repo.getRoster()
        roster = loadedRoster
        matchStates = buildMatchStates(parsed, loadedRoster)
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
                val adjusted = parsed.copy(
                    event = parsed.event.copy(eventName = name.trim(), eventType = eventType, eventRound = 0),
                    members = editedMembers
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
                    if (name.length >= 1) { val prefix = if (it == EVENT_TYPE_LEAGUE) '1' else '0'; name = prefix + name.substring(1) }
                })
            }
        }
    )
}

fun looksLikeWarJson(text: String): Boolean {
    if (text.length > 200_000) return false
    return text.contains("\"members\"") && text.contains("player_name")
}
