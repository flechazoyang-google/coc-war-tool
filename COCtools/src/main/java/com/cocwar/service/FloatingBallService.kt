package com.cocwar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.cocwar.R

/**
 * 前台服务 + 悬浮球：用户点击悬浮球触发截图。
 * 截图前收到 HIDE_OVERLAY 广播隐藏自身，截完收到 SHOW_OVERLAY 恢复，避免污染截图。
 */
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var ballView: ImageView? = null

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
                ScreenCaptureService.ACTION_HIDE_OVERLAY -> hideUi()
                ScreenCaptureService.ACTION_SHOW_OVERLAY -> showUi()
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
        Log.i(TAG, "onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val canOverlay = Settings.canDrawOverlays(this)
        Log.i(TAG, "canDrawOverlays=$canOverlay")
        if (!canOverlay) {
            Log.e(TAG, "缺少悬浮窗权限")
            Toast.makeText(this, "悬浮窗权限未授予，请在设置中开启", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            startForegroundSafe()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败", e)
            Toast.makeText(this, "前台服务启动失败：${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            createBall()
            Log.i(TAG, "悬浮球创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "addView 失败", e)
            Toast.makeText(this, "悬浮球创建失败：${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
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
        Log.i(TAG, "onDestroy")
        instance = null
        removeBall()
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafe() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel(
            "screenshot_ball",
            getString(R.string.channel_screenshot_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "screenshot_ball")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.channel_screenshot_desc))
            .setSmallIcon(R.drawable.ic_app_logo)
            .build()
    }

    private fun createBall() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }
        val ball = ImageView(this).apply {
            setImageResource(R.drawable.ic_ball)
            setOnClickListener {
                Log.i(TAG, "悬浮球点击 → requestCapture")
                val a11y = ScreenCaptureService.instance
                if (a11y == null) {
                    Log.e(TAG, "无障碍服务未启用")
                    Toast.makeText(
                        this@FloatingBallService,
                        "无障碍服务未开启：请到 设置→无障碍→部落战数据管家 开启",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    a11y.requestCapture()
                }
            }
        }
        windowManager.addView(ball, params)
        ballView = ball
    }

    private fun hideUi() {
        ballView?.visibility = View.GONE
    }

    private fun showUi() {
        ballView?.visibility = View.VISIBLE
    }

    private fun removeBall() {
        ballView?.let { windowManager.removeView(it) }
        ballView = null
    }
}
