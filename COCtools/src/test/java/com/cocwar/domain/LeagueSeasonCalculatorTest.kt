package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LeagueSeasonCalculator 纯函数测试：轮次摘要 / 成员出战轮换 / 聚合统计。
 * 口径对齐 docs/RULES.md：单轮理论最大星数 = 参战人数×3。
 */
class LeagueSeasonCalculatorTest {

    private fun event(id: String, round: Int, stars: Int, createdAt: Long = 0L) =
        WarEventEntity(
            eventId = id,
            eventName = "10307%02d".format(round),
            eventType = "league",
            eventRound = round,
            clanTotalStars = stars,
            clanTotalDestruction = "0",
            isSample = false,
            createdAt = createdAt
        )

    private fun member(eventId: String, rank: Int, name: String, stars: Int, attacks: List<Pair<Int, Int>>) =
        MemberEntity(
            id = "$eventId#$rank",
            eventId = eventId,
            rank = rank,
            playerName = name,
            role = "member",
            totalStars = stars,
            attacks = attacks.map { Attack(it.first, it.second) }
        )

    @Test
    fun `轮次摘要-最大星数为人数乘3 且计算出手人数与满星`() {
        val events = listOf(event("e1", round = 1, stars = 9))
        val members = listOf(
            member("e1", 1, "甲", 3, listOf(1 to 100)),
            member("e1", 2, "乙", 3, listOf(1 to 100)),
            member("e1", 3, "丙", 3, listOf(1 to 100)),
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, members)
        assertEquals(1, stats.rounds.size)
        val round = stats.rounds[0]
        assertEquals(1, round.round)
        assertEquals(9, round.maxStars)
        assertEquals(3, round.attackerCount)
        assertTrue(round.isFullStar)
    }

    @Test
    fun `轮次摘要-未满星且部分出手`() {
        val events = listOf(event("e1", round = 1, stars = 4))
        val members = listOf(
            member("e1", 1, "甲", 3, listOf(1 to 100)),
            member("e1", 2, "乙", 1, listOf(1 to 50)),
            member("e1", 3, "丙", 0, emptyList()),  // 未出手
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, members)
        val round = stats.rounds[0]
        assertEquals(9, round.maxStars)
        assertEquals(2, round.attackerCount)
        assertFalse(round.isFullStar)
        assertEquals(3, round.totalMembers)
    }

    @Test
    fun `轮次按 eventRound 升序-乱序输入`() {
        val events = listOf(
            event("e2", round = 2, stars = 3, createdAt = 2L),
            event("e1", round = 1, stars = 6, createdAt = 1L),
            event("e3", round = 3, stars = 0, createdAt = 3L),
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, emptyList())
        assertEquals(listOf(1, 2, 3), stats.rounds.map { it.round })
    }

    @Test
    fun `成员出战轮换-跨轮聚合与缺阵`() {
        val events = listOf(event("e1", round = 1, stars = 6), event("e2", round = 2, stars = 6))
        val members = listOf(
            member("e1", 1, "甲", 3, listOf(1 to 100)),
            member("e1", 2, "乙", 3, listOf(1 to 100)),
            member("e2", 1, "甲", 3, listOf(1 to 100)),
            member("e2", 2, "乙", 3, listOf(1 to 100)),
            member("e2", 3, "丙", 0, emptyList()),  // 第二轮才参战
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, members)
        val byName = stats.members.associateBy { it.playerName }

        // 甲：两轮都参战，无缺阵
        assertEquals(2, byName["甲"]!!.playedRounds)
        assertEquals(0, byName["甲"]!!.absentRounds)
        assertEquals(6, byName["甲"]!!.totalStars)
        assertEquals(2, byName["甲"]!!.threeStarCount)

        // 丙：只参战 1 轮（共 2 轮）→ 缺阵 1
        assertEquals(1, byName["丙"]!!.playedRounds)
        assertEquals(1, byName["丙"]!!.absentRounds)

        // 排序：总星数降序（甲/乙 6 星在前，丙 0 星在后；同星按名字序）
        assertEquals(setOf("甲", "乙"), stats.members.take(2).map { it.playerName }.toSet())
        assertEquals("丙", stats.members.last().playerName)
    }

    @Test
    fun `聚合统计-总星数与满星轮数`() {
        val events = listOf(event("e1", round = 1, stars = 9), event("e2", round = 2, stars = 6))
        val members = listOf(
            member("e1", 1, "甲", 3, listOf(1 to 100)),
            member("e1", 2, "乙", 3, listOf(1 to 100)),
            member("e1", 3, "丙", 3, listOf(1 to 100)),
            member("e2", 1, "甲", 3, listOf(1 to 100)),
            member("e2", 2, "乙", 3, listOf(1 to 100)),
            member("e2", 3, "丙", 0, emptyList()),
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, members)
        assertEquals(15, stats.totalStars)
        assertEquals(9 + 9, stats.maxStars)
        assertEquals(1, stats.fullRoundCount)  // 仅第 1 轮满星
    }

    @Test
    fun `未参战事件成员不参与统计`() {
        val events = listOf(event("e1", round = 1, stars = 3))
        // 其他事件的成员不应出现在统计中
        val members = listOf(
            member("e1", 1, "甲", 3, listOf(1 to 100)),
            member("OTHER", 1, "路人", 3, listOf(1 to 100)),
        )
        val stats = LeagueSeasonCalculator.compute(2026, 7, 1, events, members)
        assertEquals(listOf("甲"), stats.members.map { it.playerName })
        assertEquals(1, stats.rounds[0].totalMembers)
    }
}
