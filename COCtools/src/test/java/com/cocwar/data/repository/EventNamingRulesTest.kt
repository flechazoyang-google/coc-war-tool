package com.cocwar.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** EventNamingRules.computeCC 纯函数测试（SAABBCC 命名 CC 段）。 */
class EventNamingRulesTest {

    // ─── 部落战：CC = 当月第 N 场 ───

    @Test
    fun `部落战-当月无场次从 1 开始`() {
        assertEquals(1, EventNamingRules.computeCC(emptyList(), "war", 0))
    }

    @Test
    fun `部落战-按合法名称计数自增`() {
        val names = listOf("0030701", "0030702")
        assertEquals(3, EventNamingRules.computeCC(names, "war", 0))
    }

    @Test
    fun `部落战-非标准名与联赛名不参与计数`() {
        val names = listOf("示例·部落战", "1030701", "00307A1")
        assertEquals(1, EventNamingRules.computeCC(names, "war", 0))
    }

    @Test
    fun `部落战-超过 99 封顶`() {
        val names = (1..99).map { "00307%02d".format(it) }
        assertEquals(99, EventNamingRules.computeCC(names, "war", 0))
    }

    // ─── 联赛：CC = C1C2（0=月初场 1=月中场） ───

    @Test
    fun `联赛-月初场空场从轮 1 开始`() {
        assertEquals(1, EventNamingRules.computeCC(emptyList(), "league", 0))
    }

    @Test
    fun `联赛-月初场续填最小空缺轮次`() {
        // 已占 01/02/03 → 下一个应为 04
        val names = listOf("1030701", "1030702", "1030703")
        assertEquals(4, EventNamingRules.computeCC(names, "league", 0))
    }

    @Test
    fun `联赛-月初场尊重调用方轮次提示`() {
        // 已占 01..04,提示轮次 7(未占)→ 07
        val names = (1..4).map { "10307%02d".format(it) }
        assertEquals(7, EventNamingRules.computeCC(names, "league", 7))
    }

    @Test
    fun `联赛-月初场已占的提示轮次回退到空缺`() {
        // 已占 01..06,提示 6(已占)→ 最小空缺 7
        val names = (1..6).map { "10307%02d".format(it) }
        assertEquals(7, EventNamingRules.computeCC(names, "league", 6))
    }

    @Test
    fun `联赛-月初场录满后开月中场`() {
        val names = (1..7).map { "10307%02d".format(it) }
        assertEquals(11, EventNamingRules.computeCC(names, "league", 0))
    }

    @Test
    fun `联赛-月中场续填并归一化计数`() {
        // 月初场 01..07 满 + 月中场 11..13 → 下一个 14
        val names = (1..7).map { "10307%02d".format(it) } +
            listOf("1030711", "1030712", "1030713")
        assertEquals(14, EventNamingRules.computeCC(names, "league", 0))
    }

    @Test
    fun `联赛-两场各 7 轮录满兜底 99`() {
        val names = (1..7).map { "10307%02d".format(it) } +
            (11..17).map { "10307$it" }
        assertEquals(99, EventNamingRules.computeCC(names, "league", 0))
    }
}
