package com.cocwar.ui.importflow

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import java.util.Calendar
import java.util.TimeZone
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.ocr.OcrClient
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.ocr.OcrValidation
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SegmentedTabs
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.util.ImageCompress
import kotlinx.coroutines.launch
import com.cocwar.ui.util.parseEventRoundFromName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenBatchOcr: () -> Unit = {},
    pendingImportId: String? = null
) {
    val context = LocalContext.current
    val ocrConfig = remember { OcrConfig(context) }
    val viewModel: ImportViewModel = warViewModel { ImportViewModel(it, ocrConfig) }
    val scope = rememberCoroutineScope()

    var jsonText by remember { mutableStateOf("") }
    var csvText by remember { mutableStateOf("") }
    var parsedEvent by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var eventType by remember { mutableStateOf(EVENT_TYPE_WAR) }
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var matchStates by remember { mutableStateOf<List<MemberMatchState>>(emptyList()) }
    var roster by remember { mutableStateOf<List<String>>(emptyList()) }
    // 数据来源：0=JSON，1=CSV（B2）
    var sourceMode by remember { mutableStateOf(0) }
    // 截图识别：进行中 / 识别数值警告
    var recognizing by remember { mutableStateOf(false) }
    var ocrWarning by remember { mutableStateOf<String?>(null) }
    // 战报日期：影响 SAABBCC 名称的年月段与 createdAt 时间戳
    val todayMillis = remember {
        Calendar.getInstance(TimeZone.getDefault()).run {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
    var selectedDateMillis by remember { mutableStateOf<Long?>(todayMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var adjustedParsed by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }

    LaunchedEffect(parsedEvent) {
        parsedEvent?.let { parsed ->
            eventType = parsed.event.eventType
            val dateMs = selectedDateMillis ?: todayMillis
            name = viewModel.generateNameForDate(parsed.event.eventType, parsed.event.eventRound, dateMs)
            nameError = false
            val loadedRoster = viewModel.loadRoster()
            roster = loadedRoster
            matchStates = buildMatchStates(parsed, loadedRoster)
            adjustedParsed = adjustParsedDate(parsed, dateMs)
        }
    }

    fun onDateSelected(dateMs: Long) {
        selectedDateMillis = dateMs
        adjustedParsed = parsedEvent?.let { adjustParsedDate(it, dateMs) }
        scope.launch {
            name = viewModel.generateNameForDate(eventType, 0, dateMs)
        }
    }

    fun doParse(text: String) {
        // 大 JSON 解析放到 IO 线程，避免阻塞主线程
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.parse(text)
            }
            when (result) {
                is WarJsonParser.ParseResult.Success -> { parsedEvent = result.data; errorMsg = null }
                is WarJsonParser.ParseResult.Error -> { parsedEvent = null; errorMsg = result.message }
            }
        }
    }

    /** CSV 解析（B2，RULES §4.15）：按当前选择的类型填充槽位。 */
    fun doParseCsv(text: String) {
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val slotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2
                viewModel.parseCsv(text, eventType, slotCount)
            }
            when (result) {
                is WarJsonParser.ParseResult.Success -> { parsedEvent = result.data; errorMsg = null }
                is WarJsonParser.ParseResult.Error -> { parsedEvent = null; errorMsg = result.message }
            }
        }
    }

    // 待确认识图草稿：进入时自动填充 CSV 并解析（复用正常 CSV 导入链路）
    LaunchedEffect(pendingImportId) {
        pendingImportId?.let { id ->
            val pending = viewModel.loadPendingImport(id)
            if (pending != null) {
                sourceMode = 1
                csvText = pending.csvText
                doParseCsv(pending.csvText)
            } else {
                errorMsg = "该待确认识图已不存在"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // 文件读取移到 IO 线程，避免主线程磁盘 IO 卡顿
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
                    }
                }
                    .onSuccess { jsonText = it; doParse(it) }
                    .onFailure { errorMsg = "读取文件失败：${it.message}" }
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
                    }
                }
                    .onSuccess { csvText = it; doParseCsv(it) }
                    .onFailure { errorMsg = "读取文件失败：${it.message}" }
            }
        }
    }

    /** 截图识别：压缩图片 → 调模型 → CSV 填入并自动解析，复用现有导入链路。 */
    val ocrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                recognizing = true
                errorMsg = null
                ocrWarning = null
                try {
                    val base64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ImageCompress.readAndCompressToBase64(context, it)
                    }
                    if (base64 == null) {
                        errorMsg = "读取图片失败，请换一张截图重试"
                        return@launch
                    }
                    val csv = viewModel.recognize(base64)
                    if (csv.isBlank()) {
                        errorMsg = "识别结果为空（图片中未识别到战报数据）"
                        return@launch
                    }
                    csvText = csv
                    val issues = OcrValidation.validate(csv)
                    ocrWarning = if (issues.isEmpty()) null
                    else "识别结果有 ${issues.size} 处可疑数值，请核对：\n" +
                        issues.joinToString("\n") { "  ${it.name}（${it.field}=${it.value}）" }
                    doParseCsv(csv)
                } catch (e: OcrClient.OcrException.NotConfigured) {
                    errorMsg = e.message
                } catch (e: OcrClient.OcrException.Timeout) {
                    errorMsg = "识别超时（120 秒），请重试"
                } catch (e: OcrClient.OcrException.Network) {
                    errorMsg = "网络错误：${e.detail}，请检查网络后重试"
                } catch (e: OcrClient.OcrException.ApiError) {
                    errorMsg = "${e.message}"
                } catch (e: OcrClient.OcrException.BadResponse) {
                    errorMsg = "识别响应无法解析，请重试"
                } catch (e: Exception) {
                    errorMsg = "识别失败：${e.message}"
                } finally {
                    recognizing = false
                }
            }
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
            SegmentedTabs(
                options = listOf("JSON", "CSV"),
                selectedIndex = sourceMode,
                onSelect = {
                    // 切换来源时清空另一面板遗留的解析结果与错误，防止误存
                    if (sourceMode != it) {
                        sourceMode = it
                        parsedEvent = null
                        errorMsg = null
                        matchStates = emptyList()
                        nameError = false
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))

            if (sourceMode == 0) {
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
            } else {
                SectionTitle("CSV 数据")
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("粘贴 CSV 数据") },
                    placeholder = { Text("成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100%,100%") },
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
                        onClick = { csvPicker.launch("text/csv") },
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
                    OutlinedButton(
                        onClick = { ocrPicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !recognizing,
                        shape = CocShape.field,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.cocColors.hairline
                        )
                    ) {
                        if (recognizing) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.ImageSearch, null, Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (recognizing) "识别中…" else "单屏识图")
                    }
                    Button(
                        onClick = { doParseCsv(csvText) },
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
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onOpenBatchOcr,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CocShape.field,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
                ) {
                    Text("批量识图（多屏截图）", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "格式：成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率（联赛只有 1 列进攻）。\n" +
                        "摧毁率可带 %，缺失列按 0；首行若为表头会自动跳过。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CopyPromptRow()

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

            ocrWarning?.let {
                Spacer(Modifier.height(12.dp))
                CocCard(Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        color = MaterialTheme.cocColors.star,
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
                            val dateMs = selectedDateMillis ?: todayMillis
                            scope.launch {
                                name = viewModel.generateNameForDate(newType, 0, dateMs)
                            }
                            adjustedParsed = parsedEvent?.let { adjustParsedDate(it, dateMs) }
                        })
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CocShape.field,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
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
                // 导入 diff 预览（RULES §4.12）：总数 / 已在名单 / 名单外新成员
                val diff = remember(matchStates, roster) { buildDiffSummary(matchStates, roster) }
                CocCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "共 ${diff.total} 名成员 · 已在名单 ${diff.inRoster} · 新成员 ${diff.newNames}（保存时自动加入花名册）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                        val base = adjustedParsed ?: parsed
                        // 应用匹配结果到 members
                        val editedMembers = base.members.mapIndexed { i, m ->
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
                        val newEventId = if (eventType != base.event.eventType) {
                            "${eventType}_${base.event.createdAt}_${System.nanoTime()}"
                        } else base.event.eventId
                        val adjusted = base.copy(
                            event = base.event.copy(
                                eventId = newEventId,
                                eventName = name.trim(),
                                eventType = eventType,
                                eventRound = parseEventRoundFromName(name.trim())
                            ),
                            // 同步更新成员的 eventId 外键
                            members = finalMembers.map { it.copy(eventId = newEventId, id = newEventId + "#" + it.id.substringAfter("#")) }
                        )
                        // save 内部串行完成「新成员入名单 → 导入事件 → 删除待确认草稿」，避免页面退出后名单丢失
                        viewModel.save(adjusted, pendingImportId) { onSaved() }
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

            if (showDatePicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDateMillis ?: todayMillis
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { onDateSelected(it) }
                            showDatePicker = false
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                            Text("取消")
                        }
                    }
                ) {
                    DatePicker(state = pickerState)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CopyPromptRow() {
    val context = LocalContext.current
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager

    Spacer(Modifier.height(20.dp))
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.cocColors.hairline)
    Spacer(Modifier.height(14.dp))
    SectionTitle("AI 识别提示词")
    Text(
        "复制提示词后粘贴到任意 AI 工具（ChatGPT / 通义千问 / 豆包等），再附上战报截图即可转换数据",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // JSON 提示词
        OutlinedButton(
            onClick = {
                clipboard.setPrimaryClip(ClipData.newPlainText("JSON 识别提示词", CopyPrompts.JSON_PROMPT))
                Toast.makeText(context, "JSON 提示词已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(72.dp),
            shape = CocShape.field,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制 JSON 提示词", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "截图 → JSON 格式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // CSV 提示词
        OutlinedButton(
            onClick = {
                clipboard.setPrimaryClip(ClipData.newPlainText("CSV 识别提示词", CopyPrompts.CSV_PROMPT))
                Toast.makeText(context, "CSV 提示词已复制", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(72.dp),
            shape = CocShape.field,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制 CSV 提示词", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "截图 → CSV 格式",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val WEEKDAY_LABELS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

private fun formatWarDate(dateMillis: Long): String {
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
