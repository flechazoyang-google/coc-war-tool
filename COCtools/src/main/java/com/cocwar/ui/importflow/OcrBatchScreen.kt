package com.cocwar.ui.importflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocwar.data.ocr.ScreenshotGrouper
import com.cocwar.service.OcrBatchService
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.theme.cocColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 批量识图：列出按「一次截图会话」分组的截图，选择一组触发后台识图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrBatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var groups by remember { mutableStateOf<List<ScreenshotGrouper.ScreenshotGroup>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groups = ScreenshotGrouper.group(ScreenshotGrouper.load(context))
        loaded = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("批量识图", style = MaterialTheme.typography.titleMedium) },
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
        when {
            !loaded -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            groups.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "尚无截图",
                    body = "先用悬浮球对部落战报截图，一次滚动截取的多张图会自动归为一组，再回来批量识图。"
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    OcrGroupCard(
                        group = group,
                        onStart = {
                            OcrBatchService.start(context, group.items.map { it.path })
                            Toast.makeText(
                                context,
                                "已开始后台识图（" + group.items.size + " 屏）",
                                Toast.LENGTH_SHORT
                            ).show()
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrGroupCard(group: ScreenshotGrouper.ScreenshotGroup, onStart: () -> Unit) {
    CocCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val firstPath = group.items.firstOrNull()?.path
            val thumb = remember(firstPath) { firstPath?.let { decodeThumb(it) } }
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CocShape.panel),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(CocShape.panel)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("截图批次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    group.items.size.toString() + " 张 · " + formatTime(group.startMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onStart, shape = CocShape.field) {
                Text("开始识图", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 轻量缩略图解码（inSampleSize=8）；失败返回 null。 */
private fun decodeThumb(path: String): Bitmap? = runCatching {
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
}.getOrNull()

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
