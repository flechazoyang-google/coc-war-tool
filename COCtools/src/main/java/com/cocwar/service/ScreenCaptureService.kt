package com.cocwar.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 无障碍服务（中枢）：负责截图、上滑滚动拼接、保存 PNG 到本地。
 * minSdk 30+ 直接拥有 takeScreenshot 能力，无需 MediaProjection。
 */
class ScreenCaptureService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var capturing = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "无障碍服务 onCreate")
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** 由悬浮球点击调用，开始一次「截图 + 滚动拼接」。 */
    fun requestCapture() {
        if (capturing) {
            Log.w(TAG, "requestCapture 被忽略：上一次截图尚未结束")
            return
        }
        capturing = true
        Log.i(TAG, "requestCapture 开始")
        scope.launch {
            try {
                val (pageCount, savedFiles) = captureWithScrollStitch(maxPages = 20)
                Log.i(TAG, "截图完成：共 $pageCount 页，已保存到 ${savedFiles.joinToString { it.absolutePath }}")
                showToast("截图完成：共 $pageCount 页")
                broadcastResult(pageCount, savedFiles)
            } catch (e: Exception) {
                Log.e(TAG, "截图失败", e)
                showToast("截图失败：${e.message}")
            } finally {
                capturing = false
            }
        }
    }

    /**
     * 循环截图→比对→上滑→终止。
     * 返回 (页数, 保存的文件列表)。
     */
    private suspend fun captureWithScrollStitch(maxPages: Int): Pair<Int, List<File>> {
        val savedFiles = mutableListOf<File>()

        hideOverlays()
        try {
            var prevSmall: Bitmap? = null
            var lowDiffStreak = 0
            for (page in 0 until maxPages) {
                val shot = takeScreenshotWithRetry()
                if (shot == null) {
                    Log.e(TAG, "page=$page 连续截图失败，终止")
                    break
                }
                Log.d(TAG, "page=$page 截图成功 ${shot.width}x${shot.height}")

                val savedFile = saveScreenshot(shot)
                savedFiles.add(savedFile)
                Log.i(TAG, "page=$page 截图已保存到 ${savedFile.absolutePath}")

                if (page == maxPages - 1) {
                    shot.recycle()
                    break
                }

                val small = downscale(shot)

                if (prevSmall != null) {
                    val diff = bitmapDiff(prevSmall, small)
                    Log.d(TAG, "page=$page 截图差异=${"%.3f".format(diff)}")
                    if (diff < 0.03f) {
                        lowDiffStreak++
                        Log.d(TAG, "page=$page 截图几乎未变化(streak=$lowDiffStreak)")
                        if (lowDiffStreak >= 2) {
                            Log.d(TAG, "page=$page 连续2页无变化，判定已到列表底部，删除重复截图")
                            // 删除刚保存的重复页面
                            savedFiles.removeLastOrNull()?.delete()
                            small.recycle()
                            prevSmall?.recycle()
                            prevSmall = null
                            shot.recycle()
                            break
                        }
                    } else {
                        lowDiffStreak = 0
                    }
                }
                prevSmall?.recycle()
                prevSmall = small

                // 自适应手势：低差异 → 微步确认到底；正常差异 → 标准步长
                val variant = if (lowDiffStreak > 0 && page > 0) 1 else 0
                val ok = dispatchSwipeUp(variant)
                val waitMs = if (variant == 1) 800L else 900L
                Log.d(TAG, "page=$page 上滑手势(variant=$variant, ok=$ok)，等待 ${waitMs}ms")
                delay(waitMs)
                shot.recycle()
            }
            prevSmall?.recycle()
        } finally {
            showOverlays()
        }

        return Pair(savedFiles.size, savedFiles)
    }

    // ==================== 截图 ====================

    private val isAtLeastApi34 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

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
                Log.e(TAG, "takeScreenshot 失败，errorCode=$errorCode")
                if (cont.isActive) cont.resume(null)
            }
        }
        if (isAtLeastApi34) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } else {
            // API 30-33 的两参数 takeScreenshot：compileSdk 34 的 stub 已移除，用反射调用
            try {
                val m = AccessibilityService::class.java.getMethod(
                    "takeScreenshot", Executor::class.java, TakeScreenshotCallback::class.java
                )
                m.invoke(this, mainExecutor, callback)
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot(反射) 失败", e)
                if (cont.isActive) cont.resume(null)
            }
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

    // ==================== 滑动 ====================

    /** 从 SharedPreferences 读取用户配置的滑动步长（屏幕高度百分比，默认 30%）。 */
    private fun getSwipeStepPercent(): Float {
        val prefs = getSharedPreferences("cocwar_capture", MODE_PRIVATE)
        return prefs.getFloat("swipe_step_percent", 30f).coerceIn(10f, 60f)
    }

    /**
     * 下发「底部→顶部」上滑手势。
     * variant=0：标准步长（用户配置）；1：微步（确认到底）；2：大步（快速翻页）。
     *
     * 所有坐标使用 [0, h] 屏幕物理像素范围，且：
     *   - startY 上限 0.82h（避开顶部状态栏区域）
     *   - endY   下限 0.08h（避开底部导航栏区域）
     *   - 滑动距离 = startY - endY，始终 >= 0.05h（最小 5% 屏幕高度）
     */
    private fun dispatchSwipeUp(variant: Int = 0): Boolean {
        val dm = getSystemService(DisplayManager::class.java) ?: return false
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        // 安全边界（像素）
        val topMargin = (h * 0.08f).toInt()    // 顶部留 8%
        val bottomMargin = (h * 0.08f).toInt()  // 底部留 8%
        val usableH = h - topMargin - bottomMargin

        // 目标滑动距离（像素），最小 5% 屏幕高度
        val targetDist = when (variant) {
            1 -> (h * getSwipeStepPercent() / 100f * 0.55f).toInt()  // 微步：标准步长的 55%
            2 -> (h * getSwipeStepPercent() / 100f * 1.5f).toInt()   // 大步：1.5 倍
            else -> (h * getSwipeStepPercent() / 100f).toInt()       // 标准
        }.coerceIn((h * 0.05f).toInt(), usableH)  // 限制在 5%~可用高度 之间

        // startY：可用区域底部往上一点，保证 endY 不越界
        val startY = (topMargin + targetDist).coerceAtMost(h - bottomMargin).toFloat()
        val endY = (startY - targetDist).coerceAtLeast(topMargin.toFloat())

        val dur = when (variant) {
            1 -> 280L
            2 -> 420L
            else -> 340L
        }

        val x = w * 0.5f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, dur)
            )
            .build()
        val actualDist = startY - endY
        Log.d(TAG, "dispatchSwipeUp v=$variant targetDist=${targetDist}px actual=${actualDist.toInt()}px " +
                "($x,${startY.toInt()})→($x,${endY.toInt()}) dur=${dur}ms")
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gesture: android.accessibilityservice.GestureDescription) {
                Log.d(TAG, "手势已完成")
            }
            override fun onCancelled(gesture: android.accessibilityservice.GestureDescription) {
                Log.w(TAG, "手势被取消")
            }
        }
        return try {
            dispatchGesture(gesture, cb, null)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 异常", e)
            false
        }
    }

    // ==================== 广播 ====================

    private fun hideOverlays() = sendBroadcast(Intent(ACTION_HIDE_OVERLAY))
    private fun showOverlays() = sendBroadcast(Intent(ACTION_SHOW_OVERLAY))

    private fun broadcastResult(pageCount: Int, files: List<File>) {
        val i = Intent(ACTION_CAPTURE_DONE).apply {
            putExtra("pages", pageCount)
            putExtra("screenshotPaths", files.map { it.absolutePath }.toTypedArray())
        }
        sendBroadcast(i)
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 图片工具 ====================

    private fun saveScreenshot(bitmap: Bitmap): File {
        val dir = File(filesDir, "screenshots")
        if (!dir.exists()) dir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "COC_${ts}.png"
        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        Log.i(TAG, "截图已保存: ${file.absolutePath}")
        return file
    }

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
        for (i in pa.indices) {
            val la = lum(pa[i])
            val lb = lum(pb[i])
            sum += kotlin.math.abs(la - lb)
        }
        return sum.toFloat() / (pa.size * 255)
    }

    private fun lum(px: Int): Int {
        val r = (px shr 16) and 0xFF
        val g = (px shr 8) and 0xFF
        val bl = px and 0xFF
        return (r * 299 + g * 587 + bl * 114) / 1000
    }

    companion object {
        const val TAG = "ScreenCapture"
        var instance: ScreenCaptureService? = null
            private set

        const val ACTION_HIDE_OVERLAY = "com.cocwar.HIDE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.cocwar.SHOW_OVERLAY"
        const val ACTION_CAPTURE_DONE = "com.cocwar.CAPTURE_DONE"

        /**
         * 检查无障碍服务是否已开启。
         */
        fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
            val pkg = context.packageName
            // 系统存储的无障碍服务名有两种格式：
            //   相对路径：com.cocwar/.service.ScreenCaptureService
            //   完整路径：com.cocwar/com.cocwar.service.ScreenCaptureService
            val shortName = "$pkg/.service.ScreenCaptureService"
            val fullName = "$pkg/$pkg.service.ScreenCaptureService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val result = enabledServices.split(':').any {
                it.equals(shortName, ignoreCase = true) || it.equals(fullName, ignoreCase = true)
            }
            Log.d(TAG, "isAccessibilityServiceEnabled: short=$shortName full=$fullName raw=$enabledServices → $result")
            return result
        }
    }
}
