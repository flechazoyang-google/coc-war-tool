package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StatsCalculator 纯函数单元测试。
 *
 * 覆盖核心统计口径（与 docs/RULES.md 对齐）：
 * - 满星率：理论最大 = 人数×3（重复进攻同一对手不产新星），部落战/联赛通用
 * - 三星率：分母为已使用进攻（不含 unused 占位）
 * - 进攻率：分母为槽位数（部落战 2 / 联赛 1）
 * - 积分排行积分制：星+1 / 三星+1 / 满6星+2 / 空1槽-3 / 全空-10 / 未参战-4
 * - 月度统计、单场统计、未进攻排行
 */
class StatsCalculatorTest {

    // === 测试数据构造 ===

    private fun event(
        id: String,
        type: String = "war",
        round: Int = 0,
        clanStars: Int = 0,
        createdAt: Long = 0L
    ): WarEventEntity = WarEventEntity(
        eventId = id,
        eventName = "",
        eventType = type,
        eventRound = round,
        clanTotalStars = clanStars,
        clanTotalDestruction = "0%",
        isSample = false,
        createdAt = createdAt
    )

    private fun member(
        eventId: String,
        name: String,
        rank: Int = 1,
        stars: Int = 0,
        attacks: List<Attack> = emptyList()
    ): MemberEntity = MemberEntity(
        id = "$eventId#$rank",
        eventId = eventId,
        rank = rank,
        playerName = name,
        role = "member",
        totalStars = stars,
        attacks = attacks
    )

    private fun used(order: Int, destruction: Int): Attack =
        Attack(attackOrder = order, destructionPercentage = destruction)

    private fun unused(order: Int): Attack =
        Attack(attackOrder = order, destructionPercentage = 0)

    // === 满星率（口径：人数×3，部落战/联赛通用） ===

    @Test
    fun `满星率 - 部落战理论最大为人数x3而非人数x6`() {
        // 30 人部落战：26 人进攻共 146 星（对应 146 >= 30*3=90）
        val ev = event("e1", clanStars = 146)
        val members = (0 until 30).map { i ->
            member(
                "e1", "P$i", rank = i + 1,
                stars = if (i < 26) 6 else 0,
                attacks = if (i < 26) listOf(used(1, 100), used(2, 100)) else listOf(unused(1), unused(2))
            )
        }
        val overview = StatsCalculator.computeOverview(listOf(ev), members)
        // 人数×3 = 90，146 >= 90 → 满星
        assertEquals(1f, overview.fullStarRate, 0.001f)
        // 若误用人数×6 = 180 则不满星——此断言守护口径不被改回
        assertTrue(146 < 30 * 6)
    }

    @Test
    fun `满星率 - 联赛同样按人数x3`() {
        val ev = event("e1", type = "league", clanStars = 45)  // 15 人×3
        val members = (0 until 15).map { i ->
            member("e1", "P$i", rank = i + 1, stars = 3, attacks = listOf(used(1, 100)))
        }
        val overview = StatsCalculator.computeOverview(listOf(ev), members)
        assertEquals(1f, overview.fullStarRate, 0.001f)
    }

    @Test
    fun `满星率 - 未达标不算满星且无成员事件不参与分母`() {
        val ev1 = event("e1", clanStars = 89)   // 30 人理论 90
        val ev2 = event("e2", clanStars = 0, createdAt = 1)
        val members1 = (0 until 30).map { i ->
            member("e1", "P$i", rank = i + 1, stars = if (i < 29) 3 else 2,
                attacks = if (i < 29) listOf(used(1, 100)) else listOf(used(1, 66)))
        }
        // ev2 无成员 → 不参与评估
        val overview = StatsCalculator.computeOverview(listOf(ev1, ev2), members1)
        assertEquals(0f, overview.fullStarRate, 0.001f)
    }

    // === 进攻率 / 三星率（口径与 RULES.md 一致） ===

