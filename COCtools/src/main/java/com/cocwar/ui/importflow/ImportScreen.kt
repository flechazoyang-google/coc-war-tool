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
import com.cocwar.data.ocr.OcrValidation
import com.cocwar.data.model.ParseResult
import com.cocwar.data.model.ParsedEvent
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SegmentedTabs
import com.cocwar.ui.theme.cocColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ImportViewModel = warViewModel { ImportViewModel(it) }
    val scope = rememberCoroutineScope()

    var csvText by remember { mutableStateOf("") }
    var parsedEvent by remember { mutableStateOf<ParsedEvent?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var eventType by remember { mutableStateOf(EVENT_TYPE_WAR) }
    // CSV 数值校验警告（粘贴/导入的 CSV 同样可能带异常数值）
    var csvWarning by remember { mutableStateOf<String?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }
    // CSV 提示词按当前花名册动态生成（注入名单 + 形近名提醒），初值为静态兜底
    var csvPromptText by remember { mutableStateOf(CopyPrompts.CSV_PROMPT) }

    // 花名册变化时重新生成提示词（名单注入 + 形近名提醒随之更新）
    LaunchedEffect(eventType) {
        csvPromptText = viewModel.buildPromptText(eventType)
    }

    /** CSV 解析（B2，RULES §4.15）：按当前选择的类型填充槽位。 */
    fun doParseCsv(text: String) {
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val slotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2
                viewModel.parseCsv(text, eventType, slotCount)
            }
            when (result) {
                is ParseResult.Success -> { parsedEvent = result.data; errorMsg = null }
                is ParseResult.Error -> { parsedEvent = null; errorMsg = result.message }
            }
        }
        // 数值合法性提示：外部软件识别/手写的 CSV 同样可能有越界值（星数>6、摧毁率>100）
        val issues = OcrValidation.validate(text)
        csvWarning = if (issues.isEmpty()) null
        else "有 ${issues.size} 处可疑数值，请核对：\n" +
            issues.joinToString("\n") { "  ${it.name}（${it.field}=${it.value}）" }
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
                Text(
                    "格式：成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率（联赛只有 1 列进攻）。\n" +
                        "摧毁率可带 %，缺失列按 0；-1 表示看不清，导入时会提示确认；首行若为表头会自动跳过。\n" +
                        "截图请用外部 AI（豆包等）识别：点右上⧉复制本页提示词，识别结果粘贴回这里。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

            csvWarning?.let {
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
                val prompt = csvPromptText

                AlertDialog(
                    onDismissRequest = { showPromptDialog = false },
                    title = { Text("AI 识别提示词（CSV）") },
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("CSV 识别提示词", prompt))
                                Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
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
            onSaved = { eventId ->
                parsedEvent = null
                onSaved()
            },
            onDismiss = { parsedEvent = null }
        )
    }
}
