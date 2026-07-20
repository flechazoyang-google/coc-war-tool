package com.cocwar.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.cocwar.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Gitee 仓库信息（硬编码，发布时无需改动）。
 */
private const val GITEE_OWNER = "yang-genhao"
private const val GITEE_REPO = "coc-war-tool"
private const val GITEE_API = "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/releases/latest"

data class UpdateInfo(
    val version: String,       // 如 "1.2"
    val tagName: String,       // 如 "v1.2"
    val body: String,          // 更新说明
    val apkUrl: String,        // APK 下载地址
    val fileSize: Long = 0     // 文件大小（字节）
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CHANNEL_ID = "update_download"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 检查 Gitee 最新 release 是否有更新。
     * @return UpdateInfo 如果有新版本，null 表示已是最新或检查失败。
     */
    suspend fun check(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(GITEE_API).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API 返回 ${response.code}"))
            }
            val body = response.body?.string() ?: ""
            val json = JsonParser.parseString(body).asJsonObject

            val tagName = json.get("tag_name")?.asString ?: ""
            val version = tagName.removePrefix("v").removePrefix("V")
            val desc = json.get("body")?.asString?.take(500) ?: ""
            val assets = json.getAsJsonArray("assets") ?: return@withContext Result.success(null)

            // 找 .apk 文件
            var apkUrl = ""
            for (asset in assets) {
                val assetObj = asset.asJsonObject
                val name = assetObj.get("name")?.asString ?: ""
                if (name.endsWith(".apk")) {
                    apkUrl = assetObj.get("browser_download_url")?.asString ?: ""
                    break
                }
            }
            if (apkUrl.isEmpty()) {
                return@withContext Result.failure(Exception("未找到 APK 下载地址"))
            }

            val currentVersion = BuildConfig.VERSION_NAME
            Log.d(TAG, "当前: $currentVersion, 最新: $version")

            if (compareVersion(version, currentVersion) > 0) {
                Result.success(UpdateInfo(version, tagName, desc, apkUrl))
            } else {
                Result.success(null) // 已是最新
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Result.failure(e)
        }
    }

    /**
     * 下载 APK 到缓存目录，显示通知进度，完成后触发安装。
     */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)

            val downloadFile = File(context.cacheDir, "update_${info.version}.apk")
            if (downloadFile.exists()) downloadFile.delete()

            val request = Request.Builder().url(info.apkUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("下载失败 HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("响应为空"))
            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(downloadFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastNotifyTime = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        // 每秒更新一次通知
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > 1000 && total > 0) {
                            lastNotifyTime = now
                            val progress = (downloaded * 100 / total).toInt()
                            nm.notify(100, buildProgressNotification(context, progress))
                        }
                    }
                }
            }

            nm.cancel(100)
            Log.i(TAG, "下载完成: ${downloadFile.absolutePath}")

            // 触发安装
            installApk(context, downloadFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            Result.failure(e)
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun buildProgressNotification(context: Context, progress: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("正在下载更新…")
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    /** 版本号比较：按 "." 分割后逐段比较。返回 >0 表示 v1 > v2。 */
    private fun compareVersion(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }
}
