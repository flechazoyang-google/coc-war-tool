package com.cocwar.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.BuildConfig
import com.cocwar.data.update.UpdateChecker
import com.cocwar.data.update.UpdateInfo
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.UpdateDialog
import kotlinx.coroutines.launch

/**
 * 设置-更新页：当前版本 + 检查更新（数据源为 GitHub Releases）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("更新", style = MaterialTheme.typography.titleMedium) },
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

            SectionTitle("版本")
            CocCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("当前版本", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(1.dp))
                            Text(BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        val result = UpdateChecker.check(context)
                        checking = false
                        result.fold(
                            onSuccess = { info ->
                                if (info != null) updateInfo = info
                                else Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "检查失败：${e.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                enabled = !checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = CocShape.field,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (checking) "正在检查…" else "检查更新", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(info = info, onDismiss = { updateInfo = null })
    }
}
