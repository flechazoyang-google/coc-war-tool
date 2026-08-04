package com.cocwar.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WarJsonParser 解析层单元测试（与 docs/RULES.md §4.11 对齐）。
 *
 * 覆盖进攻顺序规范化：`attack_order` 缺失或 ≤0 的记录按原始出现顺序重编号，
 * 不再折叠为 order=0 被去重合并；同时确认原有去重逻辑仍然生效。
 */
class WarJsonParserTest {

    private fun parse(json: String) = WarJsonParser.parse(
        json,
        eventType = "war"
    ) as WarJsonParser.ParseResult.Success

    // === 进攻顺序规范化 ===

    /** 全部缺 attack_order：按出现顺序编号 1,2,3。 */
    @Test
    fun `missing orders are renumbered in order`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":6,"attacks":[
                {"destruction_percentage":100},
                {"destruction_percentage":80},
                {"destruction_percentage":60}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        assertEquals(listOf(1, 2, 3), attacks.map { it.attackOrder })
        assertEquals(listOf(100, 80, 60), attacks.map { it.destructionPercentage })
    }

    /** 混合：有合法 order 的保留，缺失的按序补到空位。 */
    @Test
    fun `mixed orders keep explicit ones and fill gaps`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":3,"attacks":[
                {"attack_order":2,"destruction_percentage":100},
                {"destruction_percentage":40}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        // 第一条保留 order=2；第二条缺 order → 按序分配 1（跳过已占用的 2）
        assertEquals(2, attacks.size)
        assertEquals(setOf(1, 2), attacks.map { it.attackOrder }.toSet())
        assertEquals(100, attacks.first { it.attackOrder == 2 }.destructionPercentage)
        assertEquals(40, attacks.first { it.attackOrder == 1 }.destructionPercentage)
    }

    /** 显式 order=0 与缺失等价，同样重编号。 */
    @Test
    fun `explicit zero order is treated as missing`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":3,"attacks":[
                {"attack_order":0,"destruction_percentage":90},
                {"attack_order":0,"destruction_percentage":70}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        assertEquals(listOf(1, 2), attacks.map { it.attackOrder })
    }

    /** 同 order 重复记录仍按规则去重：只保留摧毁率最高的一条。 */
    @Test
    fun `duplicate order keeps highest destruction`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":3,"attacks":[
                {"attack_order":1,"destruction_percentage":50},
                {"attack_order":1,"destruction_percentage":100}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        assertEquals(1, attacks.count { it.attackOrder == 1 })
        assertEquals(100, attacks.first { it.attackOrder == 1 }.destructionPercentage)
    }

    /** 规范化后不足槽位仍补占位（部落战 2 槽）。 */
    @Test
    fun `slots are still padded after normalization`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":3,"attacks":[
                {"destruction_percentage":100}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        assertEquals(2, attacks.size)
        assertEquals(100, attacks.first { it.attackOrder == 1 }.destructionPercentage)
        assertEquals(0, attacks.first { it.attackOrder == 2 }.destructionPercentage)
    }

    /** 缺失 order 的 JSON 不再被折叠：2 条不同摧毁率记录全部保留。 */
    @Test
    fun `no folding for missing order`() {
        val json = """
            {"members":[{"player_name":"A","total_stars":0,"attacks":[
                {"destruction_percentage":30},
                {"destruction_percentage":60}
            ]}]}
        """.trimIndent()
        val parsed = parse(json)
        val attacks = parsed.data.members.single().attacks
        assertEquals(2, attacks.size)
        assertTrue(attacks.any { it.destructionPercentage == 30 })
        assertTrue(attacks.any { it.destructionPercentage == 60 })
    }
}
