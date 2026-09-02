package com.cocwar.ui.members

import com.cocwar.data.db.MemberRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RosterHealthCheck 数据体检纯函数测试。 */
class RosterHealthCheckTest {

    private fun entry(name: String, active: Boolean = true) =
        MemberRosterEntity(name = name, role = "member", active = active)

    @Test
    fun `事件错名不在册且有相似在册成员时报出`() {
        val counts = mapOf("张叁" to 5, "张三" to 10)
        val roster = listOf(entry("张三"))
        val issues = RosterHealthCheck.scan(counts, roster)
        assertEquals(1, issues.size)
        val issue = issues[0]
        assertEquals("张叁", issue.name)
        assertEquals(5, issue.rowCount)
        assertEquals("张三", issue.suggestion)
        assertEquals(10, issue.suggestionRowCount)
        assertEquals(false, issue.inRoster)
    }

    @Test
    fun `名单外名字无相似成员不报`() {
        val counts = mapOf("王五" to 3, "张三" to 10)
        val roster = listOf(entry("张三"))
        assertTrue(RosterHealthCheck.scan(counts, roster).isEmpty())
    }

    @Test
    fun `低频在册条目与高频名字相似时报出`() {
        val counts = mapOf("张叁" to 2, "张三" to 10)
        val roster = listOf(entry("张叁"), entry("张三"))
        val issues = RosterHealthCheck.scan(counts, roster)
        assertEquals(1, issues.size)
        assertEquals("张叁", issues[0].name)
        assertEquals("张三", issues[0].suggestion)
        assertEquals(true, issues[0].inRoster)
    }

    @Test
    fun `双方都是低频不报`() {
        // 张叁 2 行、张三 2 行：目标不满足高频条件，无法判定谁是错名
        val counts = mapOf("张叁" to 2, "张三" to 2)
        val roster = listOf(entry("张叁"), entry("张三"))
        assertTrue(RosterHealthCheck.scan(counts, roster).isEmpty())
    }

    @Test
    fun `高频在册条目即使相似也不报`() {
        // 两个高频名字相似：无法判定谁是错名，交由人工长按合并
        val counts = mapOf("张叁" to 10, "张三" to 12)
        val roster = listOf(entry("张叁"), entry("张三"))
        assertTrue(RosterHealthCheck.scan(counts, roster).isEmpty())
    }

    @Test
    fun `从未参战的在册成员不参与低频判定`() {
        // 王小朋在册但 0 行（不在行数表）→ 不报
        val counts = mapOf("张三" to 10)
        val roster = listOf(entry("王小朋"), entry("张三"))
        assertTrue(RosterHealthCheck.scan(counts, roster).isEmpty())
    }

    @Test
    fun `忽略名单过滤两类可疑项`() {
        val counts = mapOf("张叁" to 5, "李四四" to 1, "李四" to 8)
        val roster = listOf(entry("张三"), entry("李四"), entry("李四四"))
        val ignored = setOf("张叁", "李四四")
        assertTrue(RosterHealthCheck.scan(counts, roster, ignored).isEmpty())
    }

    @Test
    fun `单字名互不匹配不报`() {
        // 单字名一字之差兜底已移除：明（不在册）与 朋（在册）不判为疑似同名
        val counts = mapOf("明" to 3, "朋" to 8)
        val roster = listOf(entry("朋"))
        assertTrue(RosterHealthCheck.scan(counts, roster).isEmpty())
    }

    @Test
    fun `按相似度降序排序`() {
        // 甲叁 vs 甲三 = 0.5；王小朋 vs 王小明 = 2/3 ≈ 0.67
        val counts = mapOf("甲叁" to 4, "王小朋" to 4, "甲三" to 8, "王小明" to 8)
        val roster = listOf(entry("甲三"), entry("王小明"))
        val issues = RosterHealthCheck.scan(counts, roster)
        assertEquals(listOf("王小朋", "甲叁"), issues.map { it.name })
    }

    @Test
    fun `空输入不崩溃`() {
        assertTrue(RosterHealthCheck.scan(emptyMap(), listOf(entry("张三"))).isEmpty())
        assertTrue(RosterHealthCheck.scan(mapOf("张三" to 3), emptyList()).isEmpty())
        assertTrue(RosterHealthCheck.scan(emptyMap(), emptyList()).isEmpty())
    }
}
