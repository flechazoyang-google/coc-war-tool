package com.cocwar.ui.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.window.Dialog
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.util.parseEventRoundFromName
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewDialog(
    parsed: WarJsonParser.ParsedEvent,
    viewModel: ImportViewModel,
    pendingImportId: String? = null,
    onSaved: (String) -> Unit,
    onDismiss: () -> Unit
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
    // 入册确认闸：疑似同名冲突 + 已构建的最终数据，待用户确认后保存
    var conflictConfirm by remember { mutableStateOf<Pair<List<RosterConflict>, WarJsonParser.ParsedEvent>?>(null) }

    /** 构建最终保存数据：应用成员匹配选择（使用建议/名单选择/新成员改名）、补齐进攻槽位、写入名称/类型/日期。 */
    fun buildFinalParsed(): WarJsonParser.ParsedEvent {
        val editedMembers = parsed.members.mapIndexed { i, m ->
            val state = matchStates.getOrNull(i)
            if (state != null) {
                val finalName = when (state.matchOption) {
                    MatchOption.USE_SUGGESTION -> state.suggestion ?: state.editedName
                    MatchOption.PICK_FROM_ROSTER -> state.selectedRosterName ?: state.editedName
                    MatchOption.AS_NEW_MEMBER -> state.editedName
                }.trim().ifBlank { m.playerName }
                if (finalName != m.playerName) m.copy(playerName = finalName) else m
            } else m
        }
        val finalSlotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2
        val finalMembers = editedMembers.map { m ->
            val existing = m.attacks.filter { it.destructionPercentage > 0 }
            val padded = existing + (1..finalSlotCount)
                .filterNot { order -> existing.any { it.attackOrder == order } }
                .map { Attack(attackOrder = it, destructionPercentage = 0) }
            m.copy(attacks = padded)
        }
        val newEventId = if (eventType != adjustedParsed.event.eventType) {
            "${eventType}_${adjustedParsed.event.createdAt}_${System.nanoTime()}"
        } else adjustedParsed.event.eventId
        return adjustedParsed.copy(
            event = adjustedParsed.event.copy(
                eventId = newEventId,
                eventName = name.trim(),
                eventType = eventType,
                eventRound = parseEventRoundFromName(name.trim())
            ),
            members = finalMembers.map {
                it.copy(eventId = newEventId, id = newEventId + "#" + it.id.substringAfter("#"))
            }
        )
    }

    LaunchedEffect(parsed) {
        val dateMs = selectedDateMillis ?: todayMillis
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        name = viewModel.generateNameForDate(parsed.event.eventType, parsed.event.eventRound, dateMs)
        roster = viewModel.loadRoster()
        matchStates = buildMatchStates(parsed, roster)
        adjustedParsed = adjustParsedDate(parsed, dateMs)
    }

    fun onDateSelected(dateMs: Long) {
        selectedDateMillis = dateMs
        adjustedParsed = adjustParsedDate(parsed, dateMs)
        scope.launch {
            name = viewModel.generateNameForDate(eventType, 0, dateMs)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("导入预览") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, "关闭")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                SectionTitle("战报信息")
                CocCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WarNameField(
                            name = name,
                            onNameChange = { name = it; nameError = false },
                            isError = nameError,
                            errorText = "请填写战报名称"
                        )
                        WarTypeRoundSection(eventType = eventType, onTypeChange = {
                            eventType = it
                            val dateMs = selectedDateMillis ?: todayMillis
                            scope.launch {
                                name = viewModel.generateNameForDate(it, parseEventRoundFromName(name), dateMs)
                            }
                            adjustedParsed = adjustParsedDate(parsed, dateMs)
                        })
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DateRange, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(formatWarDate(selectedDateMillis ?: todayMillis), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                SectionTitle("数据预览")
                WarPreviewCard(parsed = parsed)

                SectionTitle("成员匹配")
                val diff = buildDiffSummary(matchStates, roster)
                CocCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "共 ${diff.total} 名成员 . 已在名单 ${diff.inRoster} . 新成员 ${diff.newNames}（保存时自动加入花名册）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MemberMatchPreview(
                            matchStates = matchStates,
                            roster = roster,
                            onNameEdit = { i, n -> matchStates = matchStates.toMutableList().also { it[i] = it[i].copy(editedName = n) } },
                            onOptionChange = { i, opt ->
                                matchStates = matchStates.toMutableList().also {
                                    it[i] = it[i].copy(matchOption = opt, selectedRosterName = null)
                                }
                            },
                            onRosterPick = { i, picked ->
                                matchStates = matchStates.toMutableList().also {
                                    it[i] = it[i].copy(selectedRosterName = picked)
                                }
                            },
                            onToggleDropdown = { i ->
                                matchStates = matchStates.toMutableList().also {
                                    it[i] = it[i].copy(dropdownExpanded = !it[i].dropdownExpanded)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (name.trim().isBlank()) { nameError = true; return@Button }
                        val finalParsed = buildFinalParsed()
                        scope.launch {
                            // 入册确认闸：新名字与在册成员疑似同名时先人工确认，避免 OCR 错名污染花名册
                            val conflicts = viewModel.findRosterConflicts(
                                finalParsed.members.map { it.playerName }
                            )
                            if (conflicts.isEmpty()) {
                                viewModel.save(finalParsed, pendingImportId) { onSaved(finalParsed.event.eventId) }
                            } else {
                                conflictConfirm = conflicts to finalParsed
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Filled.Save, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存到本地", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // 入册确认闸弹窗：确认后按选择把错名并入在册成员（同名行由导入去重逻辑合并），再保存
    conflictConfirm?.let { (conflicts, finalParsed) ->
        RosterConflictDialog(
            conflicts = conflicts,
            onConfirm = { choices ->
                val merges = conflicts.filter { choices[it.newName] == true }
                    .associate { it.newName to it.suggestion }
                val resolved = if (merges.isEmpty()) finalParsed else finalParsed.copy(
                    members = finalParsed.members.map { m ->
                        merges[m.playerName]?.let { m.copy(playerName = it) } ?: m
                    }
                )
                conflictConfirm = null
                viewModel.save(resolved, pendingImportId) { onSaved(resolved.event.eventId) }
            },
            onDismiss = { conflictConfirm = null }
        )
    }

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
