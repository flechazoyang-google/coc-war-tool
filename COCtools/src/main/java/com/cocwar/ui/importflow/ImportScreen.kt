package com.cocwar.ui.importflow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val viewModel: ImportViewModel = warViewModel { ImportViewModel(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var jsonText by remember { mutableStateOf("") }
    var parsedEvent by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var eventType by remember { mutableStateOf(EVENT_TYPE_WAR) }
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var matchStates by remember { mutableStateOf<List<MemberMatchState>>(emptyList()) }

    LaunchedEffect(parsedEvent) {
        parsedEvent?.let { parsed ->
            eventType = parsed.event.eventType
            name = viewModel.generateName(parsed.event.eventType, parsed.event.eventRound)
            nameError = false
            val roster = viewModel.loadRoster()
            matchStates = buildMatchStates(parsed, roster)
        }
    }

    fun doParse(text: String) {
        when (val r = viewModel.parse(text)) {
            is WarJsonParser.ParseResult.Success -> { parsedEvent = r.data; errorMsg = null }
            is WarJsonParser.ParseResult.Error -> { parsedEvent = null; errorMsg = r.message }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: "" }
                .onSuccess { jsonText = it; doParse(it) }
                .onFailure { errorMsg = "读取文件失败：${it.message}" }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("导入战报") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = jsonText, onValueChange = { jsonText = it }, label = { Text("粘贴 JSON 数据") },
                placeholder = { Text("将部落战 JSON 粘贴到这里…") }, modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(12.dp), singleLine = false, isError = errorMsg != null)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { picker.launch("application/json") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("选择文件") }
                Button(onClick = { doParse(jsonText) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("解析并预览") }
            }

            errorMsg?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp)) }
            }

            parsedEvent?.let { parsed ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WarNameField(name = name, onNameChange = { name = it; nameError = false }, isError = nameError, errorText = "请填写战报名称")
                        WarPreviewCard(parsed = parsed)
                        MemberMatchPreview(
                            matchStates = matchStates,
                            onNameEdit = { i, n -> matchStates = matchStates.toMutableList().also { it[i] = it[i].copy(editedName = n) } },
                            onToggleSuggestion = { i, v -> matchStates = matchStates.toMutableList().also { it[i] = it[i].copy(acceptSuggestion = v) } }
                        )
                        WarTypeRoundSection(eventType = eventType, onTypeChange = {
                            eventType = it
                            if (name.length >= 1) { val prefix = if (it == EVENT_TYPE_LEAGUE) '1' else '0'; name = prefix + name.substring(1) }
                        })
                        Button(onClick = {
                            if (name.trim().isBlank()) { nameError = true; return@Button }
                            // 应用匹配结果到 members
                            val editedMembers = parsed.members.mapIndexed { i, m ->
                                val state = matchStates.getOrNull(i)
                                if (state != null) {
                                    val finalName = if (state.acceptSuggestion && state.suggestion != null) state.suggestion else state.editedName
                                    if (finalName != m.playerName) m.copy(playerName = finalName) else m
                                } else m
                            }
                            val adjusted = parsed.copy(
                                event = parsed.event.copy(eventName = name.trim(), eventType = eventType, eventRound = 0),
                                members = editedMembers
                            )
                            // 新成员加入名单
                            scope.launch {
                                val roster = viewModel.loadRoster()
                                val newNames = editedMembers.map { it.playerName }.filter { it !in roster }.distinct()
                                if (newNames.isNotEmpty()) viewModel.addToRoster(newNames)
                            }
                            viewModel.save(adjusted) { onSaved() }
                        }, modifier = Modifier.fillMaxWidth()) { Text("保存到本地") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
