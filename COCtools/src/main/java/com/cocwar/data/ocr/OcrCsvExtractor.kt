package com.cocwar.data.ocr

/**
 * 从模型响应文本中提取纯 CSV（与 scripts/doubao-ocr.mjs 的 extractCsv 逻辑一致）：
 * 剥离 Markdown 围栏（```csv / ```）、丢弃表头之前的解释行、去空白行。
 * 纯函数，无副作用。
 */
object OcrCsvExtractor {

    private val FENCE = Regex("```(?:csv)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    /** 表头识别：同时包含"成员名"与"排名"。 */
    private fun isHeaderLine(line: String): Boolean =
        line.contains("成员名") && line.contains("排名")

    /**
     * 提取纯 CSV 文本。
     * - 有表头：从表头行开始保留（含表头与全部数据行），丢弃其前解释文字；
     * - 无表头：返回全部非空行（交由 CsvImporter 判定是否可解析）；
     * - 空输入返回空串。
     */
    fun extract(content: String): String {
        var text = content.trim()
        if (text.isEmpty()) return ""

        FENCE.find(text)?.let { text = it.groupValues[1].trim() }

        val lines = text.split('\n').map { it.trimEnd('\r') }
        val headerIdx = lines.indexOfFirst { isHeaderLine(it) }
        val kept = if (headerIdx >= 0) lines.drop(headerIdx) else lines
        return kept.filter { it.isNotBlank() }.joinToString("\n").trim()
    }
}
