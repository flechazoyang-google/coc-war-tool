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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gitee 仓库信息（硬编码，发布时无需改动）。
 */
private const val GITEE_OWNER = "yang-genhao"
private const val GITEE_REPO = "coc-war-tool"
// 拉取 release 列表（含 prerelease 字段，供「加入测试计划」筛选），而非 /latest（该端点天然排除预览版）
private const val GITEE_API = "https://gitee.com/api/v5/repos/$GITEE_OWNER/$GITEE_REPO/releases?per_page=100"

data class UpdateInfo(
    val version: String,       // 如 "1.2"
    val tagName: String,       // 如 "v1.2"
    val body: String,          // 更新说明
    val apkUrl: String,        // APK 下载地址
    val fileSize: Long = 0,    // 文件大小（字节）
    val isPrerelease: Boolean = false   // 是否为预览版（prerelease）
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CHANNEL_ID = "update_download"
    private const val USER_AGENT = "COCWarTool-UpdateChecker"

    private val gson = Gson()

    /**
     * 检查 Gitee releases 是否有更新。
     * @param includePrerelease 是否纳入预览版（加入测试计划）：true 时预览版也参与「最新」判定。
     * @return UpdateInfo 如果有新版本，null 表示已是最新或检查失败。
     */
    suspend fun check(context: Context, includePrerelease: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(GITEE_API).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }

            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("API 返回 $code"))
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = JsonParser.parseString(body).asJsonArray

                val target = selectTargetRelease(releases, includePrerelease)
                    ?: return@withContext Result.success(null)  // 无可选 release（如全是预览版且未加入计划）
                val info = releaseToUpdateInfo(target)
                    ?: return@withContext Result.failure(Exception("未找到 APK 下载地址"))

                val currentVersion = BuildConfig.VERSION_NAME
                Log.d(TAG, "当前: $currentVersion, 最新: ${info.version}${if (info.isPrerelease) " (预览版)" else ""}")

                if (compareVersion(info.version, currentVersion) > 0) {
                    Result.success(info)
                } else {
                    Result.success(null) // 已是最新
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Result.failure(e)
        }
    }

    /**
     * 从 gitee releases 列表选出目标 release（纯函数，可单测）：
     * - includePrerelease=true：取版本号最新（含预览版）；
     * - includePrerelease=false：过滤掉 prerelease=true 后取最新正式版；
     * 无任何候选（列表为空 / 全是预览版且未加入计划）返回 null。
     * 版本号按 [compareVersion] 语义比较（正式版 > 预览版）。
     */
    fun selectTargetRelease(releases: JsonArray, includePrerelease: Boolean): JsonObject? {
        val candidates = releases.asSequence()
            .filter { it.isJsonObject }
            .map { it.asJsonObject }
            .filter { obj ->
                val pre = obj.get("prerelease")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                includePrerelease || !pre
            }
            .filter { obj ->
                obj.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true
            }
            .toList()
        return candidates.maxWithOrNull { a, b ->
            compareVersion(a.get("tag_name").asString, b.get("tag_name").asString)
        }
    }

    /**
     * 将单个 gitee release JSON 转换为 [UpdateInfo]（纯函数，可单测）。
     * 找不到 .apk 附件返回 null。
     */
    fun releaseToUpdateInfo(obj: JsonObject): UpdateInfo? {
        val tagName = obj.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val version = tagName.removePrefix("v").removePrefix("V")
        val desc = obj.get("body")?.takeIf { it.isJsonPrimitive }?.asString?.take(500) ?: ""
        val isPrerelease = obj.get("prerelease")?.takeIf { it.isJsonPrimitive }?.asBoolean == true

        val assets = obj.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        var apkUrl = ""
        for (asset in assets) {
            if (!asset.isJsonObject) continue
            val assetObj = asset.asJsonObject
            val name = assetObj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            if (name.endsWith(".apk")) {
                apkUrl = assetObj.get("browser_download_url")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                break
            }
        }
        if (apkUrl.isEmpty()) return null
        return UpdateInfo(version, tagName, desc, apkUrl, isPrerelease = isPrerelease)
    }

    /**
     * 下载 APK 到缓存目录，显示通知进度，完成后触发安装。
     */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)

            // 清理历史更新下载残留的 APK（安装完成后不会自动删除，会累积占满缓存）
            runCatching {
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("update_") && it.name.endsWith(".apk") }
                    ?.forEach { it.delete() }
            }

            val downloadFile = File(context.cacheDir, "update_${info.version}.apk")
            if (downloadFile.exists()) downloadFile.delete()

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext Result.failure(Exception("下载失败 HTTP $code"))
            }

            val total = connection.contentLengthLong
            var downloaded = 0L

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(downloadFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastNotifyTime = 0L
                        while (true) {
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
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
            } finally {
                connection.disconnect()
            }

            // 校验下载内容是否为有效的 APK（ZIP 格式以 "PK" 开头）。
            val magic = ByteArray(2)
            downloadFile.inputStream().use { it.read(magic) }
            if (!(magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte())) {
                downloadFile.delete()
                return@withContext Result.failure(Exception("下载内容不是有效的 APK（可能被限流，请稍后重试）"))
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
        val (nums1, pre1) = parseVersion(v1)
        val (nums2, pre2) = parseVersion(v2)
        val maxLen = maxOf(nums1.size, nums2.size)
        for (i in 0 until maxLen) {
            val a = nums1.getOrElse(i) { 0 }
            val b = nums2.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        // 数值段相等时：正式版 > 预发布版（"1.2" > "1.2-beta"）
        if (pre1 != pre2) return if (pre1) -1 else 1
        return 0
    }

    /**
     * 解析版本号为数字段列表 + 是否预发布（带 -alpha/-beta 等后缀）。
     * 如 "v1.2.3-beta" → ([1,2,3], true)；"3.2" → ([3,2], false)。
     */
    private fun parseVersion(v: String): Pair<List<Int>, Boolean> {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val match = Regex("""^(\d+(?:\.\d+)*)(.*)$""").find(cleaned) ?: return emptyList<Int>() to false
        val nums = match.groupValues[1].split(".").mapNotNull { it.toIntOrNull() }
        val isPreRelease = match.groupValues[2].isNotBlank()
        return nums to isPreRelease
    }
}
