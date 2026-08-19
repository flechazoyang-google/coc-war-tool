package com.cocwar.ui.settings

import androidx.lifecycle.ViewModel
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 识图设置 ViewModel：API Key（SecurePrefs 加密）与 BaseURL/模型（普通 prefs）。
 */
class OcrSettingsViewModel(
    @Suppress("UNUSED_PARAMETER") private val repo: WarRepository,
    private val config: OcrConfig
) : ViewModel() {

    data class OcrUiState(
        val apiKey: String = "",
        val baseUrl: String = "",
        val model: String = "",
        val isSecureAvailable: Boolean = true,
        val isConfigured: Boolean = false
    )

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state

    init {
        loadConfig()
    }

    private fun loadConfig() {
        _state.value = OcrUiState(
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            isSecureAvailable = config.isSecureStorageAvailable,
            isConfigured = config.isConfigured
        )
    }

    fun onApiKeyChange(value: String) {
        _state.value = _state.value.copy(apiKey = value)
    }

    fun onBaseUrlChange(value: String) {
        _state.value = _state.value.copy(baseUrl = value)
    }

    fun onModelChange(value: String) {
        _state.value = _state.value.copy(model = value)
    }

    /**
     * 保存配置：API Key 经 SecurePrefs 加密落盘（加密失败拒绝明文），
     * BaseURL/模型写普通 prefs。@return 是否保存成功（KeyStore 不可用或加密失败返回 false）。
     */
    fun save(): Boolean {
        val cur = _state.value
        config.baseUrl = cur.baseUrl
        config.model = cur.model
        if (cur.apiKey.trim() != config.apiKey) {
            config.apiKey = cur.apiKey.trim()
        }
        val savedOk = cur.apiKey.isBlank() || config.apiKey == cur.apiKey.trim()
        _state.value = _state.value.copy(isConfigured = config.isConfigured)
        return savedOk
    }
}
