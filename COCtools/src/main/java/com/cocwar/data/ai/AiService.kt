package com.cocwar.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.cocwar.data.model.WarDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * AI 视觉识别服务。
 * 使用 OpenAI 兼容的 Chat Completions API，支持多图输入。
 */
object AiService {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 调用视觉大模型识别截图中的战报数据。
     *
     * @param images 截图列表（按从上到下顺序）
     * @param config AI 配置（baseUrl、model、apiKey）
     * @return Result<WarDto> 成功时包含解析后的数据
     */
    suspend fun recognizeScreenshots(
        images: List<Bitmap>,
        config: AiConfig
    ): Result<WarDto> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("AI 配置不完整，请先设置 API Key"))
        }
        if (images.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("没有截图可供识别"))
        }

        try {
            // 压缩图片并编码为 base64
            val base64Images = images.map { bitmapToBase64Url(it) }

            // 构建请求体
            val requestBody = buildRequest(base64Images, config.model)

            // 发送请求
            val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JsonParser.parseString(body).asJsonObject
                        .getAsJsonObject("error")
                        ?.get("message")?.asString ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${body.take(200)}"
                }
                return@withContext Result.failure(Exception("API 错误：$errorMsg"))
            }

            // 提取 AI 返回的 JSON
            val jsonContent = extractJson(body)
            if (jsonContent.isBlank()) {
                return@withContext Result.failure(Exception("AI 返回内容无法解析为 JSON"))
            }

            // 直接序列化为 WarDto（字段名用 @SerializedName 映射）
            val warDto = try {
                gson.fromJson(jsonContent, WarDto::class.java)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("AI 返回的 JSON 格式不匹配：${e.message}"))
            }

            if (warDto == null || warDto.members.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("AI 未识别到任何成员数据"))
            }

            Result.success(warDto)
        } catch (e: Exception) {
            Result.failure(Exception("识别失败：${e.message}", e))
        }
    }

    /**
     * 测试 AI 连接是否可用。
     */
    suspend fun testConnection(config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("配置不完整"))
        }
        try {
            val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
            val body = buildTestRequest(config.model)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("连接成功 ✅")
            } else {
                val errorMsg = try {
                    JsonParser.parseString(response.body?.string() ?: "").asJsonObject
                        .getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败：${e.message}"))
        }
    }

    // ==================== 私有方法 ====================

    /** 将 Bitmap 压缩为 JPEG 并编码为 base64 data URL。 */
    private fun bitmapToBase64Url(bitmap: Bitmap): String {
        // 压缩：最大宽度 1024px，JPEG 质量 75%
        val scaled = if (bitmap.width > 1024) {
            val ratio = 1024f / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 1024, (bitmap.height * ratio).toInt(), true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val bytes = baos.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    /** 构建包含多张图片的 Chat Completions 请求体。 */
    private fun buildRequest(base64Images: List<String>, model: String): String {
        val userContent = buildString {
            append("[")
            base64Images.forEachIndexed { i, img ->
                if (i > 0) append(",")
                append("""{"type":"image_url","image_url":{"url":"$img","detail":"high"}}""")
            }
            append(",")
            append("""{"type":"text","text":"${AiPrompts.buildUserMessage(base64Images.size)}"}""")
            append("]")
        }

        return """
        {
            "model": "$model",
            "messages": [
                {"role": "system", "content": ${gson.toJson(AiPrompts.SYSTEM_PROMPT)}},
                {"role": "user", "content": $userContent}
            ],
            "max_tokens": 4096,
            "temperature": 0.1
        }
        """.trimIndent()
    }

    /** 构建测试请求。 */
    private fun buildTestRequest(model: String): String {
        return """
        {
            "model": "$model",
            "messages": [
                {"role": "user", "content": "请回复 'OK'"}
            ],
            "max_tokens": 10
        }
        """.trimIndent()
    }

    /** 从 AI 响应中提取 JSON 内容。处理可能被 markdown 包裹的情况。 */
    private fun extractJson(responseBody: String): String {
        // 先尝试整个 body 解析
        val bodyJson = try {
            JsonParser.parseString(responseBody).asJsonObject
        } catch (_: Exception) { null }

        if (bodyJson != null) {
            // 标准 OpenAI 响应格式：choices[0].message.content
            val content = try {
                bodyJson.getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
            } catch (_: Exception) { null }

            if (content != null) {
                return stripJson(content)
            }
            // 如果 choices 不存在，可能整个 body 就是结果
            if (bodyJson.has("members")) return responseBody
        }

        // fallback：直接尝试解析整个 body
        return stripJson(responseBody)
    }

    /** 去除可能的 markdown 代码块标记。 */
    private fun stripJson(text: String): String {
        var t = text.trim()
        // 移除 ```json 和 ``` 包裹
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").trim()
            if (t.endsWith("```")) {
                t = t.dropLast(3).trim()
            }
        }
        return t
    }
}
