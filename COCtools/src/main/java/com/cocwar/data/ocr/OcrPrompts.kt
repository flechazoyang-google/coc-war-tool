package com.cocwar.data.ocr

/**
 * 识图提示词常量（与 scripts/doubao-ocr.mjs 的 DEFAULT_PROMPT 对齐，
 * 经真实数据验证：数值 100%、名字模糊 95.6%，见 docs/DOUBAO_OCR_VALIDATION.md §3）。
 * 输出格式与 RULES §4.15 单事件 CSV 完全一致：成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率。
 */
object OcrPrompts {

    const val DEFAULT = """你是一个部落冲突战报数据录入助手。请识别图片中的部落战/联赛战报表格，并严格按以下 CSV 格式输出，不要输出任何其他文字、解释、Markdown 代码围栏或前后缀：

成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率

要求：
1. 第一行必须是表头，之后每行一个成员，按图片中的序号排列；
2. "排名"为图中序号，从 1 开始；
3. "总星数"为该成员本场进攻获得的总星数（部落战两次进攻合计 0-6，联赛一次进攻 0-3）；
4. 摧毁率为整数百分比，去掉 % 符号（如 87 表示 87%），未进攻填 0；
5. 联赛战报的"进攻2摧毁率"列留空；
6. 成员名必须与图中写法一致，禁止改写、加前后缀或省略（若下方附有在册成员名单，按名单匹配规则执行）；
7. 图片中没有成员数据时，只输出表头行；
8. 只输出 CSV 文本本身。"""

    /**
     * 构建带花名册的识别提示词：把在册成员名单注入 prompt，让模型在源头
     * 纠正名字错字（形近/遮挡导致的识别偏差），减少 OCR 错名流入导入链路。
     * 名单为空时返回 [DEFAULT]（无名单匹配规则段）。
     */
    fun buildPrompt(rosterNames: List<String>): String {
        val names = rosterNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return DEFAULT
        return DEFAULT + """

部落在册成员名单（共 ${names.size} 人，每行一个）：
${names.joinToString("\n")}

名单匹配规则：
1. 图中成员名与名单中某名字高度相似（仅个别字形近、错字或被遮挡）时，输出名单中的写法；
2. 图中名字与名单中任何成员都不相似时，按图中写法原样输出，禁止臆造名单中不存在的名字。"""
    }
}
