package com.cocwar.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScreenshotItem(
    val id: Long,
    val path: String,
    val dateAdded: Long
)

@Composable
fun ScreenshotGalleryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val screenshots = remember { mutableStateListOf<ScreenshotItem>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loadScreenshots(context, screenshots)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("截图列表") },
        text = {
            if (loading) {
                Text("加载中…")
            } else if (screenshots.isEmpty()) {
                Text("还没有截图\n\n使用悬浮球在游戏中进行截图",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    Text("共 ${screenshots.size} 张截图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(400.dp)
                    ) {
                        items(screenshots.toList(), key = { it.id }) { item ->
                            ScreenshotThumb(
                                item = item,
                                onDelete = {
                                    deleteScreenshot(context, item)
                                    screenshots.remove(item)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = {
                screenshots.forEach { deleteScreenshot(context, it) }
                screenshots.clear()
                Toast.makeText(context, "已清理全部截图", Toast.LENGTH_SHORT).show()
            }) { Text("清理全部") }
        }
    )
}

@Composable
private fun ScreenshotThumb(item: ScreenshotItem, onDelete: () -> Unit) {
    // 异步解码：避免 LazyVerticalGrid 滚动时在 Compose 主线程做磁盘 IO + 位图解码
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(item.path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun loadScreenshots(context: Context, list: MutableList<ScreenshotItem>) {
    try {
        // 从 MediaStore 读取
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%CocWarTool%")

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                list.add(ScreenshotItem(
                    id = cursor.getLong(idCol),
                    path = cursor.getString(dataCol),
                    dateAdded = cursor.getLong(dateCol)
                ))
            }
        }

        // Fallback: 直接读文件目录（使用负 ID 避免与 MediaStore 真实 _ID 冲突）
        if (list.isEmpty()) {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CocWarTool")
            if (dir.exists()) {
                dir.listFiles()?.filter { it.extension.equals("png", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEachIndexed { index, file ->
                        list.add(ScreenshotItem(
                            id = -(index + 1).toLong(),  // 负 ID：仅文件删除，不操作 MediaStore
                            path = file.absolutePath,
                            dateAdded = file.lastModified()
                        ))
                    }
            }
        }
    } catch (_: Exception) {}
}

private fun deleteScreenshot(context: Context, item: ScreenshotItem) {
    try {
        // 仅真实 MediaStore ID（正数）才删除 MediaStore 记录；fallback 的负 ID 只删文件
        if (item.id > 0) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.delete(
                collection,
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(item.id.toString())
            )
        }
        // 同时删除文件
        runCatching { File(item.path).delete() }
    } catch (_: Exception) {}
}
