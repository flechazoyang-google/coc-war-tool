package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** computeRosterDiff 软替换差异纯函数测试。 */
class RosterDiffTest {

    private fun member(name: String, role: String = "member", active: Boolean = true) =
        MemberRosterEntity(name = name, role = role, active = active)

    private fun entry(name: String, role: String = "member") = RosterEntry(name, role)

    @Test
    fun `五类差异各归其位`() {
        val current = listOf(
            member("张三", "member"),          // 不在新名单 → 将离队
            member("李四", "member"),          // 职位变长老 → 职位变化
            member("王五", "elder"),           // 职位不变 → 不变
            member("赵六", "member", false)    // 已离队且回来 → 恢复在册
        )
        val incoming = listOf(
            entry("李四", "elder"),
            entry("王五", "elder"),
            entry("赵六", "member"),
            entry("新人", "leader")            // 现名单没有 → 新增
        )
        val diff = computeRosterDiff(current, incoming)
        assertEquals(listOf("新人"), diff.added.map { it.name })
        assertEquals(listOf("赵六"), diff.restored.map { it.name })
        assertEquals(listOf(RoleChange("李四", "member", "elder")), diff.roleChanged)
        assertEquals(listOf("张三"), diff.departing.map { it.name })
        assertEquals(1, diff.unchangedCount)
    }

    @Test
    fun `已离队且不在新名单保持离队不出现`() {
        val current = listOf(member("甲", active = false), member("乙"))
        val diff = computeRosterDiff(current, listOf(entry("乙")))
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.restored.isEmpty())
        assertTrue(diff.roleChanged.isEmpty())
        assertTrue(diff.departing.isEmpty())
        assertEquals(1, diff.unchangedCount)
    }

    @Test
    fun `已离队且在新名单归入恢复在册`() {
        val current = listOf(member("甲", "elder", active = false))
        val diff = computeRosterDiff(current, listOf(entry("甲", "elder")))
        assertEquals(listOf("甲"), diff.restored.map { it.name })
        // 恢复者的职位覆盖不单列为职位变化
        assertTrue(diff.roleChanged.isEmpty())
        assertEquals(0, diff.unchangedCount)
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
    fun `新名单与现有一致时全部不变`() {
        val current = listOf(member("张三", "leader"), member("李四", "member"))
        val incoming = listOf(entry("李四", "member"), entry("张三", "leader"))
        val diff = computeRosterDiff(current, incoming)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.restored.isEmpty())
        assertTrue(diff.roleChanged.isEmpty())
        assertTrue(diff.departing.isEmpty())
        assertEquals(2, diff.unchangedCount)
    }

    @Test
    fun `空新名单时全部在册归入将离队`() {
        val current = listOf(member("张三"), member("李四", active = false))
        val diff = computeRosterDiff(current, emptyList())
        assertEquals(listOf("张三"), diff.departing.map { it.name })
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
