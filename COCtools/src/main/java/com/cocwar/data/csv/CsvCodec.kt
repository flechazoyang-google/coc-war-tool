package com.cocwar.data.csv

/**
 * CSV 编解码（RULES §4.13）。
 * 全部 UTF-8；导出以 BOM（\uFEFF）开头保证 Excel/WPS 中文兼容；
 * 单元格含逗号/引号/换行时按 RFC 4180 用双引号包裹并双写内部引号。
 */
object CsvCodec {

    /** 带 BOM 的 UTF-8 前缀（写入文件时位于最前）。 */
    const val BOM = "\uFEFF"

    /** 转义单元格：含逗号/引号/换行时才需要包裹。 */
    fun escapeCell(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    /** 拼接一行。 */
    fun row(cells: List<String>): String = cells.joinToString(",") { escapeCell(it) }

    /**
     * 解析完整 CSV 文本（支持 RFC 4180 引号包裹、引号内换行、\r\n 行尾）。
     * 返回所有行；空行保留（调用方可过滤）。
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < len && text[i + 1] == '"') {
                            current.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        current.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    cells.add(current.toString())
                    current.setLength(0)
                }
                c == '\n' -> {
                    cells.add(current.toString())
                    current.setLength(0)
                    rows.add(cells.toList())
                    cells.clear()
                }
                c == '\r' -> { /* 与 \n 合并处理，忽略 */ }
                else -> current.append(c)
            }
            i++
        }
        // 末行（无换行结尾）或结尾空字段
        if (current.isNotEmpty() || cells.isNotEmpty() || text.endsWith(",")) {
            cells.add(current.toString())
            rows.add(cells.toList())
        }
        return rows
    }
}
