package com.cocwar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RosterTextParser 花名册文本解析纯函数测试。 */
class RosterTextParserTest {

    @Test
    fun `基本解析英文逗号与中文职位`() {
        val result = RosterTextParser.parse("陈平安,首领\n宁姚,副首领\n裴钱,长老\n曹慈,成员")
        assertEquals(
            listOf(
                RosterEntry("陈平安", "leader"),
                RosterEntry("宁姚", "coLeader"),
                RosterEntry("裴钱", "elder"),
                RosterEntry("曹慈", "member")
            ),
            result.entries
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `中文逗号容错`() {
        val result = RosterTextParser.parse("陈平安，首领\n张三，长老")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "elder")),
            result.entries
        )
    }

    @Test
    fun `空行与空白行跳过`() {
        val result = RosterTextParser.parse("\n  \n陈平安,首领\n\n\t\n张三,成员\n")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
    }

    @Test
    fun `BOM 与围栏剥离`() {
        val text = "\uFEFF以下是识别结果：\n```csv\n陈平安,首领\n张三,成员\n```\n希望对你有帮助"
        val result = RosterTextParser.parse(text)
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
    }

    @Test
    fun `表头行跳过`() {
        val result = RosterTextParser.parse("昵称,职位\n陈平安,首领\n张三,成员")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
    }

    @Test
    fun `无职位行默认成员`() {
        val result = RosterTextParser.parse("陈平安\n张三,长老")
        assertEquals(
            listOf(RosterEntry("陈平安", "member"), RosterEntry("张三", "elder")),
            result.entries
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `英文职位归一化`() {
        assertEquals("leader", RosterTextParser.normalizeRole("Leader"))
        assertEquals("coLeader", RosterTextParser.normalizeRole("CO-LEADER"))
        assertEquals("coLeader", RosterTextParser.normalizeRole("vice_leader"))
        assertEquals("coLeader", RosterTextParser.normalizeRole("coLeader"))
        assertEquals("elder", RosterTextParser.normalizeRole(" Elder "))
        assertEquals("member", RosterTextParser.normalizeRole("MEMBER"))
        assertEquals("leader", RosterTextParser.normalizeRole("首领"))
        assertEquals("coLeader", RosterTextParser.normalizeRole("副首领"))
        assertEquals("elder", RosterTextParser.normalizeRole("长老"))
        assertEquals("member", RosterTextParser.normalizeRole("成员"))
    }

    @Test
    fun `未知职位按成员处理并告警`() {
        val result = RosterTextParser.parse("陈平安,大长老")
        assertEquals(listOf(RosterEntry("陈平安", "member")), result.entries)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("陈平安"))
        assertTrue(result.warnings.single().contains("大长老"))
        assertEquals(null, RosterTextParser.normalizeRole("大佬"))
    }

    @Test
    fun `重名去重保留首次出现并告警`() {
        val result = RosterTextParser.parse("陈平安,首领\n张三,成员\n陈平安,长老")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("陈平安"))
    }

    @Test
    fun `空输入与纯围栏返回空`() {
        assertEquals(ParsedRoster(emptyList(), emptyList()), RosterTextParser.parse(""))
        assertEquals(ParsedRoster(emptyList(), emptyList()), RosterTextParser.parse("   \n \t "))
        assertEquals(ParsedRoster(emptyList(), emptyList()), RosterTextParser.parse("```\n```"))
    }

    @Test
    fun `只有职位没有名字的行跳过`() {
        val result = RosterTextParser.parse(",首领\n，长老\n陈平安,成员")
        assertEquals(listOf(RosterEntry("陈平安", "member")), result.entries)
    }

    @Test
    fun `行尾回车与首尾空格被清理`() {
        val result = RosterTextParser.parse(" 陈平安 , 首领 \r\n  张三,member\r\n")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
    }
}
