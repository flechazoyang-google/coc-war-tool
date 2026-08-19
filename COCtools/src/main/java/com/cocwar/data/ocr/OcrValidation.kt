package com.cocwar.data.ocr

import com.cocwar.data.csv.CsvCodec

/**
 * 识别 CSV 合法性校验（纯函数）：识别结果中的异常数值（星数超范围、摧毁率非法）
 * 提示用户人工核对——验证发现单屏识别偶发数值误差（如星数 6→12），见
 * docs/DOUBAO_OCR_VALIDATION.md §3。
 */
object OcrValidation {

    /** 单行问题描述。 */
    data class RowIssue(
        val rank: Int?,
        val name: String,
        val field: String,
        val value: String,
        val message: String
    )

    /**
     * 校验识别 CSV 的数值合法性。
     * - 总星数必须为 0..6 整数（部落战两次进攻合计上限）；
     * - 摧毁率必须为 0..100 整数；
     * - 表头行自动跳过；无法解析的行不报（由 CsvImporter 处理）。
     */
    fun validate(csvText: String): List<RowIssue> {
        if (csvText.isBlank()) return emptyList()
        val rows = CsvCodec.parse(csvText.removePrefix(CsvCodec.BOM))
            .filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        // 表头识别：除名字列外无任何数字列即视为表头（与 CsvImporter.isHeaderRow 同口径）
        val dataRows = if (rows.first().drop(1).none { cell ->
                cell.trim().removeSuffix("%").trim().toIntOrNull() != null
            }) rows.drop(1) else rows

        val issues = mutableListOf<RowIssue>()
        dataRows.forEach { cells ->
            val name = cells.getOrNull(0)?.trim().orEmpty()
            val rank = cells.getOrNull(1)?.trim()?.toIntOrNull()
            val stars = cells.getOrNull(2)?.trim()
            val d1 = cells.getOrNull(3)?.trim()
            val d2 = cells.getOrNull(4)?.trim()

            val starInt = stars?.toIntOrNull()
            if (stars != null && starInt != null && starInt !in 0..6) {
                issues += RowIssue(rank, name, "总星数", stars, "总星数超出 0-6 范围，请人工核对")
            } else if (stars != null && starInt == null && stars.isNotEmpty()) {
                issues += RowIssue(rank, name, "总星数", stars, "总星数不是数字，请人工核对")
            }

            listOf("进攻1摧毁率" to d1, "进攻2摧毁率" to d2).forEach { (field, raw) ->
                if (raw.isNullOrEmpty()) return@forEach
                val v = raw.removeSuffix("%").trim().toIntOrNull()
                if (v == null) {
                    issues += RowIssue(rank, name, field, raw, "$field 不是数字，请人工核对")
                } else if (v !in 0..100) {
                    issues += RowIssue(rank, name, field, raw, "$field 超出 0-100 范围，请人工核对")
                }
            }
        }
        return issues
    }
}
