package com.cocwar.data.ocr

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Calendar

/**
 * 截图的读取与按「一次截图会话」分组。
 *
 * 截图由 [com.cocwar.service.ScreenCaptureService] 以文件名 COC_yyyyMMdd_HHmmss_SSS.png
 * 保存（毫秒精度），一次滚动截图产生多页、页间隔约 1.5~2s；两次手动截图间隔通常远大于
 * [DEFAULT_GAP_MS]，因此按时间间隔分组即可还原会话，无需改造截图服务，历史截图同样可分组。
 */
object ScreenshotGrouper {

    /** 相邻两张图时间间隔超过该值即视为新会话。 */
    const val DEFAULT_GAP_MS = 60_000L

    /** 截图元数据；负 id 表示 private 目录 fallback（仅删文件，不操作 MediaStore）。 */
    data class ScreenshotInfo(
        val id: Long,
        val path: String,
        val timestampMillis: Long
    )

    /** 一次截图会话。 */
    data class ScreenshotGroup(
        val id: Long,
        val items: List<ScreenshotInfo>,
        val startMillis: Long
    )

    /** 读取全部截图（MediaStore + 私有目录 fallback），按时间升序返回。 */
    fun load(context: Context): List<ScreenshotInfo> {
        val list = mutableListOf<ScreenshotInfo>()
        try {
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
            context.contentResolver.query(
                collection, projection, selection, arrayOf("%CocWarTool%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val dateSeconds = cursor.getLong(dateCol)
                    list.add(
                        ScreenshotInfo(
                            id = id,
                            path = path,
                            timestampMillis = filenameMillis(path) ?: (dateSeconds * 1000)
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // fallback: 公共相册目录（负 id 避免与 MediaStore 真实 _ID 冲突）
        if (list.isEmpty()) {
            list += readDir(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CocWarTool"),
                idBase = 0
            )
        }
        // fallback: 私有目录（ScreenCaptureService 保存失败时的兜底保存点）
        if (list.isEmpty()) {
            list += readDir(File(context.filesDir, "screenshots"), idBase = 1000)
        }

        return list.sortedBy { it.timestampMillis }
    }

    private fun readDir(dir: File, idBase: Int): List<ScreenshotInfo> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapIndexed { index, file ->
                ScreenshotInfo(
                    id = -(idBase + index + 1).toLong(),
                    path = file.absolutePath,
                    timestampMillis = filenameMillis(file.absolutePath) ?: file.lastModified()
                )
            }
            ?: emptyList()
    }

    /** 从文件名解析毫秒时间戳（COC_yyyyMMdd_HHmmss_SSS.png）；失败返回 null。 */
    fun filenameMillis(path: String): Long? {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val m = FILENAME_RE.find(name) ?: return null
        val (y, mo, d, h, mi, s, ms) = m.destructured
        val cal = Calendar.getInstance().apply {
            clear()
            set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
        }
        return cal.timeInMillis + ms.toInt()
    }

    /** 按时间间隔分组：相邻间隔大于 [gapMillis] 开新组。 */
    fun group(items: List<ScreenshotInfo>, gapMillis: Long = DEFAULT_GAP_MS): List<ScreenshotGroup> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.sortedBy { it.timestampMillis }
        val groups = mutableListOf<ScreenshotGroup>()
        var current = mutableListOf<ScreenshotInfo>()
        var start = sorted.first().timestampMillis
        var prev = sorted.first().timestampMillis
        for (item in sorted) {
            if (current.isNotEmpty() && item.timestampMillis - prev > gapMillis) {
                groups.add(ScreenshotGroup(current.first().timestampMillis, current.toList(), start))
                current = mutableListOf()
                start = item.timestampMillis
            }
            current.add(item)
            prev = item.timestampMillis
        }
        if (current.isNotEmpty()) {
            groups.add(ScreenshotGroup(current.first().timestampMillis, current.toList(), start))
        }
        return groups
    }

    private val FILENAME_RE =
        Regex("COC_(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})_(\\d{3})\\.png", RegexOption.IGNORE_CASE)
}
