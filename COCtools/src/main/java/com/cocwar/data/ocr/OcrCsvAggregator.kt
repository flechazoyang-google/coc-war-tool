package com.cocwar.data.ocr

import com.cocwar.data.csv.CsvCodec

/**
 * 多屏识别结果聚合（纯函数）：把每屏识别出的 CSV 按「排名」合并为一条 CSV。
 *
 * 方法论与 scripts/ 验证脚本一致（45 人真实战报实测数值 100%）：
 * - 名字取众数（并列取首屏出现序）；
 * - 总星数/摧毁率取非零值，多个非零冲突取最后一屏的非零值；
 * - 空屏跳过；全部空返回空串（调用方据此判定失败）。
 */
object OcrCsvAggregator {

    private const val HEADER = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率"

    /** 一行识别数据（已数值化）。 */
    data class Row(
        val rank: Int,
        val name: String,
        val stars: Int,
        val d1: Int,
        val d2: Int
    )

    /**
     * 聚合多屏 CSV 为一条 5 列 CSV（含表头）。空输入或全部无数据返回空串。
     */
    fun aggregate(perScreenCsv: List<String>): String {
        val rows = perScreenCsv.flatMap { parseRows(it) }
        if (rows.isEmpty()) return ""

        val byRank = LinkedHashMap<Int, MutableList<Row>>()
        rows.forEach { r -> byRank.getOrPut(r.rank) { mutableListOf() }.add(r) }
        if (byRank.isEmpty()) return ""

        val lines = mutableListOf(HEADER)
        byRank.toSortedMap().forEach { (rank, rs) ->
            val name = modeName(rs.map { it.name })
            val stars = lastNonZero(rs.map { it.stars })
            val d1 = lastNonZero(rs.map { it.d1 })
            val d2 = lastNonZero(rs.map { it.d2 })
            lines.add(CsvCodec.row(listOf(name, rank.toString(), stars.toString(), d1.toString(), d2.toString())))
        }
        return lines.joinToString("\n")
    }

    /** 单屏 CSV -> 数值化行；表头自动跳过，无法解析排名的行丢弃。 */
    private fun parseRows(csvText: String): List<Row> {
        if (csvText.isBlank()) return emptyList()
        val rows = CsvCodec.parse(csvText.removePrefix(CsvCodec.BOM))
            .filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        // 表头识别：除名字列外无任何数字列即视为表头（与 CsvImporter.isHeaderRow 同口径）
        val dataRows = if (rows.first().drop(1).none { cell ->
                cell.trim().removeSuffix("%").trim().toIntOrNull() != null
            }) rows.drop(1) else rows

        return dataRows.mapNotNull { cells ->
            val name = cells.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val rank = cells.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            Row(
                rank = rank,
                name = name,
                stars = cells.getOrNull(2).asciiPercent()?.coerceAtLeast(0) ?: 0,
                d1 = cells.getOrNull(3).asciiPercent()?.coerceAtLeast(0) ?: 0,
                d2 = cells.getOrNull(4).asciiPercent()?.coerceAtLeast(0) ?: 0
            )
        }
    }

    /** 解析 100 / 100% ；无法解析返回 null。 */
    private fun String?.asciiPercent(): Int? =
        this?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()

    /** 众数（并列取首屏出现序，靠 LinkedHashMap 插入序保证）。 */
    private fun modeName(names: List<String>): String {
        val counts = LinkedHashMap<String, Int>()
        names.forEach { raw ->
            val n = raw.trim()
            if (n.isNotEmpty()) counts[n] = counts.getOrDefault(n, 0) + 1
        }
        if (counts.isEmpty()) return ""
        return counts.maxByOrNull { it.value }?.key ?: ""
    }

    /** 非零优先：返回最后一个非零值；无非零则返回 0。越界值原样保留，交由下游校验。 */
    private fun lastNonZero(values: List<Int>): Int {
        var last = 0
        for (v in values) if (v != 0) last = v
        return last
    }
}
