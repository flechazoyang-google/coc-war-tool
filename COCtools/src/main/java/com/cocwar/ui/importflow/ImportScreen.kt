package com.cocwar.ui.importflow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors
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
    var roster by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(parsedEvent) {
        parsedEvent?.let { parsed ->
            eventType = parsed.event.eventType
            name = viewModel.generateName(parsed.event.eventType, parsed.event.eventRound)
            nameError = false
            val loadedRoster = viewModel.loadRoster()
            roster = loadedRoster
            matchStates = buildMatchStates(parsed, loadedRoster)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("导入战报", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("数据来源")
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text("粘贴 JSON 数据") },
                placeholder = { Text("将部落战 JSON 粘贴到这里…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                shape = CocShape.field,
                singleLine = false,
                isError = errorMsg != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                    cursorColor = MaterialTheme.cocColors.accent
                )
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch("application/json") },
                    modifier = Modifier.weight(1f),
                    shape = CocShape.field,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.cocColors.hairline
                    )
                ) {
                    Icon(Icons.Filled.FileOpen, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }
                Button(
                    onClick = { doParse(jsonText) },
                    modifier = Modifier.weight(1f),
                    shape = CocShape.field,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("解析并预览", fontWeight = FontWeight.SemiBold)
                }
            }

            errorMsg?.let {
                Spacer(Modifier.height(12.dp))
                CocCard(Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        color = MaterialTheme.cocColors.danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            parsedEvent?.let { parsed ->
                SectionTitle("战报信息")
                CocCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        WarNameField(
                            name = name,
                            onNameChange = { name = it; nameError = false },
                            isError = nameError,
                            errorText = "请填写战报名称"
                        )
                        WarTypeRoundSection(eventType = eventType, onTypeChange = { newType ->
                            eventType = newType
                            // 切换类型时重新生成完整名称（前缀 + 年月 + 自增序号）
                            scope.launch {
                                name = viewModel.generateName(newType, 0)
                            }
                        })
                    }
                }

                SectionTitle("数据预览")
                WarPreviewCard(parsed = parsed)

                SectionTitle("成员匹配")
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

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (name.trim().isBlank()) { nameError = true; return@Button }
                        // 应用匹配结果到 members
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
                        // 新成员加入名单
                        scope.launch {
                            val roster = viewModel.loadRoster()
                            val newNames = editedMembers.map { it.playerName }.filter { it !in roster }.distinct()
                            if (newNames.isNotEmpty()) viewModel.addToRoster(newNames)
                        }
                        viewModel.save(adjusted) { onSaved() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = CocShape.field,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.Save, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存到本地", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
