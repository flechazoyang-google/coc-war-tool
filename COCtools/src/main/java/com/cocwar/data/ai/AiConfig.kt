package com.cocwar.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * AI 模型提供商预设配置。
 */
data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val description: String
)

val AI_PROVIDERS = listOf(
    AiProvider(
        id = "openai",
        name = "OpenAI (GPT-4o)",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        description = "全球最强的视觉模型，推荐"
    ),
    AiProvider(
        id = "qwen",
        name = "通义千问 (Qwen-VL)",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-vl-max",
        description = "阿里云视觉模型，国内访问快"
    ),
    AiProvider(
        id = "glm",
        name = "智谱 (GLM-4V)",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4v-plus",
        description = "智谱 AI 视觉模型"
    ),
    AiProvider(
        id = "custom",
        name = "自定义端点",
        baseUrl = "",
        defaultModel = "",
        description = "任意 OpenAI 兼容 API 地址"
    )
)

/**
 * 用户配置的 AI 连接参数。
 */
data class AiConfig(
    val providerId: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = ""
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

/**
 * 使用 EncryptedSharedPreferences 安全存储 AI 配置。
 */
object AiConfigStore {

    private const val PREFS_NAME = "cocwar_ai_config"
    private const val KEY_PROVIDER = "provider_id"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_API_KEY = "api_key"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(context: Context): AiConfig {
        val prefs = getPrefs(context)
        return AiConfig(
            providerId = prefs.getString(KEY_PROVIDER, "") ?: "",
            baseUrl = prefs.getString(KEY_BASE_URL, "") ?: "",
            model = prefs.getString(KEY_MODEL, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        )
    }

    fun save(context: Context, config: AiConfig) {
        getPrefs(context).edit()
            .putString(KEY_PROVIDER, config.providerId)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_API_KEY, config.apiKey)
            .apply()
    }

    /** 选择一个预设提供商后，填充默认 baseUrl 和 model。 */
    fun applyPreset(providerId: String): AiConfig {
        val preset = AI_PROVIDERS.find { it.id == providerId }
        return AiConfig(
            providerId = providerId,
            baseUrl = preset?.baseUrl ?: "",
            model = preset?.defaultModel ?: ""
        )
    }
}
