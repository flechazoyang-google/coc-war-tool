package com.cocwar.ui.util

import com.cocwar.data.db.WarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Labels 名称解析与战报列表排序纯函数单元测试（与 docs/RULES.md 3.x 对齐）：
 * - parseWarSeqFromName：部落战 CC 场次序号解析边界
 * - compareWarEventsBySeq：部落战视图「年月倒序 + CC 升序、无法解析垫底」
 * - compareLeagueRound：联赛组内「名称轮次优先 → eventRound 回退 → 垫底」
 */
class LabelsTest {

    private fun event(
        name: String,
        round: Int = 0,
        createdAt: Long = 0L
    ): WarEventEntity = WarEventEntity(
        eventId = name,
        eventName = name,
        eventType = if (name.startsWith("1") && name.length >= 7) "league" else "war",
        eventRound = round,
        clanTotalStars = 0,
        clanTotalDestruction = "0%",
        isSample = false,
        createdAt = createdAt
    )

    // === parseWarSeqFromName ===

    @Test
    fun parseWarSeq_validName_returnsCc() {
        assertEquals(1, parseWarSeqFromName("0260801"))
        assertEquals(10, parseWarSeqFromName("0260810"))
        assertEquals(99, parseWarSeqFromName("0260899"))
    }

    @Test
    fun parseWarSeq_leagueName_returnsNull() {
        assertNull(parseWarSeqFromName("1260801"))
    }

    @Test
    fun parseWarSeq_nonStandardOrMalformed_returnsNull() {
        assertNull(parseWarSeqFromName("示例·30人部落战"))
        assertNull(parseWarSeqFromName("1212战报01"))
        assertNull(parseWarSeqFromName("0250"))            // 长度不足
        assertNull(parseWarSeqFromName("026081A"))         // 含非数字
        assertNull(parseWarSeqFromName("0261301"))         // 月份 13 非法
    }

    @Test
    fun parseWarSeq_outOfRangeCc_returnsNull() {
        assertNull(parseWarSeqFromName("0260800"))  // CC=00 异常数据
    }

    // === compareWarEventsBySeq ===

    @Test
    fun compareWarEvents_yearDescMonthDescSeqAsc() {
        val e1 = event("0260802")   // 26年8月 第2场
        val e2 = event("0260801")   // 26年8月 第1场
        val e3 = event("0260703")   // 26年7月 第3场
        val e4 = event("0250801")   // 25年8月 第1场
        val sorted = listOf(e1, e4, e3, e2).sortedWith(compareWarEventsBySeq())
        assertEquals(listOf(e2, e1, e3, e4), sorted)
    }

    @Test
    fun compareWarEvents_unparsable_goesLast() {
        val e1 = event("0260801")
        val bad = event("示例·30人部落战")
        val sorted = listOf(bad, e1).sortedWith(compareWarEventsBySeq())
        assertEquals(listOf(e1, bad), sorted)
    }

    // === compareLeagueRound ===

    @Test
    fun compareLeagueRound_nameRoundPriority() {
        // 名称轮次优先于 eventRound 字段（与列表页展示口径一致）
        val r1 = event("1260801", round = 7)  // 名称第1轮，字段 7
        val r3 = event("1260803", round = 1)  // 名称第3轮，字段 1
        val sorted = listOf(r3, r1).sortedWith(compareLeagueRound())
        assertEquals(listOf(r1, r3), sorted)
    }

    @Test
    fun compareLeagueRound_fallbackToEventRound() {
        // 名称无法解析（非标准名）→ 回退 eventRound
        val fallback5 = event("示例·15人联赛（第3轮）", round = 5)
        val fallback2 = event("示例·15人联赛（第3轮）", round = 2)
        val sorted = listOf(fallback5, fallback2).sortedWith(compareLeagueRound())
        assertEquals(listOf(fallback2, fallback5), sorted)
    }

    @Test
    fun compareLeagueRound_invalidRound_goesLast() {
        val ok = event("1260801")
        val invalid = event("示例·15人联赛（第3轮）", round = 0)  // 名称与字段均无法解析
        val sorted = listOf(invalid, ok).sortedWith(compareLeagueRound())
        assertEquals(listOf(ok, invalid), sorted)
    }
}
