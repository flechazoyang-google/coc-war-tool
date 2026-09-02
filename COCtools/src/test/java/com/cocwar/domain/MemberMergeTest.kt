package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** MemberMerge 纯函数测试：同名行合并 / 事件内去重 / 聚合重算。 */
class MemberMergeTest {

    private fun member(
        id: String,
        rank: Int,
        name: String,
        stars: Int,
        vararg attacks: Pair<Int, Int>
    ) = MemberEntity(
        id = id,
        eventId = id.substringBefore("#"),
        rank = rank,
        playerName = name,
        role = "member",
        totalStars = stars,
        attacks = attacks.map { Attack(it.first, it.second) }
    )

    private fun event(type: String = "war") = WarEventEntity(
        eventId = "e1", eventName = "n", eventType = type, eventRound = 0,
        clanTotalStars = 99, clanTotalDestruction = "99%", isSample = false, createdAt = 0
    )

    @Test
    fun `合并保留主行身份，进攻按次序取较优值，星数求和`() {
        val keep = member("e1#1", 1, "张三", 3, 1 to 100, 2 to 0)
        val other = member("e1#2", 2, "张叁", 2, 1 to 80, 2 to 60)
        val merged = MemberMerge.mergeRows(keep, other, 6)
        assertEquals("e1#1", merged.id)
        assertEquals("张三", merged.playerName)
        assertEquals(5, merged.totalStars)
        assertEquals(listOf(Attack(1, 100), Attack(2, 60)), merged.attacks)
    }

    @Test
    fun `星数求和受单场上限封顶`() {
        val keep = member("e1#1", 1, "张三", 4, 1 to 100, 2 to 100)
        val other = member("e1#2", 2, "张叁", 4, 1 to 90, 2 to 90)
        assertEquals(6, MemberMerge.mergeRows(keep, other, 6).totalStars)
        assertEquals(3, MemberMerge.mergeRows(keep, other, 3).totalStars)
    }

    @Test
    fun `无重名时去重原样返回同一实例`() {
        val members = listOf(member("e1#1", 1, "张三", 3), member("e1#2", 2, "李四", 0))
        assertSame(members, MemberMerge.dedupeByName(members, "war"))
    }

    @Test
    fun `两行同名合并为一行，保留首行 id 与排名`() {
        val members = listOf(
            member("e1#1", 1, "张三", 3, 1 to 100),
            member("e1#2", 2, "张三", 1, 1 to 80, 2 to 50),
            member("e1#3", 3, "李四", 0)
        )
        val result = MemberMerge.dedupeByName(members, "war")
        assertEquals(listOf("e1#1", "e1#3"), result.map { it.id })
        assertEquals(listOf(1, 3), result.map { it.rank })
        assertEquals(4, result[0].totalStars)
        assertEquals(listOf(Attack(1, 100), Attack(2, 50)), result[0].attacks)
    }

    @Test
    fun `三行同名连续合并`() {
        val members = listOf(
            member("e1#1", 1, "张三", 1, 1 to 50),
            member("e1#2", 2, "张三", 2, 1 to 80),
            member("e1#3", 3, "张三", 3, 2 to 100)
        )
        val result = MemberMerge.dedupeByName(members, "war")
        assertEquals(1, result.size)
        assertEquals("e1#1", result[0].id)
        assertEquals(6, result[0].totalStars)
        assertEquals(listOf(Attack(1, 80), Attack(2, 100)), result[0].attacks)
    }

    @Test
    fun `联赛事件按 3 星封顶`() {
        val members = listOf(
            member("e1#1", 1, "张三", 3, 1 to 100),
            member("e1#2", 2, "张三", 3, 1 to 90)
        )
        assertEquals(3, MemberMerge.dedupeByName(members, "league").single().totalStars)
    }

    @Test
    fun `重算聚合星数求和摧毁率取已用进攻平均`() {
        val members = listOf(
            member("e1#1", 1, "张三", 5, 1 to 100, 2 to 50),
            member("e1#2", 2, "李四", 3, 1 to 75)
        )
        val result = MemberMerge.recomputeTotals(event(), members)
        assertEquals(8, result.clanTotalStars)
        assertEquals("75.0%", result.clanTotalDestruction)
    }

    @Test
    fun `无已用进攻时摧毁率为 0`() {
        val members = listOf(member("e1#1", 1, "张三", 0, 1 to 0, 2 to 0))
        val result = MemberMerge.recomputeTotals(event(), members)
        assertEquals(0, result.clanTotalStars)
        assertEquals("0%", result.clanTotalDestruction)
    }

    @Test
    fun `单场星数上限按事件类型判定`() {
        assertEquals(6, MemberMerge.maxStarsFor("war"))
        assertEquals(3, MemberMerge.maxStarsFor("league"))
    }

    @Test
    fun `去重不改变无重名列表内容`() {
        val members = listOf(member("e1#1", 1, "张三", 3), member("e1#2", 2, "张三", 3))
        // 有重名时应产生新列表而非修改原列表
        val result = MemberMerge.dedupeByName(members, "war")
        assertNotSame(members, result)
        assertEquals(1, result.size)
        assertEquals(2, members.size)
    }
}
