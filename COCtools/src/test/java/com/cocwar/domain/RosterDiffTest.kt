package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** computeRosterDiff 硬替换差异纯函数测试（新增 / 职位变化 / 退出 / 不变）。 */
class RosterDiffTest {

    private fun member(name: String, role: String = "member") =
        MemberRosterEntity(name = name, role = role)

    private fun entry(name: String, role: String = "member") = RosterEntry(name, role)

    @Test
    fun `四类差异各归其位`() {
        val current = listOf(
            member("张三"),              // 不在新名单 → 退出
            member("李四"),              // 职位变长老 → 职位变化
            member("王五", "elder"),     // 职位不变 → 不变
            member("赵六")               // 职位不变 → 不变
        )
        val incoming = listOf(
            entry("李四", "elder"),
            entry("王五", "elder"),
            entry("赵六"),
            entry("新人", "leader")      // 现名单没有 → 新增
        )
        val diff = computeRosterDiff(current, incoming)
        assertEquals(listOf("新人"), diff.added.map { it.name })
        assertEquals(listOf(RoleChange("李四", "member", "elder")), diff.roleChanged)
        assertEquals(listOf("张三"), diff.departing.map { it.name })
        assertEquals(2, diff.unchangedCount)
    }

    @Test
    fun `退出成员全部进入退出列表`() {
        val current = listOf(member("甲"), member("乙"))
        val diff = computeRosterDiff(current, listOf(entry("乙")))
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.roleChanged.isEmpty())
        assertEquals(listOf("甲"), diff.departing.map { it.name })
        assertEquals(1, diff.unchangedCount)
    }

    @Test
    fun `职位等价比较忽略大小写与分隔符`() {
        // 数据库遗留 co-leader 写法 vs 解析出的 coLeader → 视为不变
        val current = listOf(member("张三", "co-leader"))
        val diff = computeRosterDiff(current, listOf(entry("张三", "coLeader")))
        assertTrue(diff.roleChanged.isEmpty())
        assertEquals(1, diff.unchangedCount)
    }

    @Test
    fun `职位不同才计入职位变化`() {
        val current = listOf(member("甲", "elder"))
        val diff = computeRosterDiff(current, listOf(entry("甲", "leader")))
        assertEquals(listOf(RoleChange("甲", "elder", "leader")), diff.roleChanged)
        assertEquals(0, diff.unchangedCount)
    }

    @Test
    fun `新名单与现有一致时全部不变`() {
        val current = listOf(member("张三", "leader"), member("李四"))
        val incoming = listOf(entry("李四"), entry("张三", "leader"))
        val diff = computeRosterDiff(current, incoming)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.roleChanged.isEmpty())
        assertTrue(diff.departing.isEmpty())
        assertEquals(2, diff.unchangedCount)
    }

    @Test
    fun `空新名单时全部归入退出`() {
        val current = listOf(member("张三"), member("李四"))
        val diff = computeRosterDiff(current, emptyList())
        assertEquals(listOf("张三", "李四"), diff.departing.map { it.name })
    }

    @Test
    fun `现有花名册为空时全部新增`() {
        val incoming = listOf(entry("张三", "leader"), entry("李四"))
        val diff = computeRosterDiff(emptyList(), incoming)
        assertEquals(incoming, diff.added)
        assertTrue(diff.departing.isEmpty())
        assertEquals(0, diff.unchangedCount)
    }
}
