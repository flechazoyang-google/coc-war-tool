package com.cocwar.data.ocr

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 识图客户端：调用 OpenAI 兼容 `chat/completions` 视觉接口（默认千问 DashScope，
 * 可配置为豆包 / SiliconFlow 等），base64 图片 → 模型响应文本。
 *
 * 网络层沿用 WebDavClient 模式：`HttpURLConnection` + 可注入 [connectionFactory]
 * 便于 JVM 单元测试 mock；错误统一映射为 [OcrException] 子类供 UI 提示。
 */
class OcrClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    /** 连接工厂：默认 URL.openConnection()；测试可注入 fake 验证协议细节。 */
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    }
) {

    /** 识别结果分类。 */
    sealed class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        /** 未配置 API Key。 */
        class NotConfigured : OcrException("未配置 API Key，请先到 设置 → 识图设置 填写")

        /** 网络层错误（连接失败等）。 */
        class Network(val detail: String, cause: Throwable? = null) :
            OcrException("网络错误：$detail", cause)

        /** 请求超时。 */
        class Timeout : OcrException("请求超时（120 秒），请重试")

        /** HTTP 非 2xx。 */
        class ApiError(val code: Int, val detail: String) :
            OcrException("API 错误（HTTP $code）：${detail.ifBlank { "无详细信息" }}")

        /** 响应非预期结构（无 content 等）。 */
        class BadResponse(val detail: String) : OcrException("响应解析失败：$detail")
    }

    /**
     * 识别图片，返回模型输出原始文本（调用方用 [OcrCsvExtractor] 提取 CSV）。
     * @param imageBase64 图片的 base64 内容（不含 data: 前缀）
     */
    suspend fun recognize(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        prompt: String = OcrPrompts.DEFAULT
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw OcrException.NotConfigured()

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = buildPayload(imageBase64, mimeType, prompt)
        val body = payload.toString().toByteArray(Charsets.UTF_8)

        try {
            val conn = connectionFactory(url).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(body.size)
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 120_000
            }
            try {
                conn.outputStream.use { it.write(body); it.flush() }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                    }.getOrDefault("")
                    throw OcrException.ApiError(code, extractApiErrorMessage(errBody))
                }
                InputStreamReader(conn.inputStream, Charsets.UTF_8).use { reader ->
                    parseContent(reader.readText())
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: OcrException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw OcrException.Timeout()
        } catch (e: IOException) {
            throw OcrException.Network(e.message ?: "连接失败", e)
        }
    }

    /** 构造 OpenAI 兼容多模态请求体（image_url + text）。 */
    private fun buildPayload(imageBase64: String, mimeType: String, prompt: String): JsonObject {
        val imageUrl = JsonObject().apply { addProperty("url", "data:$mimeType;base64,$imageBase64") }
        val imageContent = JsonObject().apply { add("image_url", imageUrl) }
        val textContent = JsonObject().apply { addProperty("type", "text"); addProperty("text", prompt) }
        val userMessage = JsonObject().apply {
            addProperty("role", "user")
            add("content", com.google.gson.JsonArray().apply {
                add(imageContent)
                add(textContent)
            })
        }
        return JsonObject().apply {
            addProperty("model", model)
            add("messages", com.google.gson.JsonArray().apply { add(userMessage) })
            addProperty("temperature", 0.1)
            addProperty("max_tokens", 4096)
        }
    }

    /** 提取模型回复文本：choices[0].message.content。 */
    private fun parseContent(body: String): String {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
        } catch (e: Exception) {
            null
        } ?: throw OcrException.BadResponse(body.take(300))
    }

    /** 从 OpenAI 兼容错误体提取 message 字段。 */
    private fun extractApiErrorMessage(body: String): String {
        return try {
            JsonParser.parseString(body).asJsonObject
                ?.getAsJsonObject("error")?.get("message")?.asString.orEmpty()
        } catch (e: Exception) {
            body.take(300)
        }
    }
}
