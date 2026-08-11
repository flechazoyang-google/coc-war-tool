package com.cocwar.data.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * 轻量级 WebDAV 客户端，基于 HttpURLConnection。
 * 主备份文件统一放在 baseUrl 下的 coc_backup/ 子目录中；
 * 冲突归档放在 coc_backup/archives/ 子目录。
 */
class WebDavClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
    /** 连接工厂：默认用 URL.openConnection()；测试可注入 fake 以做协议级验证。 */
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    }
) {
    /** 规范化后的 baseUrl（去除尾部斜杠，避免拼接出双斜杠 URL）。 */
    private val normalizedUrl: String = baseUrl.trimEnd('/')

    /** 文件所在子目录名 */
    companion object {
        const val BACKUP_DIR = "coc_backup"
        const val BACKUP_FILE = "coc_war_backup.json"
        const val ARCHIVES_DIR = "coc_backup/archives"
    }

    /** Basic Auth 头 */
    private val authHeader: String
        get() {
            val creds = "$username:$password"
            return "Basic " + Base64.getEncoder().encodeToString(creds.toByteArray(Charsets.UTF_8))
        }

    /** 主备份文件的完整路径 */
    private val fileUrl: String get() = "$normalizedUrl/$BACKUP_DIR/$BACKUP_FILE"

    /** 归档文件完整路径（带时间戳文件名）。 */
    fun archiveUrl(name: String): String = "$normalizedUrl/$ARCHIVES_DIR/$name"

    /** 上传备份文件到 WebDAV 服务器。先确保子目录存在。 */
    fun upload(content: String, path: String = fileUrl): Result<Unit> {
        return runCatching {
            val data = content.toByteArray(Charsets.UTF_8)
            // 先创建子目录（已存在则忽略）；归档目录独立创建
            mkcol("$normalizedUrl/$BACKUP_DIR/")
            mkcol("$normalizedUrl/$ARCHIVES_DIR/")

            val conn = connectionFactory(path).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(data.size)
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                conn.outputStream.use { it.write(data); it.flush() }
                val code = conn.responseCode
                val msg = if (code !in 200..299) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                    }.getOrDefault("")
                    "上传失败 — 目标: $path — 服务器返回 $code${
                        if (errBody.isNotBlank()) " — $errBody" else ""
                    }"
                } else null
                if (msg != null) throw Exception(msg)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** 从 WebDAV 下载备份文件。 */
    fun download(path: String = fileUrl): Result<String> {
        return runCatching {
            val conn = connectionFactory(path).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw Exception("下载失败 — 目标: $path — 服务器返回 $code（文件可能不存在）")
                }
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** 主备份文件探测结果（三态，RULES §6：无法确认时中止同步）。 */
    enum class RemoteState { EXISTS, MISSING, UNKNOWN }

    /**
     * 探测主备份文件状态。仅服务器明确返回 2xx（存在）/404（不存在）时给出确定结论；
     * 认证失败、网络错误、超时或服务器不支持 HEAD 一律返回 [RemoteState.UNKNOWN]，
     * 由调用方保守中止，避免把「不知道」当成「不存在」而覆盖有数据的远端。
     */
    fun probe(): RemoteState {
        return runCatching {
            val conn = connectionFactory(fileUrl).apply {
                requestMethod = "HEAD"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                when (conn.responseCode) {
                    in 200..299 -> RemoteState.EXISTS
                    404 -> RemoteState.MISSING
                    else -> RemoteState.UNKNOWN
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(RemoteState.UNKNOWN)
    }

    /** 删除文件（404 视为已删除，不报错）。 */
    fun delete(path: String): Result<Unit> {
        return runCatching {
            val conn = connectionFactory(path).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299 && code != 404) {
                    throw Exception("删除失败 — 目标: $path — 服务器返回 $code")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** 测试连接：用 GET 请求探测 baseUrl 是否可达且认证正确。 */
    fun testConnection(): Result<Boolean> {
        return runCatching {
            val conn = connectionFactory(normalizedUrl).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                val code = conn.responseCode
                // 严格判定：仅 2xx 算连接成功；3xx 通常是没有重定向处理的欢迎页/认证跳转
                code in 200..299
            } finally {
                conn.disconnect()
            }
        }
    }

    /** 创建目录（已存在则忽略错误）。 */
    private fun mkcol(dirUrl: String) {
        runCatching {
            val conn = connectionFactory(dirUrl).apply {
                requestMethod = "MKCOL"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }
}
