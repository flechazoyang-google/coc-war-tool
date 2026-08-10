package com.cocwar.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.ToolsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页：工具页 → 设置（更新入口 / 清理缓存）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 缓存占用（更新下载残留的 APK 等），进入页面时计算一次，清理后归零
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        cacheSizeBytes = withContext(Dispatchers.IO) { computeDirSize(context.cacheDir) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
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
            SectionTitle("通用")
            CocCard(Modifier.fillMaxWidth()) {
                Column {
                    ToolsRow(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = "更新",
                        subtitle = "加入测试计划 · 检查更新",
                        onClick = onOpenUpdate
                    )
                    ToolsRow(
                        icon = Icons.Filled.DeleteSweep,
                        title = "清理缓存",
                        subtitle = if (cacheSizeBytes > 0) {
                            "当前占用 ${formatFileSize(cacheSizeBytes)}（更新残留 APK 等），点击清理"
                        } else {
                            "缓存正常"
                        },
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                }
                                cacheSizeBytes = 0
                                Toast.makeText(context, "缓存已清理", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun computeDirSize(dir: File): Long =
    dir.listFiles()?.sumOf { if (it.isDirectory) computeDirSize(it) else it.length() } ?: 0L

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
