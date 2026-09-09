package com.cocwar.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.cocwar.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases API：只取最新正式发行版（prerelease 不会出现在 latest 中）。 */
private const val RELEASE_API_URL = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"

data class UpdateInfo(
    val version: String,
    val body: String,
    val apkUrl: String,
    val isPrerelease: Boolean = false
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CHANNEL_ID = "update_download"
    private const val USER_AGENT = "COCWarTool-UpdateChecker"

    /**
     * 从 GitHub Releases 检查更新（仓库坐标见 BuildConfig.UPDATE_REPO）。
     * 无发行版（404）视为「已是最新」；有新版本返回 UpdateInfo，否则返回 null。
     */
    suspend fun check(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASE_API_URL).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }

            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext Result.success(null)
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(Exception("GitHub 返回 $code"))
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val info = parseGitHubRelease(body) ?: return@withContext Result.success(null)

                val currentVersion = BuildConfig.VERSION_NAME
                Log.d(TAG, "当前: $currentVersion, 最新: ${info.version}${prereleaseLabel(info.version)}")

                if (compareVersion(info.version, currentVersion) > 0) {
                    Result.success(info)
                } else {
                    Result.success(null)
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
     * 解析 GitHub Releases API 的 `releases/latest` 响应（纯函数，可单测）。
     *
     * 取值：`tag_name` 去掉 `v` 前缀作为版本号；`body` 作为更新说明（截断 500 字）；
     * `assets[]` 中第一个 `.apk` 资产的 `browser_download_url` 作为下载地址。
     * 缺少 tag、缺少 APK 资产、draft/prerelease 或 JSON 非法时返回 null。
     */
    fun parseGitHubRelease(json: String): UpdateInfo? {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            return null
        }

        fun boolOrFalse(key: String): Boolean =
            root.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

        if (boolOrFalse("draft") || boolOrFalse("prerelease")) return null

        val tag = root.get("tag_name")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            .orEmpty()
        if (tag.isBlank()) return null

        val apkUrl = firstApkAssetUrl(root) ?: return null

        val body = root.get("body")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.take(500)
            .orEmpty()

        val version = tag.removePrefix("v").removePrefix("V")
        if (version.isBlank()) return null

        return UpdateInfo(
            version = version,
            body = body,
            apkUrl = apkUrl,
            isPrerelease = isPrereleaseVersion(version)
        )
    }

    /** 取 assets 中第一个 `.apk` 资产的下载地址（跳过非 APK 与缺失 url 的资产）。 */
    private fun firstApkAssetUrl(root: JsonObject): String? {
        val assets = root.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        for (asset in assets) {
            val obj = asset.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = obj.get("browser_download_url")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                .orEmpty()
            if (url.isNotBlank()) return url
        }
        return null
    }

    /**
     * 下载 APK 到缓存目录，显示通知进度，完成后触发安装。
     */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "更新下载", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)

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

            val magic = ByteArray(2)
            downloadFile.inputStream().use { it.read(magic) }
            if (!(magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte())) {
                downloadFile.delete()
                return@withContext Result.failure(Exception("下载内容不是有效的 APK（可能被限流，请稍后重试）"))
            }

            nm.cancel(100)
            Log.i(TAG, "下载完成: ${downloadFile.absolutePath}")

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

    /** 版本号比较（SemVer）：数字段逐段比较，数字相同则按 prerelease 阶段排序。
     *  alpha < beta < rc < 正式版。同阶段按序号比较（alpha.1 < alpha.2）。
     *  返回 >0 表示 v1 > v2。 */
    internal fun compareVersion(v1: String, v2: String): Int {
        val p1 = parseSemVer(v1)
        val p2 = parseSemVer(v2)
        val maxLen = maxOf(p1.nums.size, p2.nums.size)
        for (i in 0 until maxLen) {
            val a = p1.nums.getOrElse(i) { 0 }
            val b = p2.nums.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return comparePrerelease(p1, p2)
    }

    private fun comparePrerelease(v1: SemVer, v2: SemVer): Int {
        if (v1.stage == null && v2.stage == null) return 0
        if (v1.stage == null) return 1
        if (v2.stage == null) return -1
        val stageDiff = stageOrder(v1.stage) - stageOrder(v2.stage)
        if (stageDiff != 0) return stageDiff
        return v1.stageNum - v2.stageNum
    }

    private data class SemVer(val nums: List<Int>, val stage: String?, val stageNum: Int)

    private fun parseSemVer(v: String): SemVer {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val match = Regex("""^(\d+(?:\.\d+)*)(?:-(alpha|beta|rc)\.(\d+))?$""").find(cleaned)
            ?: return SemVer(emptyList(), null, 0)
        val nums = match.groupValues[1].split(".").mapNotNull { it.toIntOrNull() }
        val stage = match.groupValues[2].ifEmpty { null }
        val stageNum = match.groupValues[3].toIntOrNull() ?: 0
        return SemVer(nums, stage, stageNum)
    }

    private fun stageOrder(stage: String): Int = when (stage) {
        "alpha" -> 0
        "beta" -> 1
        "rc" -> 2
        else -> 3
    }

    /** 从版本号自动判断是否为预发布版本（含 -alpha / -beta / -rc / -preview 后缀）。 */
    fun isPrereleaseVersion(version: String): Boolean {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        return Regex("""-(alpha|beta|rc)\.\d+$""").containsMatchIn(cleaned)
            || Regex("""-preview$""").containsMatchIn(cleaned)
    }

    /** 返回 prerelease 阶段的中文标签，正式版返回空字符串。兼容旧 -preview 后缀。 */
    fun prereleaseLabel(version: String): String {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        val match = Regex("""-(alpha|beta|rc)\.\d+$""").find(cleaned)
        if (match != null) {
            return when (match.groupValues[1]) {
                "alpha" -> "（内部测试版）"
                "beta" -> "（公开测试版）"
                "rc" -> "（候选版）"
                else -> ""
            }
        }
        if (cleaned.endsWith("-preview")) return "（预览版）"
        return ""
    }
}
