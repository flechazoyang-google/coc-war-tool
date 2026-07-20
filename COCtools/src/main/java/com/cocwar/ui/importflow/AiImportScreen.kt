package com.cocwar.ui.importflow

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.cocwar.CocWarApplication
import com.cocwar.data.ai.AiConfigStore
import com.cocwar.data.ai.AiService
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.di.warViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImportScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val viewModel: ImportViewModel = warViewModel { ImportViewModel(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 加载截图列表
    val screenshotDir = remember { File(context.filesDir, "screenshots") }
    val screenshotFiles = remember {
        if (screenshotDir.exists()) screenshotDir.listFiles()?.filter { it.extension.lowercase() == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        else emptyList()
    }

    // 选择状态 — 默认全不选
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 识别状态
    var isRecognizing by remember { mutableStateOf(false) }
    var parsedEvent by remember { mutableStateOf<WarJsonParser.ParsedEvent?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 识别导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (screenshotFiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到截图文件\n请先使用截图悬浮窗截取游戏画面",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }

            // 截图预览区
            Text("选择截图（${selectedIndices.size}/${screenshotFiles.size}）",
                style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(screenshotFiles) { index, file ->
                    val isSelected = index in selectedIndices
                    val bitmap = remember(file) {
                        runCatching {
                            BitmapFactory.decodeFile(file.absolutePath)
                        }.getOrNull()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                selectedIndices = if (isSelected) selectedIndices - index
                                else selectedIndices + index
                            }
                            .padding(4.dp)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "截图 ${index + 1}",
                                modifier = Modifier.size(width = 100.dp, height = 140.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(Modifier.size(100.dp, 140.dp), contentAlignment = Alignment.Center) {
                                Text("加载失败", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("#${index + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 操作栏：全选 / 清除截图
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        selectedIndices = if (selectedIndices.size == screenshotFiles.size) emptySet()
                        else screenshotFiles.indices.toSet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (selectedIndices.size == screenshotFiles.size) "取消全选" else "全选")
                }
                Button(
                    onClick = {
                        val toDelete = screenshotFiles.filterIndexed { i, _ -> i in selectedIndices }
                        toDelete.forEach { it.delete() }
                        Toast.makeText(context, "已删除 ${toDelete.size} 张截图", Toast.LENGTH_SHORT).show()
                        selectedIndices = emptySet()
                    },
                    enabled = selectedIndices.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清除选中 (${selectedIndices.size})")
                }
            }

            // 识别按钮
            Button(
                onClick = {
                    val config = AiConfigStore.load(context)
                    if (!config.isConfigured) {
                        Toast.makeText(context, "请先在「AI 设置」中配置 API Key", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val selectedFiles = screenshotFiles.filterIndexed { i, _ -> i in selectedIndices }
                    if (selectedFiles.isEmpty()) {
                        Toast.makeText(context, "请至少选择一张截图", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isRecognizing = true
                    errorMsg = null
                    parsedEvent = null
                    scope.launch {
                        try {
                            val bitmaps = withContext(Dispatchers.IO) {
                                selectedFiles.mapNotNull { f ->
                                    runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
                                }
                            }
                            val result = AiService.recognizeScreenshots(bitmaps, config)
                            result.fold(
                                onSuccess = { dto ->
                                    val parsed = WarJsonParser.fromDto(dto, isSample = false, createdAt = System.currentTimeMillis())
                                    parsedEvent = parsed
                                    name = viewModel.generateName(parsed.event.eventType, parsed.event.eventRound)
                                    Toast.makeText(context, "识别完成：${parsed.members.size} 名成员", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { e ->
                                    errorMsg = e.message ?: "识别失败"
                                }
                            )
                        } catch (e: Exception) {
                            errorMsg = "识别异常：${e.message}"
                        } finally {
                            isRecognizing = false
                        }
                    }
                },
                enabled = !isRecognizing && selectedIndices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRecognizing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("AI 识别中…")
                } else {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("开始 AI 识别")
                }
            }

            // 错误信息
            errorMsg?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }

            // 识别结果预览
            parsedEvent?.let { parsed ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("共识别 ${parsed.members.size} 名成员 · ${parsed.event.clanTotalStars} 总星数",
                            style = MaterialTheme.typography.bodyMedium)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; nameError = false },
                            label = { Text("战报名称") },
                            isError = nameError,
                            supportingText = if (nameError) {{ Text("请填写战报名称") }} else null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // 成员列表简览
                        parsed.members.take(15).forEach { member ->
                            val attacksSummary = member.attacks.joinToString(" · ") { a ->
                                if (a.status == "used") "${a.destructionPercentage}%" else "未进攻"
                            }
                            Text(
                                "#${member.rank} ${member.playerName}  ★${member.totalStars}  $attacksSummary",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (parsed.members.size > 15) {
                            Text("…等 ${parsed.members.size} 人", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = {
                                if (name.trim().isBlank()) { nameError = true; return@Button }
                                val adjusted = parsed.copy(
                                    event = parsed.event.copy(eventName = name.trim(), eventType = EVENT_TYPE_WAR, eventRound = 0)
                                )
                                viewModel.save(adjusted) { onSaved() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存到本地")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
