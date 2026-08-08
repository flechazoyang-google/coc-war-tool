package com.cocwar.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 无障碍服务：截图 → 差异比对 → 上滑 → 循环，直到检测到列表底部。
 * 截图保存到系统相册 Pictures/CocWarTool/。
 */
class ScreenCaptureService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    @Volatile private var capturing = false
    private var captureJob: Job? = null
    private var captureGeneration = 0  // 代际计数器，防止取消/新请求竞态

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_CAPTURE) {
                Log.i(TAG, "收到取消截图广播")
                cancelCapture()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "无障碍服务 onCreate")
        val filter = IntentFilter(ACTION_CANCEL_CAPTURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cancelReceiver, filter)
        }
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(cancelReceiver) }
        scope?.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun requestCapture() {
        if (capturing) {
            Log.w(TAG, "requestCapture 被忽略：上一次截图尚未结束")
            return
        }
        capturing = true
        captureGeneration++
        val thisGen = captureGeneration
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        Log.i(TAG, "requestCapture 开始 (gen=$thisGen)")
        captureJob = scope!!.launch {
            try {
                // 定期清理旧截图
                cleanOldScreenshots(this@ScreenCaptureService)
                val pageCount = captureWithScrollStitch(maxPages = 30)
                Log.i(TAG, "截图完成：共 $pageCount 页")
                showToast("截图完成：共 $pageCount 页")
                broadcastResult(pageCount)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动取消：协程取消异常不视为失败，直接重新抛出（配合 finally 收尾）
                Log.i(TAG, "截图已取消")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "截图失败", e)
                showToast("截图失败：${e.message}")
            } finally {
                // 仅当仍是当前代时才重置状态，避免取消后旧协程覆盖新请求的状态
                if (captureGeneration == thisGen) {
                    capturing = false
                    showOverlays()
                }
            }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        capturing = false
        captureGeneration++  // 使旧协程的 finally 无效，避免与新请求竞态
        showOverlays()
        showToast("截图已取消")
    }

    private suspend fun captureWithScrollStitch(maxPages: Int): Int {
        var savedCount = 0

        hideOverlays()
        try {
            var prevSmall: Bitmap? = null
            var lowDiffStreak = 0
            // 上一张已保存的 Uri，判定到底时连同当前重复页一起移除
            var lastSavedUri: Uri? = null
            for (page in 0 until maxPages) {
                val shot = takeScreenshotWithRetry() ?: break
                Log.d(TAG, "page=$page 截图成功 ${shot.width}x${shot.height}")

                if (page == maxPages - 1) {
                    saveToGallery(shot)
                    savedCount++
                    shot.recycle()
                    break
                }

                val small = downscale(shot)
                var reachedBottom = false
                if (prevSmall != null) {
                    val diff = bitmapDiff(prevSmall, small)
                    Log.d(TAG, "page=$page 差异=${"%.3f".format(diff)}")
                    if (diff < 0.03f) {
                        lowDiffStreak++
                        if (lowDiffStreak >= 2) {
                            // 连续2页无变化，判定到底：不保存当前重复页，
                            // 并删除上一张同样重复的截图（它先于判定保存了）
                            Log.d(TAG, "连续${lowDiffStreak}页无变化，判定到底")
                            reachedBottom = true
                        }
                    } else {
                        lowDiffStreak = 0
                    }
                }

                if (reachedBottom) {
                    // 移除上一张与当前页重复的截图
                    lastSavedUri?.let { uri ->
                        runCatching {
                            contentResolver.delete(uri, null, null)
                            savedCount--
                            Log.d(TAG, "已删除重复截图")
                        }
                    }
                    lastSavedUri = null
                    small.recycle(); prevSmall?.recycle()
                    shot.recycle(); break
                }

                // 非重复页才保存
                lastSavedUri = saveToGallery(shot)
                savedCount++

                prevSmall?.recycle()
                prevSmall = small

                val swipeOk = dispatchSwipeUp()
                if (!swipeOk) {
                    Log.w(TAG, "上滑手势派发失败，停止滚动截图")
                    small.recycle(); prevSmall?.recycle()
                    shot.recycle(); break
                }
                delay(900)
                shot.recycle()
            }
            prevSmall?.recycle()
        } finally {
            showOverlays()
        }
        return savedCount
    }

    // ==================== 截图 ====================

    private suspend fun takeScreenshotSuspend(): Bitmap? = suspendCancellableCoroutine { cont ->
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer: HardwareBuffer = result.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                if (cont.isActive) cont.resume(bitmap)
            }
            override fun onFailure(errorCode: Int) {
                Log.e(TAG, "takeScreenshot 失败, errorCode=$errorCode")
                if (cont.isActive) cont.resume(null)
            }
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot 异常", e)
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun takeScreenshotWithRetry(maxAttempts: Int = 3): Bitmap? {
        repeat(maxAttempts) { attempt ->
            val b = takeScreenshotSuspend()
            if (b != null) return b
            Log.w(TAG, "截图第${attempt + 1}次失败，300ms 后退避重试")
            delay(300)
        }
        return null
    }

    // ==================== 保存到相册 ====================

    /** 保存到相册；返回可删除的 Uri（供后续移除重复截图），fallback 到私有目录时返回 null。 */
    private suspend fun saveToGallery(bitmap: Bitmap): Uri? {
        // PNG 压缩 + 写盘是重 IO 操作，移到 IO 线程，避免阻塞主线程导致卡顿/ANR
        return withContext(Dispatchers.IO) {
            var uri: Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "COC_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CocWarTool")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    }
                    Log.i(TAG, "截图已保存到相册")
                }
            } catch (e: Exception) {
                // fallback: 保存到私有目录
                Log.e(TAG, "保存到相册失败，fallback 到私有目录", e)
                // 清理已插入的 pending 条目，避免 MediaStore 残留幽灵记录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                    try {
                        contentResolver.delete(uri!!, null, null)
                    } catch (_: Exception) {}
                }
                uri = null
                val dir = File(filesDir, "screenshots")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "COC_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            uri
        }
    }

    // ==================== 滑动 ====================

    private fun getSwipeStepPercent(): Float {
        val prefs = getSharedPreferences("cocwar_capture", MODE_PRIVATE)
        return prefs.getFloat("swipe_step_percent", 30f).coerceIn(10f, 60f)
    }

    private fun dispatchSwipeUp(): Boolean {
        val dm = getSystemService(DisplayManager::class.java) ?: return false
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        val topMargin = (h * 0.08f).toInt()
        val bottomMargin = (h * 0.08f).toInt()
        val usableH = h - topMargin - bottomMargin

        val targetDist = (h * getSwipeStepPercent() / 100f).toInt()
            .coerceIn((h * 0.05f).toInt(), usableH)

        val startY = (topMargin + targetDist).coerceAtMost(h - bottomMargin).toFloat()
        val endY = (startY - targetDist).coerceAtLeast(topMargin.toFloat())
        val x = w * 0.5f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 340))
            .build()

        Log.d(TAG, "上滑 (${x},${startY.toInt()})→(${x},${endY.toInt()}) dist=${(startY-endY).toInt()}px")
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gesture: android.accessibilityservice.GestureDescription) {}
            override fun onCancelled(gesture: android.accessibilityservice.GestureDescription) {}
        }
        return try {
            dispatchGesture(gesture, cb, null)
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 广播 ====================

    private fun hideOverlays() = sendBroadcast(Intent(ACTION_HIDE_OVERLAY).setPackage(packageName))
    private fun showOverlays() = sendBroadcast(Intent(ACTION_SHOW_OVERLAY).setPackage(packageName))

    private fun broadcastResult(pageCount: Int) {
        sendBroadcast(Intent(ACTION_CAPTURE_DONE).setPackage(packageName).apply { putExtra("pages", pageCount) })
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 图片工具 ====================

    private fun downscale(src: Bitmap, targetW: Int = 240): Bitmap {
        val targetH = (src.height * targetW / src.width.toFloat()).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    private fun bitmapDiff(a: Bitmap, b: Bitmap): Float {
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        val pa = IntArray(w * h)
        val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, 0, 0, w, h)
        b.getPixels(pb, 0, w, 0, 0, w, h)
        var sum = 0L
        for (i in pa.indices) sum += kotlin.math.abs(lum(pa[i]) - lum(pb[i]))
        return sum.toFloat() / (pa.size * 255)
    }

    private fun lum(px: Int): Int {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val bl = px and 0xFF
        return (r * 299 + g * 587 + bl * 114) / 1000
    }

    // ==================== 清理 ====================

    companion object {
        const val TAG = "ScreenCapture"
        var instance: ScreenCaptureService? = null
            private set

        const val ACTION_HIDE_OVERLAY = "com.cocwar.HIDE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.cocwar.SHOW_OVERLAY"
        const val ACTION_CAPTURE_DONE = "com.cocwar.CAPTURE_DONE"
        const val ACTION_CANCEL_CAPTURE = "com.cocwar.CANCEL_CAPTURE"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val pkg = context.packageName
            val shortName = "$pkg/.service.ScreenCaptureService"
            val fullName = "$pkg/$pkg.service.ScreenCaptureService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any {
                it.equals(shortName, ignoreCase = true) || it.equals(fullName, ignoreCase = true)
            }
        }

        /** 清理超过 N 天的旧截图 */
        suspend fun cleanOldScreenshots(context: Context) {
            withContext(Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences("cocwar_capture", Context.MODE_PRIVATE)
                    val cleanDays = prefs.getInt("clean_days", 7)
                    val cutoff = System.currentTimeMillis() - cleanDays * 24 * 60 * 60 * 1000L

                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
                    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?"
                    val selectionArgs = arrayOf("%CocWarTool%", (cutoff / 1000).toString())

                    context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val ids = mutableListOf<String>()
                        while (cursor.moveToNext()) ids.add(cursor.getString(idCol))
                        ids.forEach { id ->
                            context.contentResolver.delete(collection,
                                "${MediaStore.Images.Media._ID} = ?", arrayOf(id))
                        }
                        if (ids.isNotEmpty()) Log.i(TAG, "定期清理: 删除 ${ids.size} 张过期截图")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "定期清理失败", e)
                }
            }
        }

        /** 清理全部截图 */
        suspend fun cleanAllScreenshotsAsync(context: Context) {
            withContext(Dispatchers.IO) {
                try {
                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                    context.contentResolver.delete(collection, selection, arrayOf("%CocWarTool%"))

                    // 同时清理私有目录
                    val dir = File(context.filesDir, "screenshots")
                    if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
                } catch (_: Exception) {}
            }
        }
    }
}
