package com.cocwar.ui.importflow

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
    // 数据来源：0=JSON，1=CSV（B2）
    var sourceMode by remember { mutableStateOf(0) }
    // 截图识别：进行中 / 识别数值警告
    var recognizing by remember { mutableStateOf(false) }
    var ocrWarning by remember { mutableStateOf<String?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showPromptDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, "AI 识别提示词")
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

            if (showPromptDialog) {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                val isJson = sourceMode == 0
                val prompt = if (isJson) CopyPrompts.JSON_PROMPT else CopyPrompts.CSV_PROMPT
                val formatLabel = if (isJson) "JSON" else "CSV"

                AlertDialog(
                    onDismissRequest = { showPromptDialog = false },
                    title = { Text("AI 识别提示词（$formatLabel）") },
                    text = {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CocShape.field
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("$formatLabel 识别提示词", prompt))
                                Toast.makeText(context, "$formatLabel 提示词已复制", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, "复制提示词",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { showPromptDialog = false }) {
                                Text("关闭")
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    parsedEvent?.let { parsed ->
        ImportPreviewDialog(
            parsed = parsed,
            viewModel = viewModel,
            pendingImportId = pendingImportId,
            onSaved = { eventId ->
                parsedEvent = null
                onSaved()
            },
            onDismiss = { parsedEvent = null }
        )
    }
}
