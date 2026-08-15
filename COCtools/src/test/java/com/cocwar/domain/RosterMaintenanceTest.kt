package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** RosterMaintenance 疑似离队筛选纯函数测试。 */
class RosterMaintenanceTest {

    private fun member(name: String, role: String = "member", active: Boolean = true) =
        MemberRosterEntity(name = name, role = role, active = active)

    @Test
    fun `连续缺席达到阈值入选，未达不入选`() {
        val roster = listOf(member("张三"), member("李四"), member("王五"))
        // 共 6 场部落战；张三缺席 3 场、李四 2 场、王五 0 场
        val absent = mapOf("张三" to 3, "李四" to 2, "王五" to 0)
        val result = RosterMaintenance.filterSuspectedDeparted(roster, absent, 6, 3)
        assertEquals(listOf("张三"), result.map { it.name })
        assertEquals(3, result.single().absentCount)
    }

    @Test
    fun `从未参战的新成员不误报`() {
        // 共 5 场部落战，新人从未参战 → count == totalWarCount；老手缺席 3 场（此前参战过）
        val roster = listOf(member("新人"), member("老手"))
        val absent = mapOf("新人" to 5, "老手" to 3)
        val result = RosterMaintenance.filterSuspectedDeparted(roster, absent, 5, 3)
        assertEquals(listOf("老手"), result.map { it.name })
    }

    @Test
    fun `已离队成员不参与判定`() {
        val roster = listOf(member("甲", active = false), member("乙"))
        val absent = mapOf("甲" to 6, "乙" to 4)
        val result = RosterMaintenance.filterSuspectedDeparted(roster, absent, 6, 3)
        assertEquals(listOf("乙"), result.map { it.name })
    }

    @Test
    fun `按缺席场次降序，同场次按名字升序`() {
        val roster = listOf(member("c"), member("b"), member("a"))
        val absent = mapOf("a" to 4, "b" to 5, "c" to 4)
        val result = RosterMaintenance.filterSuspectedDeparted(roster, absent, 6, 3)
        // b 缺席最多排第一；a/c 同为 4 场，按名字升序 a 在前
        assertEquals(listOf("b", "a", "c"), result.map { it.name })
    }

    @Test
    fun `无部落战数据返回空`() {
        val roster = listOf(member("张三"))
        assertEquals(emptyList<SuspectMember>(),
            RosterMaintenance.filterSuspectedDeparted(roster, mapOf("张三" to 0), 0, 3))
    }

    @Test
    fun `阈值越界收敛到至少 1`() {
        val roster = listOf(member("张三"))
        val absent = mapOf("张三" to 1)
        // 阈值 0/-5 都收敛为 1；共 2 场，张三缺席 1 场应入选
        assertEquals(listOf("张三"),
            RosterMaintenance.filterSuspectedDeparted(roster, absent, 2, 0).map { it.name })
        assertEquals(listOf("张三"),
            RosterMaintenance.filterSuspectedDeparted(roster, absent, 2, -5).map { it.name })
    }

    @Test
    fun `战报场次不足阈值时不入选`() {
        // 共 2 场部落战，张三缺席 1 场（此前参战过），阈值 3 达不到
        val roster = listOf(member("张三"))
        val absent = mapOf("张三" to 1)
        assertEquals(emptyList<SuspectMember>(),
            RosterMaintenance.filterSuspectedDeparted(roster, absent, 2, 3))
    }

    @Test
    fun `缺席次数缺失的成员不入选`() {
        val roster = listOf(member("张三"), member("李四"))
        val absent = mapOf("李四" to 4)
        assertEquals(listOf("李四"),
            RosterMaintenance.filterSuspectedDeparted(roster, absent, 5, 3).map { it.name })
    }

    @Test
    fun `空名单与空映射不崩溃`() {
        assertEquals(emptyList<SuspectMember>(),
            RosterMaintenance.filterSuspectedDeparted(emptyList(), emptyMap(), 5, 3))
    }
}
