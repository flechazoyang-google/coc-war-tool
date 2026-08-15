package com.cocwar.ui.members

import com.cocwar.data.db.MemberRosterEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** sortRoster 纯函数测试（花名册排序：职位 → 连续缺席部落战场次从少到多）。 */
class MemberRosterSortTest {

    private fun member(name: String, role: String = "member") =
        MemberRosterEntity(name = name, role = role)

    @Test
    fun `先按职位分组，同职位按连续缺席场次从少到多`() {
        val roster = listOf(
            member("张三", "member"),
            member("李四", "leader"),
            member("王五", "member"),
            member("赵六", "elder"),
            member("钱七", "member")
        )
        val absent = mapOf(
            "张三" to 3, "李四" to 0, "王五" to 0, "赵六" to 1, "钱七" to 2
        )
        val sorted = sortRoster(roster, absent)
        assertEquals(
            listOf("李四", "赵六", "王五", "钱七", "张三"),
            sorted.map { it.name }
        )
    }

    @Test
    fun `同职位同缺席次数保持花名册原有顺序`() {
        val roster = listOf(
            member("阿一", "elder"),
            member("阿二", "elder"),
            member("阿三", "member")
        )
        val sorted = sortRoster(roster, mapOf("阿一" to 0, "阿二" to 0, "阿三" to 1))
        assertEquals(listOf("阿一", "阿二", "阿三"), sorted.map { it.name })
    }

    @Test
    fun `缺席次数缺失的成员排在同职位末尾`() {
        val roster = listOf(member("甲", "member"), member("乙", "member"))
        val sorted = sortRoster(roster, mapOf("甲" to 0))
        assertEquals(listOf("甲", "乙"), sorted.map { it.name })
    }

    @Test
    fun `从未参战按全部场次计，排在同职位最后`() {
        val roster = listOf(member("新人", "member"), member("老手", "member"))
        // 共 2 场部落战：老手参加最新一场（0），新人从未参战（2）
        val sorted = sortRoster(roster, mapOf("老手" to 0, "新人" to 2))
        assertEquals(listOf("老手", "新人"), sorted.map { it.name })
    }

    @Test
    fun `空名单与空缺席映射不崩溃`() {
        assertEquals(emptyList<MemberRosterEntity>(), sortRoster(emptyList(), emptyMap()))
        assertEquals(listOf("甲"), sortRoster(listOf(member("甲")), emptyMap()).map { it.name })
    }
}
