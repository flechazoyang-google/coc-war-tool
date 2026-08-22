package com.cocwar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.ocr.OcrClient
import com.cocwar.data.ocr.OcrConfig
import com.cocwar.data.ocr.OcrProviders
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 识图设置 ViewModel：每个服务商独立保存 API Key，切换不丢失；
 * 支持从 API 获取可用模型列表、搜索筛选模型、测试连接。
 */
class OcrSettingsViewModel(
    @Suppress("UNUSED_PARAMETER") private val repo: WarRepository,
    private val config: OcrConfig
) : ViewModel() {

    data class OcrUiState(
        val apiKey: String = "",
        val maskedKey: String = "",
        val baseUrl: String = "",
        val model: String = "",
        val isSecureAvailable: Boolean = true,
        val isConfigured: Boolean = false,
        val providerIndex: Int = 0,
        /** 从 API 获取的模型列表（空 = 未获取或不支持）。 */
        val fetchedModels: List<String> = emptyList(),
        /** 模型搜索关键词。 */
        val modelFilter: String = "",
        /** 是否正在加载模型列表。 */
        val isLoadingModels: Boolean = false,
        /** 加载模型时的错误信息（空 = 无错误）。 */
        val modelsError: String? = null,
        val isCustomProvider: Boolean = false,
        /** 各预设服务商的掩码 Key（用于状态指示圆点）。 */
        val providerKeys: Map<String, String> = emptyMap(),
        /** 是否正在测试连接。 */
        val isTesting: Boolean = false,
        /** 测试结果（null = 未测试，空串 = 成功，非空 = 错误信息）。 */
        val testResult: TestResult? = null
    )

    sealed class TestResult {
        data class Success(val response: String) : TestResult()
        data class Error(val message: String) : TestResult()
    }

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val baseUrl = config.baseUrl
        val model = config.model
        val provider = OcrProviders.match(baseUrl, model)
        val index = OcrProviders.indexOf(provider)
        val isCustom = provider.id == "custom"
        _state.value = OcrUiState(
            apiKey = config.apiKeyFor(provider.id),
            maskedKey = config.maskedKeyFor(provider.id),
            baseUrl = baseUrl,
            model = model,
            isSecureAvailable = config.isSecureStorageAvailable,
            isConfigured = config.isConfiguredFor(provider.id),
            providerIndex = index,
            isCustomProvider = isCustom,
            providerKeys = loadAllMaskedKeys()
        )
    }

    fun onApiKeyChange(value: String) {
        _state.value = _state.value.copy(apiKey = value, testResult = null)
    }

    fun onBaseUrlChange(value: String) {
        _state.value = _state.value.copy(baseUrl = value, testResult = null)
    }

    fun onModelChange(value: String) {
        _state.value = _state.value.copy(model = value, testResult = null)
    }

    fun onModelSelect(model: String) {
        _state.value = _state.value.copy(model = model, testResult = null)
    }

    fun onModelFilterChange(value: String) {
        _state.value = _state.value.copy(modelFilter = value)
    }

    /** 过滤后的模型列表（按搜索关键词前缀匹配）。 */
    val filteredModels: List<String>
        get() {
            val filter = _state.value.modelFilter.trim().lowercase()
            val models = _state.value.fetchedModels
            if (filter.isBlank()) return models
            return models.filter { it.lowercase().contains(filter) }
        }

    /**
     * 选择服务商预设：先保存当前服务商的 Key（防止丢失），
     * 再加载目标服务商的 Key + 默认模型。
     */
    fun onProviderSelect(index: Int) {
        val preset = OcrProviders.ALL.getOrNull(index) ?: return
        val cur = _state.value

        // 保存当前服务商的 Key（避免切换丢失）
        val curPreset = OcrProviders.ALL.getOrNull(cur.providerIndex)
        if (curPreset != null && cur.apiKey.isNotBlank()) {
            config.setApiKey(curPreset.id, cur.apiKey.trim())
        }

        val isCustom = preset.id == "custom"
        val savedKey = config.apiKeyFor(preset.id)

        _state.value = cur.copy(
            apiKey = savedKey,
            maskedKey = config.maskedKeyFor(preset.id),
            baseUrl = if (isCustom) cur.baseUrl else preset.baseUrl,
            model = if (isCustom) cur.model else preset.model,
            providerIndex = index,
            fetchedModels = emptyList(),
            modelFilter = "",
            modelsError = null,
            isCustomProvider = isCustom,
            isConfigured = config.isConfiguredFor(preset.id),
            providerKeys = loadAllMaskedKeys(),
            testResult = null
        )
    }

    /**
     * 从 API 获取可用模型列表。
     */
    fun fetchModels() {
        val cur = _state.value
        val key = cur.apiKey.ifBlank { config.apiKeyFor(OcrProviders.ALL[cur.providerIndex].id) }
        if (key.isBlank()) {
            _state.value = cur.copy(modelsError = "请先填写 API Key")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingModels = true, modelsError = null)
            val client = OcrClient(
                apiKey = key,
                baseUrl = _state.value.baseUrl,
                model = _state.value.model
            )
            try {
                val models = client.fetchModels()
                _state.value = _state.value.copy(
                    fetchedModels = models,
                    isLoadingModels = false,
                    modelFilter = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingModels = false,
                    modelsError = e.message ?: "获取模型列表失败"
                )
            }
        }
    }

    /**
     * 测试连接：验证 API Key 和模型是否可用。
     */
    fun testConnection() {
        val cur = _state.value
        val key = cur.apiKey.ifBlank { config.apiKeyFor(OcrProviders.ALL[cur.providerIndex].id) }
        if (key.isBlank()) {
            _state.value = cur.copy(testResult = TestResult.Error("请先填写 API Key"))
            return
        }
        if (cur.model.isBlank()) {
            _state.value = cur.copy(testResult = TestResult.Error("请先选择模型"))
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isTesting = true, testResult = null)
            val client = OcrClient(
                apiKey = key,
                baseUrl = cur.baseUrl,
                model = cur.model
            )
            try {
                val response = client.testConnection()
                _state.value = _state.value.copy(
                    isTesting = false,
                    testResult = TestResult.Success(response)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isTesting = false,
                    testResult = TestResult.Error(e.message ?: "连接失败")
                )
            }
        }
    }

    /**
     * 保存配置：API Key 按服务商加密落盘，BaseURL / 模型写普通 prefs。
     * @return 是否保存成功（KeyStore 不可用或加密失败返回 false）。
     */
    fun save(): Boolean {
        val cur = _state.value
        val preset = OcrProviders.ALL[cur.providerIndex]
        config.baseUrl = cur.baseUrl
        config.model = cur.model
        config.setApiKey(preset.id, cur.apiKey.trim())
        val savedOk = cur.apiKey.isBlank() || config.apiKeyFor(preset.id) == cur.apiKey.trim()
        _state.value = cur.copy(
            isConfigured = config.isConfiguredFor(preset.id),
            maskedKey = config.maskedKeyFor(preset.id),
            providerKeys = loadAllMaskedKeys()
        )
        return savedOk
    }

    private fun loadAllMaskedKeys(): Map<String, String> =
        OcrProviders.ALL.filter { it.id != "custom" }.associate {
            it.id to config.maskedKeyFor(it.id)
        }
}
