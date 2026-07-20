package com.cocwar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.cocwar.data.ai.AI_PROVIDERS
import com.cocwar.data.ai.AiConfig
import com.cocwar.data.ai.AiConfigStore
import com.cocwar.data.ai.AiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedConfig = remember { AiConfigStore.load(context) }
    var providerId by remember { mutableStateOf(savedConfig.providerId) }
    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var model by remember { mutableStateOf(savedConfig.model) }
    var apiKey by remember { mutableStateOf(savedConfig.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var providerExpanded by remember { mutableStateOf(false) }

    val currentProvider = AI_PROVIDERS.find { it.id == providerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 识别设置") },
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
            // 提供商选择
            Text("模型提供商", style = MaterialTheme.typography.titleSmall)
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it }
            ) {
                OutlinedTextField(
                    value = currentProvider?.name ?: "请选择",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    AI_PROVIDERS.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                providerId = p.id
                                if (p.id != "custom") {
                                    baseUrl = p.baseUrl
                                    model = p.defaultModel
                                }
                                providerExpanded = false
                            },
                            leadingIcon = {
                                if (p.id == providerId) Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
            if (currentProvider != null && currentProvider.description.isNotBlank()) {
                Text(currentProvider.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API 地址 (Base URL)") },
                placeholder = { Text("https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // 模型名称
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称") },
                placeholder = { Text("gpt-4o / qwen-vl-max / glm-4v-plus") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "隐藏" else "显示"
                        )
                    }
                }
            )

            // 按钮行
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        val config = AiConfig(providerId, baseUrl.trim(), model.trim(), apiKey.trim())
                        scope.launch {
                            val result = AiService.testConnection(config)
                            testResult = result.getOrElse { it.message ?: "未知错误" }
                            testing = false
                        }
                    },
                    enabled = !testing && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                Button(
                    onClick = {
                        val config = AiConfig(providerId, baseUrl.trim(), model.trim(), apiKey.trim())
                        AiConfigStore.save(context, config)
                        Toast.makeText(context, "AI 配置已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    enabled = baseUrl.isNotBlank() && apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }

            // 测试结果
            testResult?.let { msg ->
                val isSuccess = msg.contains("✅") || msg.contains("成功")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
