package com.cocwar.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cocwar.CocWarApplication
import com.cocwar.R
import com.cocwar.data.ocr.OcrClient
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.ocr.OcrCsvAggregator
import com.cocwar.data.ocr.OcrCsvExtractor
import com.cocwar.ui.util.ImageCompress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * 前台服务：后台静默批量识图。
 *
 * 把一组截图逐张调用视觉模型（顺序执行，避免限额/限流），逐张把进度写回 Room 的
 * pending_imports 草稿；全部完成后按排名聚合为一条 CSV，草稿状态置为 ready，战报页据此
 * 显示「待确认」项。进度通过前台通知展示，可退出 App / 锁屏继续执行。
 */
class OcrBatchService : Service() {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL) {
                Log.i(TAG, "收到取消识图广播")
                job?.cancel()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL))
        }
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(cancelReceiver) }
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先进入前台（5 秒内必须调用 startForeground，否则 Android 12+ 崩溃）
        startForegroundInternal("识图进行中…")
        val paths = intent?.getStringArrayListExtra(EXTRA_PATHS)?.toList() ?: emptyList()
        val replaceId = intent?.getStringExtra(EXTRA_REPLACE_ID)
        job = scope?.launch { runBatch(paths, replaceId) }
        return START_NOT_STICKY
    }

    private suspend fun runBatch(paths: List<String>, replaceId: String?) {
        val app = application as CocWarApplication
        val repo = app.repository
        val config = OcrConfig(applicationContext)

        if (replaceId != null) repo.deletePendingImport(replaceId)
        val id = repo.createPendingImport(paths)

        if (paths.isEmpty()) {
            repo.failPendingImport(id, "没有可识别的截图")
            showDoneNotification(false, "识图失败：没有可识别的截图")
            stopSelfLater()
            return
        }
        if (!config.isConfigured) {
            repo.failPendingImport(id, "未配置 API Key，请到 设置 → 识图设置 填写")
            showDoneNotification(false, "识图失败：未配置 API Key")
            stopSelfLater()
            return
        }

        val client = OcrClient(apiKey = config.apiKey, baseUrl = config.baseUrl, model = config.model)
        val csvs = mutableListOf<String>()
        var processed = 0
        try {
            for (path in paths) {
                coroutineContext.ensureActive()
                val base64 = ImageCompress.readAndCompressToBase64FromPath(path)
                if (base64 != null) {
                    val raw = client.recognize(base64)
                    val csv = OcrCsvExtractor.extract(raw)
                    if (csv.isNotBlank()) csvs.add(csv)
                }
                processed++
                repo.updatePendingProgress(id, processed)
                updateForeground("识图进行中 ($processed/${paths.size})")
            }
            val merged = OcrCsvAggregator.aggregate(csvs)
            if (merged.isBlank()) {
                repo.failPendingImport(id, "未识别到任何战报数据，请检查截图后重试")
                showDoneNotification(false, "识图失败：未识别到任何战报数据")
            } else {
                repo.completePendingImport(id, merged)
                showDoneNotification(true, "识图完成，战报待确认")
            }
        } catch (e: CancellationException) {
            repo.failPendingImport(id, "已取消")
            showDoneNotification(false, "识图已取消")
            throw e
        } catch (e: OcrClient.OcrException.NotConfigured) {
            repo.failPendingImport(id, e.message ?: "未配置 API Key")
            showDoneNotification(false, e.message ?: "识图失败：未配置 API Key")
        } catch (e: OcrClient.OcrException.ApiError) {
            repo.failPendingImport(id, e.message ?: "API 错误")
            showDoneNotification(false, e.message ?: "识图失败")
        } catch (e: OcrClient.OcrException) {
            repo.failPendingImport(id, e.message ?: "识别失败")
            showDoneNotification(false, e.message ?: "识图失败")
        } catch (e: Exception) {
            repo.failPendingImport(id, "识别失败：${e.message}")
            showDoneNotification(false, "识图失败：${e.message}")
        } finally {
            instance = null
            stopForegroundAfter()
            stopSelf()
        }
    }

    private fun stopSelfLater() {
        instance = null
        stopForegroundAfter()
        stopSelf()
    }

    // ==================== 通知 ====================

    private fun startForegroundInternal(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm, CHANNEL_PROGRESS, "识图进度", NotificationManager.IMPORTANCE_LOW)
        val notification = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .setContentIntent(mainActivityPendingIntent())
            .addAction(0, "取消", cancelPendingIntent())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateForeground(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .setContentIntent(mainActivityPendingIntent())
            .addAction(0, "取消", cancelPendingIntent())
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    private fun showDoneNotification(success: Boolean, text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel(nm, CHANNEL_DONE, "识图完成", NotificationManager.IMPORTANCE_HIGH)
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setContentTitle(if (success) "识图完成" else "识图未完成")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(mainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(DONE_NOTIF_ID, notification)
    }

    private fun stopForegroundAfter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel(nm: NotificationManager, id: String, name: String, importance: Int) {
        nm.createNotificationChannel(NotificationChannel(id, name, importance))
    }

    private fun mainActivityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, com.cocwar.ui.MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 1, Intent(ACTION_CANCEL).setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val TAG = "OcrBatch"
        var instance: OcrBatchService? = null
            private set

        const val ACTION_CANCEL = "com.cocwar.OCR_BATCH_CANCEL"
        private const val EXTRA_PATHS = "extra_paths"
        private const val EXTRA_REPLACE_ID = "extra_replace_id"
        private const val CHANNEL_PROGRESS = "ocr_batch_progress"
        private const val CHANNEL_DONE = "ocr_batch_done"
        private const val NOTIF_ID = 1011
        private const val DONE_NOTIF_ID = 1012

        fun isRunning(): Boolean = instance != null

        /** 启动批量识图；[replaceId] 非空时先删除该旧草稿（failed 重试）。 */
        fun start(context: Context, imagePaths: List<String>, replaceId: String? = null) {
            val intent = Intent(context, OcrBatchService::class.java)
                .putStringArrayListExtra(EXTRA_PATHS, ArrayList(imagePaths))
                .putExtra(EXTRA_REPLACE_ID, replaceId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
