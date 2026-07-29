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
import android.widget.LinearLayout
import android.widget.TextView
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
    private var progressOverlay: LinearLayout? = null
    private var progressText: TextView? = null

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
                    ballView?.visibility = View.GONE
                    showProgressOverlay()
                }
                ScreenCaptureService.ACTION_SHOW_OVERLAY -> {
                    removeProgressOverlay()
                    ballView?.visibility = View.VISIBLE
                }
                ScreenCaptureService.ACTION_CAPTURE_DONE -> {
                    val pages = intent.getIntExtra("pages", 0)
                    Log.i(TAG, "截图完成：共 $pages 页")
                    Toast.makeText(this@FloatingBallService, "截图完成：共 $pages 页", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限未授予", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try { startForegroundSafe() } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败", e); stopSelf(); return
        }

        try { createBall() } catch (e: Exception) {
            Log.e(TAG, "悬浮球创建失败", e); stopSelf(); return
        }

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
        removeProgressOverlay()
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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x; initialY = params.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                isDragging = false
                startClickTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isDragging = true
                if (isDragging) {
                    params.x = initialX - dx
                    params.y = initialY + dy
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
        // 如果球在左半边就吸附左边，否则右边
        params.x = if (params.x < screenWidth / 2) 24 else 24
        windowManager.updateViewLayout(ballView, params)
    }

    private fun performClick() {
        ballView?.alpha = 1.0f
        ballView?.postDelayed({ ballView?.alpha = 0.7f }, 200)

        val a11y = ScreenCaptureService.instance
        if (a11y == null) {
            Toast.makeText(this, "无障碍服务未开启：请到 设置→无障碍→部落战数据管家 开启", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "开始截图，长按进度条可取消", Toast.LENGTH_SHORT).show()
            a11y.requestCapture()
        }
    }

    // ==================== 进度条浮层 ====================

    private fun showProgressOverlay() {
        try {
            removeProgressOverlay()

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32, 16, 32, 16)
                setBackgroundColor(0xCC000000.toInt())
                setOnLongClickListener {
                    // 长按取消截图
                    ScreenCaptureService.instance?.cancelCapture()
                    sendBroadcast(Intent(ScreenCaptureService.ACTION_CANCEL_CAPTURE))
                    true
                }
            }

            progressText = TextView(this).apply {
                text = "📷 截图进行中…"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            }
            container.addView(progressText)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }
            // 取消 FLAG_NOT_TOUCHABLE 让长按可接收
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

            windowManager.addView(container, params)
            progressOverlay = container
        } catch (e: Exception) {
            Log.e(TAG, "创建进度条失败", e)
        }
    }

    private fun removeProgressOverlay() {
        progressOverlay?.let { runCatching { windowManager.removeView(it) } }
        progressOverlay = null
        progressText = null
    }

    private fun removeBall() {
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
    }
}
