package com.cocwar.data.csv

import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.model.AttackDto
import com.cocwar.data.model.MemberDto
import com.cocwar.data.model.WarDto
import com.cocwar.data.parser.WarJsonParser

/**
 * CSV 导入（RULES §4.15）。
 *
 * 单事件格式：`成员名,排名,总星数,进攻1摧毁率[,进攻2摧毁率]`
 * - 摧毁率接受 `0..100` 纯数字或带 `%`（如 `100` / `100%`），无法解析按 0；
 * - 缺失列补 0；
 * - 首行若无法解析出任何数字列视为表头跳过；
 * - 按 [slotCount] 填充进攻列（部落战 2 / 联赛 1）；
 * - 解析结果复用 `WarJsonParser.fromDto` 完整链路（去重/占位/职位映射/事件统计）。
 */
object CsvImporter {

    /** 解析摧毁率：`100` / `100%` → 0..100；无法解析 → 0。 */
    fun parseDestruction(raw: String): Int {
        val cleaned = raw.trim().removeSuffix("%").trim()
        return cleaned.toIntOrNull()?.coerceIn(0, 100) ?: 0
    }

    /** 首行是否表头：除第一列（名字）外没有任何数字列即视为表头。 */
    fun isHeaderRow(cells: List<String>): Boolean {
        val numeric = cells.drop(1).any { cell ->
            cell.trim().removeSuffix("%").trim().toIntOrNull() != null
        }
        return !numeric
    }

    /**
     * 解析单事件 CSV 为 [WarJsonParser.ParsedEvent]（直接走 JSON 导入同款链路）。
     *
     * @param slotCount 进攻槽位数（部落战 2 / 联赛 1）
     * @param rosterRoles 花名册职位映射（职位一律以花名册为准）
     */
    fun parse(
        text: String,
        slotCount: Int,
        isSample: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        eventType: String = EVENT_TYPE_WAR,
        eventRound: Int = 0,
        rosterRoles: Map<String, String> = emptyMap()
    ): WarJsonParser.ParseResult {
        // 剥离 BOM：导出文件以 \uFEFF 开头，回导时首格会带上前缀（如 \uFEFF张三），
        // 会导致花名册匹配失败与名字入库带前缀
        val rows = CsvCodec.parse(text.removePrefix(CsvCodec.BOM))
            .filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return WarJsonParser.ParseResult.Error("CSV 内容为空")

        val dataRows = if (isHeaderRow(rows.first())) rows.drop(1) else rows
        if (dataRows.isEmpty()) {
            return WarJsonParser.ParseResult.Error("CSV 没有数据行（首行被识别为表头）")
        }

        val members = dataRows.mapIndexedNotNull { index, cells ->
            val name = cells.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val rank = cells.getOrNull(1)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: (index + 1)
            val stars = cells.getOrNull(2)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val attacks = (0 until slotCount).map { i ->
                AttackDto(
                    attackOrder = i + 1,
                    destructionPercentage = parseDestruction(cells.getOrNull(3 + i).orEmpty())
                )
            }
            MemberDto(
                rank = rank,
                playerName = name,
                totalStars = stars,
                attacks = attacks
            )
        }
        if (members.isEmpty()) {
            return WarJsonParser.ParseResult.Error("CSV 未解析出任何成员")
        }

        return try {
            WarJsonParser.ParseResult.Success(
                WarJsonParser.fromDto(
                    WarDto(members = members), isSample, createdAt, eventType, eventRound, rosterRoles
                )
            )
        } catch (e: Exception) {
            WarJsonParser.ParseResult.Error("数据校验失败：${e.message}")
        }
    }
}
