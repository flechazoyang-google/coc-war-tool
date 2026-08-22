package com.cocwar.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.ocr.OcrProviders
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.InfoRow
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors

/**
 * 设置-识图设置页：服务商下拉选择 + API Key + 从 API 获取模型列表（支持搜索筛选）+ 测试连接。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CocWarApplication
    val config = remember { OcrConfig(context) }
    val viewModel: OcrSettingsViewModel = warViewModel { OcrSettingsViewModel(app.repository, config) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("识图设置", style = MaterialTheme.typography.titleMedium) },
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
            Spacer(Modifier.height(4.dp))

            // ── 服务商（下拉框） ──
            SectionTitle("服务商")
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { providerExpanded = true },
                            shape = CocShape.field,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    OcrProviders.ALL.getOrNull(state.providerIndex)?.name ?: "选择服务商",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            OcrProviders.ALL.forEachIndexed { index, provider ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(provider.name)
                                            if (index == state.providerIndex) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    null,
                                                    tint = MaterialTheme.cocColors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.onProviderSelect(index)
                                        providerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 服务商 Key 配置状态指示
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OcrProviders.ALL.filter { it.id != "custom" }.forEach { preset ->
                            val configured = state.providerKeys[preset.id]?.isNotBlank() == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (configured) MaterialTheme.cocColors.accent
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    preset.name.takeWhile { it != '（' && it != '(' },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── API Key ──
            SectionTitle("API Key")
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val providerLabel = OcrProviders.ALL.getOrNull(state.providerIndex)?.keyLabel ?: "API Key"
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = viewModel::onApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(providerLabel) },
                        placeholder = {
                            Text(
                                if (state.isConfigured) "已配置，留空保持不变" else "未配置"
                            )
                        },
                        singleLine = true,
                        shape = CocShape.field,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    if (showKey) "隐藏" else "显示"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                            cursorColor = MaterialTheme.cocColors.accent
                        )
                    )
                    if (state.maskedKey.isNotBlank()) {
                        InfoRow(label = "已保存", value = state.maskedKey)
                    } else {
                        InfoRow(label = "状态", value = "未配置")
                    }
                }
            }

            if (!state.isSecureAvailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "设备加密存储不可用（AndroidKeyStore 异常），API Key 将无法安全保存，请勿继续。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 模型 ──
            SectionTitle("模型")
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // 获取模型按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = viewModel::fetchModels,
                            enabled = !state.isLoadingModels,
                            modifier = Modifier.weight(1f),
                            shape = CocShape.field,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.cocColors.accent
                            )
                        ) {
                            if (state.isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.isLoadingModels) "获取中..." else "从 API 获取模型列表")
                        }
                    }

                    // 模型搜索框
                    if (state.fetchedModels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.modelFilter,
                            onValueChange = viewModel::onModelFilterChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索模型（前缀匹配）") },
                            singleLine = true,
                            shape = CocShape.field,
                            leadingIcon = {
                                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                                cursorColor = MaterialTheme.cocColors.accent
                            )
                        )
                    }

                    // 模型错误提示
                    if (state.modelsError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.modelsError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 模型下拉选择
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { if (state.fetchedModels.isNotEmpty()) modelExpanded = true },
                            shape = CocShape.field,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    state.model.ifBlank { "选择模型" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (state.model.isBlank())
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    "展开",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            val filtered = viewModel.filteredModels
                            if (filtered.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("无匹配模型", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { modelExpanded = false }
                                )
                            } else {
                                filtered.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(model, modifier = Modifier.weight(1f, fill = false))
                                                if (model == state.model) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        null,
                                                        tint = MaterialTheme.cocColors.accent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.onModelSelect(model)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 自定义模型输入（当搜索后想手动输入不在列表中的模型）
                    if (state.fetchedModels.isNotEmpty() && state.model.isNotBlank() && state.model !in state.fetchedModels) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前模型 \"${state.model}\" 不在列表中（可手动输入）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 手动输入模型的备选方案
                    if (state.fetchedModels.isEmpty() && state.modelsError == null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.model,
                            onValueChange = viewModel::onModelChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("模型名称") },
                            placeholder = { Text("手动输入模型名称") },
                            singleLine = true,
                            shape = CocShape.field,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                                cursorColor = MaterialTheme.cocColors.accent
                            )
                        )
                    }
                }
            }

            // ── 高级（仅自定义服务商） ──
            if (state.isCustomProvider) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("高级")
                CocCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = state.baseUrl,
                            onValueChange = viewModel::onBaseUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Base URL（OpenAI 兼容端点）") },
                            singleLine = true,
                            shape = CocShape.field,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                                cursorColor = MaterialTheme.cocColors.accent
                            )
                        )
                    }
                }
            } else {
                // 预设服务商：只读展示端点
                Spacer(Modifier.height(8.dp))
                InfoRow(label = "端点", value = state.baseUrl, showDivider = false)
            }

            Spacer(Modifier.height(16.dp))

            // ── 测试连接 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !state.isTesting,
                    modifier = Modifier.weight(1f),
                    shape = CocShape.field,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.cocColors.accent
                    )
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("测试中...")
                    } else {
                        Text("测试连接")
                    }
                }
            }

            // 测试结果
            when (val result = state.testResult) {
                is OcrSettingsViewModel.TestResult.Success -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = MaterialTheme.cocColors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "连接成功${if (result.response.isNotBlank()) "：${result.response}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.cocColors.accent
                        )
                    }
                }
                is OcrSettingsViewModel.TestResult.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Error,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "连接失败：${result.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                null -> {}
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val ok = viewModel.save()
                    Toast.makeText(
                        context,
                        if (ok) "已保存" else "保存失败：设备加密存储不可用",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CocShape.field,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "说明：识图功能在导入页「单屏识图 / 批量识图」入口使用，识别结果以 CSV 填入并复用现有导入校验链路。每个服务商的 API Key 独立保存，切换不丢失。API Key 经 AndroidKeyStore 加密存储，不会明文落盘。点击「从 API 获取模型列表」可拉取当前服务商支持的模型，支持搜索筛选。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