    @Test
    fun `进攻率 - 分母为槽位数 部落战2 联赛1`() {
        // 部落战：10 人，5 人各进攻 1 次 → used=5, possible=20 → 25%
        val war = event("w", clanStars = 10)
        val warMembers = (0 until 10).map { i ->
            member("w", "P$i", rank = i + 1, stars = if (i < 5) 2 else 0,
                attacks = if (i < 5) listOf(used(1, 94), unused(2)) else listOf(unused(1), unused(2)))
        }
        val overview = StatsCalculator.computeOverview(listOf(war), warMembers)
        assertEquals(5, overview.totalUsedAttacks)
        assertEquals(20, overview.totalPossibleAttacks)
        assertEquals(0.25f, overview.overallAttackRate, 0.001f)

        // 联赛：10 人，5 人各进攻 1 次 → used=5, possible=10 → 50%
        val lg = event("l", type = "league", clanStars = 10)
        val lgMembers = (0 until 10).map { i ->
            member("l", "P$i", rank = i + 1, stars = if (i < 5) 2 else 0,
                attacks = if (i < 5) listOf(used(1, 94)) else listOf(unused(1)))
        }
        val lgOverview = StatsCalculator.computeOverview(listOf(lg), lgMembers)
        assertEquals(5, lgOverview.totalUsedAttacks)
        assertEquals(10, lgOverview.totalPossibleAttacks)
        assertEquals(0.5f, lgOverview.overallAttackRate, 0.001f)
    }

    @Test
    fun `三星率 - 分母为已使用进攻 不含unused占位`() {
        // 2 人：A 一次三星一次两星；B 一次三星一次空 → used=3, 三星=2 → 2/3
        val ev = event("e", clanStars = 8)
        val members = listOf(
            member("e", "A", rank = 1, stars = 5, attacks = listOf(used(1, 100), used(2, 66))),
            member("e", "B", rank = 2, stars = 3, attacks = listOf(used(1, 100), unused(2)))
        )
        val stats = StatsCalculator.compute(ev, members)
        assertEquals(3, stats.totalUsedAttacks)
        assertEquals(2, stats.threeStarCount)
        assertEquals(2f / 3f, stats.threeStarRate, 0.001f)
        assertEquals(0, stats.nonAttackerCount)  // A、B 均有已使用进攻
    }

