package com.cocwar.ui.sync

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.sync.SyncConfig
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CocWarApplication
    val config = rememberSyncConfig(context)
    val viewModel: SyncViewModel = warViewModel { SyncViewModel(app.repository, config) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("云端同步", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 连接配置
            SectionTitle("WEBDAV 连接配置")
            CocCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SyncTextField(
                        value = state.serverUrl,
                        onValueChange = { viewModel.onUrlChange(it) },
                        label = "服务器地址",
                        placeholder = "https://example.com/remote.php/dav/files/user"
                    )
                    SyncTextField(
                        value = state.username,
                        onValueChange = { viewModel.onUsernameChange(it) },
                        label = "用户名"
                    )
                    SyncTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = "密码",
                        isPassword = true
                    )
                    Button(
                        onClick = { viewModel.testConnection() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        enabled = !state.isWorking,
                        shape = CocShape.field,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (state.isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Filled.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text("测试连接", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 同步操作
            SectionTitle("同步操作")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.uploadBackup() },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    enabled = !state.isWorking && state.isConfigured,
                    shape = CocShape.field,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("上传备份", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { viewModel.downloadAndRestore() },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    enabled = !state.isWorking && state.isConfigured,
                    shape = CocShape.field,
                    border = BorderStroke(1.dp, MaterialTheme.cocColors.hairline)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("下载恢复")
                }
            }

            // 状态信息
            state.statusMessage?.let { message ->
                val isSuccess = message.startsWith("✓")
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CocShape.panel,
                    color = if (isSuccess) MaterialTheme.cocColors.accentSoft
                    else MaterialTheme.cocColors.dangerSoft,
                    border = BorderStroke(
                        1.dp,
                        if (isSuccess) MaterialTheme.cocColors.accent.copy(alpha = 0.3f)
                        else MaterialTheme.cocColors.danger.copy(alpha = 0.3f)
                    ),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.cocColors.accent
                            else MaterialTheme.cocColors.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSuccess) MaterialTheme.cocColors.accent
                            else MaterialTheme.cocColors.danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SyncTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = CocShape.field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = MaterialTheme.cocColors.hairline,
            cursorColor = MaterialTheme.cocColors.accent
        )
    )
}

/** 在 Composable 中获取 SyncConfig 单例。 */
@Composable
private fun rememberSyncConfig(context: android.content.Context): SyncConfig {
    return androidx.compose.runtime.remember { SyncConfig(context) }
}
