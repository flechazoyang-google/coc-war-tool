package com.cocwar.data.csv

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.parser.WarJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 编解码/导入/导出单元测试（与 docs/RULES.md §4.13-§4.16 对齐）。
 */
class CsvTest {

    // === CsvCodec ===

    @Test
    fun `escapeCell keeps plain values`() {
        assertEquals("abc", CsvCodec.escapeCell("abc"))
        assertEquals("123", CsvCodec.escapeCell("123"))
    }

    @Test
    fun `escapeCell wraps comma quote and newline`() {
        assertEquals("\"a,b\"", CsvCodec.escapeCell("a,b"))
        assertEquals("\"a\"\"b\"", CsvCodec.escapeCell("a\"b"))
        assertEquals("\"a\nb\"", CsvCodec.escapeCell("a\nb"))
    }

    @Test
    fun `row joins escaped cells`() {
        assertEquals("a,b", CsvCodec.row(listOf("a", "b")))
        assertEquals("\"x,y\",z", CsvCodec.row(listOf("x,y", "z")))
    }

    @Test
    fun `parse handles quoted commas and CRLF`() {
        val rows = CsvCodec.parse("a,\"x,y\"\r\nb,c\r\n")
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "x,y"), rows[0])
        assertEquals(listOf("b", "c"), rows[1])
    }

    @Test
    fun `parse handles doubled quotes inside quoted cell`() {
        val rows = CsvCodec.parse("\"say \"\"hi\"\"\",2")
        assertEquals(listOf("say \"hi\"", "2"), rows.single())
    }

    // === CsvImporter ===

    @Test
    fun `parseDestruction accepts digits and percent`() {
        assertEquals(100, CsvImporter.parseDestruction("100"))
        assertEquals(100, CsvImporter.parseDestruction("100%"))
        assertEquals(50, CsvImporter.parseDestruction("50 %"))
        assertEquals(0, CsvImporter.parseDestruction("abc"))
        assertEquals(0, CsvImporter.parseDestruction(""))
    }

    @Test
    fun `isHeaderRow detects our exported header`() {
        assertTrue(CsvImporter.isHeaderRow(listOf("成员名", "排名", "总星数")))
        assertFalse(CsvImporter.isHeaderRow(listOf("张三", "1", "6")))
    }

    @Test
    fun `parse war csv with header skips it and pads 2 slots`() {
        val csv = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            张三,1,6,100%,100%
            李四,2,3,50%,20%
        """.trimIndent()
        val result = CsvImporter.parse(csv, slotCount = 2, eventType = "war")
        assertTrue(result is WarJsonParser.ParseResult.Success)
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        assertEquals(2, members.size)
        val zhang = members.first { it.playerName == "张三" }
        assertEquals(listOf(1, 2), zhang.attacks.map { it.attackOrder })
        assertEquals(listOf(100, 100), zhang.attacks.map { it.destructionPercentage })
        val li = members.first { it.playerName == "李四" }
        assertEquals(3, li.totalStars)
        assertEquals(listOf(50, 20), li.attacks.map { it.destructionPercentage })
    }

    @Test
    fun `parse league csv uses 1 slot`() {
        val csv = "王五,1,3,100%\n赵六,2,0,0"
        val result = CsvImporter.parse(csv, slotCount = 1, eventType = "league")
        assertTrue(result is WarJsonParser.ParseResult.Success)
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        assertEquals(2, members.size)
        members.forEach { assertEquals(1, it.attacks.size) }
    }

    @Test
    fun `parse missing columns defaults to zero`() {
        val csv = "张三,1\n李四"
        val result = CsvImporter.parse(csv, slotCount = 2, eventType = "war")
        assertTrue(result is WarJsonParser.ParseResult.Success)
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        assertEquals(2, members.size)
        members.forEach { m ->
            assertEquals(0, m.totalStars)
            assertEquals(2, m.attacks.size)
            assertTrue(m.attacks.all { it.destructionPercentage == 0 })
        }
    }

    @Test
    fun `parse empty csv returns error`() {
        val result = CsvImporter.parse("", slotCount = 2, eventType = "war")
        assertTrue(result is WarJsonParser.ParseResult.Error)
    }

    /** 导出文件带 BOM，回导时首格名字不能带 \uFEFF 前缀。 */
    @Test
    fun `parse strips BOM from first cell`() {
        val result = CsvImporter.parse("\uFEFF张三,1,6,100%,100%", slotCount = 2, eventType = "war")
        assertTrue(result is WarJsonParser.ParseResult.Success)
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        assertEquals("张三", members.first().playerName)
        assertEquals(100, members.first().attacks.first().destructionPercentage)
    }

    // === CsvExporter ===

    private fun event(id: String, name: String, type: String, createdAt: Long = 0L) = WarEventEntity(
        eventId = id, eventName = name, eventType = type, eventRound = 0,
        clanTotalStars = 9, clanTotalDestruction = "60%", isSample = false, createdAt = createdAt
    )

    private fun member(eventId: String, name: String, stars: Int, attacks: List<Attack>) = MemberEntity(
        id = "$eventId#$name", eventId = eventId, rank = 1, playerName = name,
        role = "member", totalStars = stars, attacks = attacks
    )

    @Test
    fun `exportEventsCsv starts with BOM and header`() {
        val ev = event("e1", "战报1", "war")
        val csv = CsvExporter.exportEventsCsv(listOf(ev), emptyMap())
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("事件名称,类型,时间,成员名,排名,职位,总星数,进攻1摧毁率,进攻2摧毁率"))
        // 无成员事件输出单行汇总
        assertTrue(csv.lines().any { it.contains("战报1,部落战") })
    }

    @Test
    fun `exportEventsCsv writes war two attacks and league one`() {
        val ev = event("e1", "0260801", "war")
        val league = event("e2", "1260801", "league")
        val members = listOf(
            member("e1", "张三", 6, listOf(Attack(1, 100), Attack(2, 80))),
            member("e2", "李四", 3, listOf(Attack(1, 100)))
        )
        val csv = CsvExporter.exportEventsCsv(
            listOf(ev, league),
            members.groupBy { it.eventId }
        )
        assertTrue(csv.lines().any { it.startsWith("0260801,部落战,") && it.contains("张三") && it.contains("100%,80%") })
        assertTrue(csv.lines().any { it.startsWith("1260801,联赛,") && it.contains("李四") && it.contains("100%,") })
    }

    @Test
    fun `exportMonthlyReportCsv contains title summary and header`() {
        val stats = com.cocwar.domain.StatsCalculator.computeMonthly(
            listOf(event("e1", "0260801", "war")),
            listOf(member("e1", "张三", 6, listOf(Attack(1, 100), Attack(2, 100))))
        )
        val csv = CsvExporter.exportMonthlyReportCsv("2026年8月部落战月度报告", stats, null)
        assertTrue(csv.startsWith("\uFEFF# 2026年8月部落战月度报告"))
        assertTrue(csv.contains("成员,职位,参战场次,有效参战"))
        assertTrue(csv.lines().any { it.contains("张三") && it.contains("100.0%") })
    }
}
