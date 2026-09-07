package com.cocwar.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.ocr.OcrPrompts
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SegmentedTabs
import com.cocwar.ui.importflow.ImportViewModel

/**
 * 设置-识别提示词页。
 *
 * App 本身不调用任何 AI 接口：这里按当前花名册生成识别提示词，用户复制后
 * 到豆包等外部软件识别战报截图，再把结果 CSV 粘贴回导入页。
 *
 * 提示词注入在册成员名单与形近名提醒（[OcrPrompts.buildPrompt]），
 * 能在源头把名字错字纠正为花名册写法，比通用提示词更准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ImportViewModel = warViewModel { ImportViewModel(it) }
    var typeIndex by remember { mutableStateOf(0) }
    var prompt by remember { mutableStateOf(OcrPrompts.DEFAULT) }

    // 花名册/赛事类型变化时重建提示词（注入名单 + 形近名提醒）
    LaunchedEffect(typeIndex) {
        val eventType = if (typeIndex == 1) EVENT_TYPE_LEAGUE else EVENT_TYPE_WAR
        prompt = viewModel.buildPromptText(eventType)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("识别提示词", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { copyPrompt(context, prompt) }) {
                        Icon(Icons.Filled.ContentCopy, "复制提示词", tint = MaterialTheme.colorScheme.primary)
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "本 App 不内置 AI 识别。复制下方提示词，连同战报截图一起发给豆包等外部 AI，" +
                    "把返回的 CSV 粘贴回导入页即可。提示词已注入你的在册成员名单，" +
                    "能自动把名字错字纠正为花名册写法。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SegmentedTabs(
                options = listOf("部落战（两次进攻）", "联赛（一次进攻）"),
                selectedIndex = typeIndex,
                onSelect = { typeIndex = it }
            )

            CocCard(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { copyPrompt(context, prompt) },
                modifier = Modifier.fillMaxWidth(),
                shape = CocShape.field
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("复制提示词", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun copyPrompt(context: Context, prompt: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "复制失败：无法访问剪贴板", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("战报识别提示词", prompt))
    Toast.makeText(context, "提示词已复制，去外部 AI 粘贴使用", Toast.LENGTH_SHORT).show()
}
