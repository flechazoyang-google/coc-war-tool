package com.cocwar.data.csv

import com.cocwar.data.model.Attack
import com.cocwar.data.model.EventBuilder
import com.cocwar.data.model.ParseResult
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.model.UNKNOWN_VALUE

/**
 * CSV 导入（RULES §4.15）。
 *
 * 单事件格式：`成员名,排名,总星数,进攻1摧毁率[,进攻2摧毁率]`
 * - 摧毁率接受 `0..100` 纯数字或带 `%`（如 `100` / `100%`），无法解析按 0；
 * - `-1` 作为「看不清」哨兵值原样保留，供导入预览提示用户确认后归 0；
 * - 缺失列补 0；
 * - 首行若无法解析出任何数字列视为表头跳过；
 * - 按 [slotCount] 填充进攻列（部落战 2 / 联赛 1）；
 * - 解析结果走 [EventBuilder] 完整链路（去重/占位/职位映射/事件统计）。
 */
object CsvImporter {

    /** 解析摧毁率：`100` / `100%` → 0..100；`-1` → -1（看不清）；无法解析 → 0。 */
    fun parseDestruction(raw: String): Int {
        val cleaned = raw.trim().removeSuffix("%").trim()
        val v = cleaned.toIntOrNull() ?: return 0
        return if (v == UNKNOWN_VALUE) UNKNOWN_VALUE else v.coerceIn(0, 100)
    }

    /** 解析总星数：`-1` → -1（看不清）；负数/非法 → 0；其余原样。 */
    fun parseStars(raw: String): Int {
        val v = raw.trim().toIntOrNull() ?: return 0
        return if (v == UNKNOWN_VALUE) UNKNOWN_VALUE else v.coerceAtLeast(0)
    }

    /** 首行是否表头：除第一列（名字）外没有任何数字列即视为表头。 */
    fun isHeaderRow(cells: List<String>): Boolean {
        val numeric = cells.drop(1).any { cell ->
            cell.trim().removeSuffix("%").trim().toIntOrNull() != null
        }
        return !numeric
    }

    /**
     * 解析单事件 CSV 为 [ParseResult]。
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
    ): ParseResult {
        // 剥离 BOM：导出文件以 \uFEFF 开头，回导时首格会带上前缀（如 \uFEFF张三），
        // 会导致花名册匹配失败与名字入库带前缀
        val rows = CsvCodec.parse(text.removePrefix(CsvCodec.BOM))
            .filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return ParseResult.Error("CSV 内容为空")

        val dataRows = if (isHeaderRow(rows.first())) rows.drop(1) else rows
        if (dataRows.isEmpty()) {
            return ParseResult.Error("CSV 没有数据行（首行被识别为表头）")
        }

        val members = dataRows.mapIndexedNotNull { index, cells ->
            val name = cells.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null
            val rank = cells.getOrNull(1)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: (index + 1)
            val stars = parseStars(cells.getOrNull(2).orEmpty())
            val attacks = (0 until slotCount).map { i ->
                Attack(
                    attackOrder = i + 1,
                    destructionPercentage = parseDestruction(cells.getOrNull(3 + i).orEmpty())
                )
            }
            EventBuilder.MemberInput(
                rank = rank,
                playerName = name,
                totalStars = stars,
                attacks = attacks
            )
        }
        if (members.isEmpty()) {
            return ParseResult.Error("CSV 未解析出任何成员")
        }

        return try {
            ParseResult.Success(
                EventBuilder.build(members, isSample, createdAt, eventType, eventRound, rosterRoles)
            )
        } catch (e: Exception) {
            ParseResult.Error("数据校验失败：${e.message}")
        }
    }
}