    @Test
    fun `单场统计 - 未进攻成员判定依赖占位`() {
        val ev = event("e")
        val members = listOf(
            member("e", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), used(2, 100))),
            member("e", "B", rank = 2, stars = 0, attacks = listOf(unused(1), unused(2))),
            member("e", "C", rank = 3, stars = 0, attacks = listOf(unused(1), unused(2)))
        )
        val stats = StatsCalculator.compute(ev, members)
        assertEquals(3, stats.totalMembers)
        assertEquals(1, stats.attackerCount)
        assertEquals(2, stats.nonAttackerCount)
        assertEquals(listOf("B", "C"), stats.nonAttackerNames)
    }

    // === 积分排行积分制 ===

    @Test
    fun `积分制 - 两次全空扣10 单次空扣3 满6星加2 每星加1`() {
        val ev = event("w", clanStars = 12)
        // A：满 6 星（2 次三星）
        // B：只打 1 次（空 1 槽）3 星
        // C：完全未进攻（空 2 槽）0 星
        // D：只打 1 次 2 星（空 1 槽）
        val members = listOf(
            member("w", "A", rank = 1, stars = 6, attacks = listOf(used(1, 100), used(2, 100))),
            member("w", "B", rank = 2, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("w", "C", rank = 3, stars = 0, attacks = listOf(unused(1), unused(2))),
            member("w", "D", rank = 4, stars = 2, attacks = listOf(used(1, 66), unused(2)))
        )
        val top = StatsCalculator.computeTopMembers(listOf(ev), members)
        val byName = top.associateBy { it.playerName }

        // A：6 星(+6) + 2 次三星(+2) + 满6星(+2) = 10
        assertEquals(10f, byName["A"]!!.score, 0.001f)
        assertEquals(0, byName["A"]!!.noAttackCount)
        assertEquals(0, byName["A"]!!.missedAttackCount)

        // B：3 星(+3) + 1 次三星(+1) + 空1槽(-3) = 1
        assertEquals(1f, byName["B"]!!.score, 0.001f)
        assertEquals(1, byName["B"]!!.missedAttackCount)

        // C：0 + 空2槽(-10) = -10
        assertEquals(-10f, byName["C"]!!.score, 0.001f)
        assertEquals(1, byName["C"]!!.noAttackCount)

        // D：2 星(+2) + 空1槽(-3) = -1
        assertEquals(-1f, byName["D"]!!.score, 0.001f)
    }

    @Test
    fun `积分制 - 名单成员未参战扣4分`() {
        val ev = event("w", clanStars = 3)
        val members = listOf(
            member("w", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2)))
        )
        val top = StatsCalculator.computeTopMembers(
            listOf(ev), members,
            roster = listOf("A", "未参战者")
        )
        val byName = top.associateBy { it.playerName }
        // A：3 星(+3) + 1 次三星(+1) + 空1槽(-3) = 1
        assertEquals(1f, byName["A"]!!.score, 0.001f)
        // 未参战者：-4
        assertEquals(-4f, byName["未参战者"]!!.score, 0.001f)
        assertEquals(1, byName["未参战者"]!!.absentCount)
    }

    @Test
    fun `积分制 - 联赛事件不参与评选`() {
        val ev = event("l", type = "league", clanStars = 3)
        val members = listOf(
            member("l", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100)))
        )
        val top = StatsCalculator.computeTopMembers(listOf(ev), members)
        assertTrue(top.isEmpty())
    }

    // === 月度统计 ===

    @Test
    fun `月度统计 - 参战率与有效参战率`() {
        val ev1 = event("e1", createdAt = 1)
        val ev2 = event("e2", createdAt = 2)
        // A 两场均参战、均进攻；B 两场均参战、仅第 1 场进攻；C 仅第 1 场参战
        val members = listOf(
            member("e1", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("e2", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("e1", "B", rank = 2, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("e2", "B", rank = 2, stars = 0, attacks = listOf(unused(1), unused(2))),
            member("e1", "C", rank = 3, stars = 0, attacks = listOf(unused(1), unused(2)))
        )
        val stats = StatsCalculator.computeMonthly(listOf(ev1, ev2), members)
        val byName = stats.associateBy { it.playerName }

        assertEquals(2, byName["A"]!!.participated)
        assertEquals(2, byName["A"]!!.attacked)
        assertEquals(0, byName["A"]!!.missedCount)
        assertEquals(1f, byName["A"]!!.participationRate, 0.001f)
        assertEquals(1f, byName["A"]!!.effectiveRate, 0.001f)

        assertEquals(2, byName["B"]!!.participated)
        assertEquals(1, byName["B"]!!.attacked)
        assertEquals(1, byName["B"]!!.missedCount)
        assertEquals(0.5f, byName["B"]!!.effectiveRate, 0.001f)

        assertEquals(1, byName["C"]!!.participated)
        assertEquals(0, byName["C"]!!.attacked)
    }

    // === 未进攻排行 ===

    @Test
    fun `未进攻排行 - 仅统计参与但未进攻`() {
        val ev1 = event("e1", createdAt = 1)
        val ev2 = event("e2", createdAt = 2)
        val members = listOf(
            member("e1", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("e2", "A", rank = 1, stars = 0, attacks = listOf(unused(1), unused(2))),
            member("e1", "B", rank = 2, stars = 0, attacks = listOf(unused(1), unused(2)))
        )
        val missed = StatsCalculator.computeRecentMissed(listOf(ev2, ev1), members, 2)
        val byName = missed.associateBy { it.playerName }
        // A：1 场未进攻；B：1 场未进攻
        assertEquals(1, byName["A"]!!.missedCount)
        assertEquals(1, byName["B"]!!.missedCount)
    }

    // === 空输入与未覆盖分支 ===

    @Test
    fun `空输入 - 各统计入口返回空或全零`() {
        val overview = StatsCalculator.computeOverview(emptyList(), emptyList())
        assertEquals(0, overview.totalEvents)
        assertEquals(0, overview.warCount)
        assertEquals(0, overview.leagueCount)
        assertEquals(0f, overview.overallAttackRate, 0.001f)
        assertTrue(overview.war == null)
        assertTrue(overview.league == null)

        assertTrue(StatsCalculator.computeMonthly(emptyList(), emptyList()).isEmpty())
        assertTrue(StatsCalculator.computeRecentMissed(emptyList(), emptyList(), 10).isEmpty())
        assertTrue(StatsCalculator.computeRecentMissed(emptyList(), emptyList(), -1).isEmpty())
        assertTrue(StatsCalculator.computeTopMembers(emptyList(), emptyList()).isEmpty())
        assertTrue(StatsCalculator.computeEventSummaries(emptyList(), emptyList()).isEmpty())

        val stats = StatsCalculator.compute(event("e"), emptyList())
        assertEquals(0, stats.totalMembers)
        assertEquals(0, stats.totalUsedAttacks)
        assertEquals(0, stats.threeStarCount)
        assertEquals(0f, stats.threeStarRate, 0.001f)
        assertEquals(0f, stats.avgDestruction, 0.001f)
    }

    // === computeEventSummaries（单场战报摘要） ===

    @Test
    fun `单场摘要 - 联赛每人1槽 部落战每人2槽`() {
        val war = event("w", clanStars = 6)
        val warMembers = listOf(
            member("w", "A", rank = 1, stars = 6, attacks = listOf(used(1, 100), used(2, 100)))
        )
        val warSummary = StatsCalculator.computeEventSummaries(listOf(war), warMembers).single()
        assertEquals(2, warSummary.possibleAttacks)
        assertEquals(2, warSummary.totalUsedAttacks)
        assertEquals(1f, warSummary.participationRate, 0.001f)
        assertEquals(2, warSummary.threeStarCount)

        val lg = event("l", type = "league", clanStars = 3)
        val lgMembers = listOf(
            member("l", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100)))
        )
        val lgSummary = StatsCalculator.computeEventSummaries(listOf(lg), lgMembers).single()
        assertEquals(1, lgSummary.possibleAttacks)
        assertEquals(1f, lgSummary.participationRate, 0.001f)
    }

    @Test
    fun `单场摘要 - 参与率为使用进攻除以可用槽位`() {
        // 10 人部落战：5 人各进攻 1 次 → used=5, possible=20 → 25%
        val ev = event("e", clanStars = 10)
        val members = (0 until 10).map { i ->
            member("e", "P$i", rank = i + 1, stars = if (i < 5) 2 else 0,
                attacks = if (i < 5) listOf(used(1, 94), unused(2)) else listOf(unused(1), unused(2)))
        }
        val summary = StatsCalculator.computeEventSummaries(listOf(ev), members).single()
        assertEquals(5, summary.totalUsedAttacks)
        assertEquals(20, summary.possibleAttacks)
        assertEquals(0.25f, summary.participationRate, 0.001f)
    }

    // === computeOverview 分类型汇总 ===

    @Test
    fun `分类型统计 - 部落战与联赛分开汇总`() {
        val war = event("w", clanStars = 9)
        val league = event("l", type = "league", clanStars = 3, createdAt = 1)
        val members = listOf(
            member("w", "A", rank = 1, stars = 6, attacks = listOf(used(1, 100), used(2, 100))),
            member("w", "B", rank = 2, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("l", "C", rank = 1, stars = 3, attacks = listOf(used(1, 100)))
        )
        val overview = StatsCalculator.computeOverview(listOf(war, league), members)
        assertEquals(2, overview.totalEvents)
        assertEquals(1, overview.warCount)
        assertEquals(1, overview.leagueCount)
        assertEquals(9, overview.war!!.totalStars)
        assertEquals(3, overview.league!!.totalStars)
        assertEquals(3f, overview.league!!.avgStarsPerEvent, 0.001f)  // 场均星数 = 单场总星 3
        assertEquals(1, overview.league!!.totalUsedAttacks)
        assertEquals(1, overview.league!!.totalPossibleAttacks)  // 1 人联赛 × 1 槽
        assertEquals(1f, overview.league!!.attackRate, 0.001f)
    }

    // === computeMonthly 角色取最近事件 ===

    @Test
    fun `月度统计 - 角色取最近一次参战事件`() {
        val ev1 = event("e1", createdAt = 1)
        val ev2 = event("e2", createdAt = 2)
        val members = listOf(
            member("e1", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2))),
            member("e2", "A", rank = 1, stars = 3, attacks = listOf(used(1, 100), unused(2)))
                .copy(role = "leader")
        )
        val stats = StatsCalculator.computeMonthly(listOf(ev1, ev2), members)
        assertEquals("leader", stats.single().role)
    }
}
