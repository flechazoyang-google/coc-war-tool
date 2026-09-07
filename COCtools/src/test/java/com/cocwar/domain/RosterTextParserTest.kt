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
    fun `表头行用名字而非昵称也能跳过`() {
        // AI 实际输出的是「名字,职位」，原逻辑只认「昵称」会漏掉
        val result = RosterTextParser.parse("名字,职位\n陈平安,首领\n张三,成员")
        assertEquals(
            listOf(RosterEntry("陈平安", "leader"), RosterEntry("张三", "member")),
            result.entries
        )
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `表头中文逗号且姓名列也能跳过`() {
        val result = RosterTextParser.parse("姓名，职位\n陈平安,首领")
        assertEquals(listOf(RosterEntry("陈平安", "leader")), result.entries)
    }

    @Test
    fun `名字含全角逗号可正确解析为成员`() {
        // 用户实测：成员本名就叫「小样，我就这样」，名字内部含全角逗号；
        // 用最后一个半角逗号切分，名字里的全角逗号应保留、职位取「成员」
        val result = RosterTextParser.parse("小样，我就这样,成员\n陈平安,首领\n张三,成员")
        assertEquals(
            listOf(
                RosterEntry("小样，我就这样", "member"),
                RosterEntry("陈平安", "leader"),
                RosterEntry("张三", "member")
            ),
            result.entries
        )
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `职位非合法值保留成员并告警按成员处理`() {
        // 角色被 AI 误识别（如「首领吗不确定」「大长老」）：保留成员、按成员处理并告警，
        // 不整行丢弃——用户在预览里可改回正确职位
        val result = RosterTextParser.parse("张三,首领吗不确定\n陈平安,首领\n李四,成员")
        assertEquals(
            listOf(
                RosterEntry("张三", "member"),
                RosterEntry("陈平安", "leader"),
                RosterEntry("李四", "member")
            ),
            result.entries
        )
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings.single().contains("张三"))
        assertTrue(result.warnings.single().contains("首领吗不确定"))
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `缺名字的脏行进入已忽略`() {
        // 形如「,成员」缺名字的脏行无法解析，应进 errors 供用户修正
        val result = RosterTextParser.parse(",成员\n陈平安,首领")
        assertEquals(listOf(RosterEntry("陈平安", "leader")), result.entries)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains(",成员"))
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
        // 游戏内成员页显示「长者」，与「长老」等价
        assertEquals("elder", RosterTextParser.normalizeRole("长者"))
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
