package com.cocwar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.cocwar.R

/**
 * 前台服务 + 可拖动悬浮球：点击在游戏中触发截图。
 * 截图时自动隐藏，截图完成后恢复。
 */
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var ballView: ImageView? = null

    // 拖动
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var startClickTime = 0L

    companion object {
        const val TAG = "FloatingBall"
        var instance: FloatingBallService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }

        fun isRunning(): Boolean = instance != null
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCaptureService.ACTION_HIDE_OVERLAY -> {
                    // 截图期间隐藏悬浮球，且不显示任何进度浮层：
                    // 否则进度条/Toast 会被截进截图画面（用户看到的“通知弹窗被截图”）
                    ballView?.visibility = View.GONE
                }
                ScreenCaptureService.ACTION_SHOW_OVERLAY -> {
                    ballView?.visibility = View.VISIBLE
                    updateForegroundProgress("截图悬浮球运行中")
                }
                ScreenCaptureService.ACTION_CAPTURE_DONE -> {
                    val pages = intent.getIntExtra("pages", 0)
                    Log.i(TAG, "截图完成：共 $pages 页")
                    updateForegroundProgress("截图悬浮球运行中")
                    // 0 页说明截图流程失败/被中断，不弹「保存 0 张」误导通知
                    if (pages > 0) showCaptureDoneNotification(pages)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 必须先进入前台：startForegroundService 启动的服务若在 5 秒内（Android 12+）未调用
        // startForeground，系统会抛 ForegroundServiceDidNotStartInTimeException 导致崩溃。
        // 因此无论后续悬浮窗权限是否满足，都先启动前台通知。
        try { startForegroundSafe() } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败", e); stopSelf(); return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限未授予", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try { createBall() } catch (e: Exception) {
            Log.e(TAG, "悬浮球创建失败", e); stopSelf(); return
        }

        // 自动启动部落冲突
        launchGame()

        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_HIDE_OVERLAY)
            addAction(ScreenCaptureService.ACTION_SHOW_OVERLAY)
            addAction(ScreenCaptureService.ACTION_CAPTURE_DONE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        instance = null
        removeBall()
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafe() {
        val ch = NotificationChannel(
            "screenshot_ball",
            getString(R.string.channel_screenshot_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)

        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, com.cocwar.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "screenshot_ball")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("截图悬浮球运行中")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // ==================== 悬浮球 ====================

    private fun createBall() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 300
        }

        val ball = ImageView(this).apply {
            setImageResource(R.drawable.ic_ball)
            alpha = 0.7f
            setOnTouchListener { _, event -> handleBallTouch(event, params); true }
        }

        windowManager.addView(ball, params)
        ballView = ball
    }

    private fun handleBallTouch(event: MotionEvent, params: WindowManager.LayoutParams) {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x; initialY = params.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                isDragging = false
                startClickTime = System.currentTimeMillis()
            }
            // 第二根手指按下/抬起：忽略，避免多指交错时位移跳变或误判为点击
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 保持第一根手指的基准坐标
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 若第一根手指已抬起，重新以剩余手指建立基准，避免跳变
                if (event.pointerCount > 1) {
                    val idx = if (event.actionIndex == 0) 1 else 0
                    initialTouchX = event.getRawX(idx)
                    initialTouchY = event.getRawY(idx)
                    initialX = params.x
                    initialY = params.y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isDragging = true
                if (isDragging) {
                    params.x = initialX - dx
                    val maxY = resources.displayMetrics.heightPixels - (ballView?.height ?: 0)
                    params.y = (initialY + dy).coerceIn(0, maxY)
                    windowManager.updateViewLayout(ballView, params)
                }
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - startClickTime
                if (!isDragging && elapsed < 300) {
                    // 点击：触发截图
                    performClick()
                }
                if (isDragging) {
                    // 松手吸附边缘
                    snapToEdge(params)
                }
            }
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        // 获取屏幕宽度后吸附到最近边缘
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val ballWidth = ballView?.width ?: 0
        val margin = 24
        // 布局 gravity 为 TOP|END：params.x 表示距右边缘的距离。
        // 球距左边缘 ≈ screenWidth - ballWidth - params.x，据此判断吸附到左还是右。
        val distanceToLeft = screenWidth - ballWidth - params.x
        params.x = if (distanceToLeft < params.x) {
            // 距左边更近：吸附左边（x 需为整屏宽减球宽减边距）
            (screenWidth - ballWidth - margin).coerceAtLeast(margin)
        } else {
            // 距右边更近：吸附右边
            margin
        }
        // 防止拖出屏幕上下边界
        params.y = params.y.coerceIn(0, dm.heightPixels - (ballView?.height ?: 0))
        windowManager.updateViewLayout(ballView, params)
    }

    private fun performClick() {
        ballView?.alpha = 1.0f
        ballView?.postDelayed({ ballView?.alpha = 0.7f }, 200)

        val a11y = ScreenCaptureService.instance
        if (a11y == null) {
            // 无障碍未开启：提示并直接跳转系统无障碍设置页（该权限只能由用户在系统设置中手动开启）
            Toast.makeText(this, "无障碍服务未开启：请到 设置→无障碍→部落战数据管家 开启", Toast.LENGTH_LONG).show()
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } else {
            // 注意：这里不弹「开始截图」Toast——截图立即开始，Toast 会被截进画面。
            // 进度反馈通过前台通知的「截图进行中」文案与悬浮球闪动体现。
            updateForegroundProgress("截图进行中…")
            a11y.requestCapture()
        }
    }

    /** 更新前台服务通知文案（不遮挡游戏画面，也不会被截进截图），附「取消截图」入口。 */
    private fun updateForegroundProgress(text: String) {
        runCatching {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val cancelIntent = PendingIntent.getBroadcast(
                this, 2,
                Intent(ScreenCaptureService.ACTION_CANCEL_CAPTURE).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, "screenshot_ball")
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                    Intent(this, com.cocwar.ui.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .addAction(0, "取消截图", cancelIntent)
                .setOngoing(true)
                .build()
            nm.notify(1, notification)
        }
    }

    // ==================== 启动游戏 ====================

    private fun launchGame() {
        try {
            // 尝试多个版本的部落冲突包名（国际版 / 昆仑版 / 腾讯版）
            val pkgs = listOf(
                "com.supercell.clashofclans",
                "com.supercell.clashofclans.kunlun",
                "com.tencent.tmgp.supercell.clashofclans"
            )
            for (pkg in pkgs) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Log.i(TAG, "已启动部落冲突 ($pkg)")
                    return
                }
            }
            Log.w(TAG, "未找到部落冲突，尝试的包名: $pkgs")
            Toast.makeText(this, "未找到部落冲突，请确认已安装", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "启动游戏失败", e)
        }
    }

    // ==================== 截图完成通知 ====================

    private fun showCaptureDoneNotification(pages: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 使用高优先级渠道确保弹窗可见
        val channelId = "screenshot_done"
        val channel = NotificationChannel(
            channelId,
            "截图完成",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)

        val clickIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, com.cocwar.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("截图完成")
            .setContentText("共保存 $pages 张截图到相册")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(clickIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notification)
    }

    private fun removeBall() {
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
    }
}
