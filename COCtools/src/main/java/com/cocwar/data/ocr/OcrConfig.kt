package com.cocwar.data.ocr

import android.content.Context
import android.content.SharedPreferences
import com.cocwar.data.sync.SecurePrefs

/**
 * 识图（AI OCR）配置存储。
 *
 * API Key 经 [SecurePrefs]（AndroidKeyStore + AES/GCM）加密后落盘，避免明文被
 * Android Auto Backup 上传云端；BaseURL / 模型名非敏感，存普通 SharedPreferences。
 * 默认指向千问 DashScope 兼容端点（与验证一致，见 docs/DOUBAO_OCR_VALIDATION.md）；
 * BaseURL / 模型可改，预留切换豆包 / SiliconFlow 等 OpenAI 兼容服务。
 */
class OcrConfig(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_ocr_prefs", Context.MODE_PRIVATE)

    /** API Key 密文存储（普通 prefs，值为 [SecurePrefs] 的 AES/GCM 密文）。 */
    private val securePrefs: SharedPreferences =
        appContext.getSharedPreferences("coc_ocr_secure_prefs", Context.MODE_PRIVATE)

    /** 加密存储是否可用（false = Key 无法安全保存，UI 应提示用户）。 */
    val isSecureStorageAvailable: Boolean
        get() = SecurePrefs.isKeystoreAvailable()

    /** API Key（空 = 未配置；解密失败/未加密时返回空，用户重输覆盖）。 */
    var apiKey: String
        get() {
            val cipher = securePrefs.getString(KEY_API_KEY, null) ?: return ""
            return SecurePrefs.decrypt(cipher) ?: ""
        }
        set(value) {
            if (value.isBlank()) {
                securePrefs.edit().remove(KEY_API_KEY).apply()
                return
            }
            // AndroidKeyStore 不可用或加密失败时拒绝保存，避免明文写入普通 prefs
            val encrypted = SecurePrefs.encrypt(value) ?: return
            securePrefs.edit().putString(KEY_API_KEY, encrypted).apply()
        }

    /** OpenAI 兼容端点 BaseURL（默认千问 DashScope）。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

    /** 视觉模型名（默认千问 omni，与验证一致）。 */
    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /** 是否已配置 API Key。 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    companion object {
        /** 千问 DashScope OpenAI 兼容端点。 */
        const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

        /** 千问多模态模型（验证实测达标，见 docs/DOUBAO_OCR_VALIDATION.md §3）。 */
        const val DEFAULT_MODEL = "qwen3.5-omni-plus"

        private const val KEY_API_KEY = "ocr_api_key"
        private const val KEY_BASE_URL = "ocr_base_url"
        private const val KEY_MODEL = "ocr_model"
    }
}
