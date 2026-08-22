package com.cocwar.data.ocr

import android.content.Context
import android.content.SharedPreferences
import com.cocwar.data.sync.SecurePrefs

/**
 * 识图（AI OCR）配置存储。
 *
 * API Key 经 [SecurePrefs]（AndroidKeyStore + AES/GCM）加密后落盘，避免明文被
 * Android Auto Backup 上传云端；BaseURL / 模型名非敏感，存普通 SharedPreferences。
 * 每个服务商独立保存 API Key，切换不丢失。
 * 默认指向 agnes-ai 端点（agnes-2.5-pro，45 人真实战报实测达标，见 docs/DOUBAO_OCR_VALIDATION.md）；
 * BaseURL / 模型可改，设置页提供「百炼（阿里云）/ 豆包（火山引擎）/ agnes-ai / 自定义」一键切换，也可手填 SiliconFlow 等。
 * 百炼走阿里云 DashScope compatible-mode 端点。
 */
class OcrConfig(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_ocr_prefs", Context.MODE_PRIVATE)

    /** API Key 密文存储（普通 prefs，值为 [SecurePrefs] 的 AES/GCM 密文）。 */
    private val securePrefs: SharedPreferences =
        appContext.getSharedPreferences("coc_ocr_secure_prefs", Context.MODE_PRIVATE)

    init {
        migrateLegacyKey()
    }

    /** 加密存储是否可用（false = Key 无法安全保存，UI 应提示用户）。 */
    val isSecureStorageAvailable: Boolean
        get() = SecurePrefs.isKeystoreAvailable()

    /** 读取指定服务商的 API Key（空 = 未配置）。 */
    fun apiKeyFor(providerId: String): String {
        val cipher = securePrefs.getString(keyForProvider(providerId), null) ?: return ""
        return SecurePrefs.decrypt(cipher) ?: ""
    }

    /** 写入指定服务商的 API Key（空值则删除）。 */
    fun setApiKey(providerId: String, value: String) {
        val key = keyForProvider(providerId)
        if (value.isBlank()) {
            securePrefs.edit().remove(key).apply()
            return
        }
        val encrypted = SecurePrefs.encrypt(value) ?: return
        securePrefs.edit().putString(key, encrypted).apply()
    }

    /** 指定服务商是否已配置 API Key。 */
    fun isConfiguredFor(providerId: String): Boolean = apiKeyFor(providerId).isNotBlank()

    /** 返回指定服务商 API Key 的掩码展示串（如 "ark-••••••7f3a"），未配置返回空。 */
    fun maskedKeyFor(providerId: String): String {
        val raw = apiKeyFor(providerId)
        if (raw.isBlank()) return ""
        if (raw.length <= 8) return "••••••"
        return raw.take(4) + "••••••" + raw.takeLast(4)
    }

    /**
     * 当前激活服务商的 API Key（由 baseUrl + model 反查 provider）。
     * 供 OcrBatchService / ImportViewModel 等消费方直接读取。
     */
    var apiKey: String
        get() = apiKeyFor(OcrProviders.match(baseUrl, model).id)
        set(value) = setApiKey(OcrProviders.match(baseUrl, model).id, value)

    /** OpenAI 兼容端点 BaseURL（默认 agnes-ai）。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    /** 视觉模型名（默认 agnes-2.5-pro）。 */
    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** 当前激活服务商是否已配置 API Key。 */
    val isConfigured: Boolean
        get() = isConfiguredFor(OcrProviders.match(baseUrl, model).id)

    private fun keyForProvider(providerId: String) = "ocr_api_key_$providerId"

    /**
     * 旧版单 Key 迁移：将 "ocr_api_key" 密文迁移到 "ocr_api_key_agnes"。
     * 幂等——旧 key 不存在时直接跳过。
     */
    private fun migrateLegacyKey() {
        val legacyCipher = securePrefs.getString(KEY_API_KEY, null) ?: return
        val legacyPlain = SecurePrefs.decrypt(legacyCipher)
        securePrefs.edit().remove(KEY_API_KEY).apply()
        if (!legacyPlain.isNullOrBlank()) {
            setApiKey("agnes", legacyPlain)
        }
    }

    companion object {
        /** agnes-ai OpenAI 兼容端点。 */
        const val DEFAULT_BASE_URL = "https://api.agnes-ai.cn/v1"

        /** agnes 视觉模型（45 人真实战报实测：数值 100%、名字模糊 93.3%）。 */
        const val DEFAULT_MODEL = "agnes-2.5-pro"

        /** 阿里云百炼（DashScope） OpenAI 兼容端点。 */
        const val BAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

        /** 百炼通义千问 VL 视觉模型（qwen-vl-max，战报截图识别旗舰档）。 */
        const val BAILIAN_MODEL = "qwen-vl-max"

        /** 火山引擎 ARK（豆包） OpenAI 兼容端点。 */
        const val DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

        /** 豆包视觉旗舰模型（doubao-seed-2.1-pro，支持图片/视频理解）。 */
        const val DOUBAO_MODEL = "doubao-seed-2-1-pro-260628"

        private const val KEY_API_KEY = "ocr_api_key"
        private const val KEY_BASE_URL = "ocr_base_url"
        private const val KEY_MODEL = "ocr_model"
    }
}
