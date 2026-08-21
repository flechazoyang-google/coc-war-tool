package com.cocwar.data.ocr

/**
 * 识图服务商预设：一键切换 BaseURL + 模型。
 *
 * 全部走 OpenAI 兼容 /chat/completions 协议（OcrClient），仅端点与模型名不同；
 * 百炼为阿里云 DashScope compatible-mode，通义千问 VL 视觉模型。
 */
data class OcrProviderPreset(
    val id: String,
    /** 中文展示名（设置页分段选择器）。 */
    val name: String,
    val baseUrl: String,
    val model: String,
    /** API Key 输入框的 label 文案。 */
    val keyLabel: String
)

object OcrProviders {

    val AGNES = OcrProviderPreset(
        id = "agnes",
        name = "agnes-ai（默认）",
        baseUrl = OcrConfig.DEFAULT_BASE_URL,
        model = OcrConfig.DEFAULT_MODEL,
        keyLabel = "agnes-ai API Key"
    )

    val BAILIAN = OcrProviderPreset(
        id = "bailian",
        name = "百炼（阿里云）",
        baseUrl = OcrConfig.BAILIAN_BASE_URL,
        model = OcrConfig.BAILIAN_MODEL,
        keyLabel = "百炼 / DashScope API Key"
    )

    val CUSTOM = OcrProviderPreset(
        id = "custom",
        name = "自定义",
        baseUrl = "",
        model = "",
        keyLabel = "OpenAI 兼容 API Key"
    )

    val ALL: List<OcrProviderPreset> = listOf(AGNES, BAILIAN, CUSTOM)

    /** 根据当前 baseUrl+model 反推选中的预设；不匹配返回 [CUSTOM]。 */
    fun match(baseUrl: String, model: String): OcrProviderPreset =
        ALL.firstOrNull { it.id != CUSTOM.id && it.baseUrl == baseUrl && it.model == model } ?: CUSTOM

    /** 预设对应的索引（供分段选择器回显）。 */
    fun indexOf(preset: OcrProviderPreset): Int = ALL.indexOf(preset)
}
