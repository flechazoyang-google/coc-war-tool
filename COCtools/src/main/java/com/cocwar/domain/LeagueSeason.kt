package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.isUsed

/** 联赛赛季中单轮（单场战报）摘要。 */
data class LeagueRoundSummary(
    val round: Int,               // 1..7
    val eventId: String,
    val eventName: String,
    val clanTotalStars: Int,      // 本轮我方总星数
    val maxStars: Int,            // 人数×3（与满星率口径一致）
    val isFullStar: Boolean,      // 本轮是否满星
    val attackerCount: Int,       // 已出手人数
    val totalMembers: Int         // 参战人数
)

/** 赛季内单个成员的出战轮换统计。 */
data class LeagueMemberSeasonStat(
    val playerName: String,
    val role: String,
    val playedRounds: Int,        // 参战轮数
    val attackedRounds: Int,      // 有进攻的轮数
    val totalStars: Int,          // 赛季总星数
    val threeStarCount: Int,      // 三星次数
    val absentRounds: Int         // 缺阵轮数 = 赛季轮数 - 参战轮数
)

/** 一场联赛（7 轮）的赛季聚合统计。 */
data class LeagueSeasonStats(
    val year: Int,
    val month: Int,
    val match: Int,               // 1=月初场 2=月中场
    val rounds: List<LeagueRoundSummary>,          // 按轮次 1..7 升序
    val members: List<LeagueMemberSeasonStat>,     // 按总星数降序
    val totalStars: Int,
    val maxStars: Int,
    val fullRoundCount: Int
)

/**
 * 联赛赛季聚合计算（纯函数，供赛季视图使用）。
 *
 * 输入为某一场联赛（月初场或月中场）的全部轮次事件（由调用方按名称 C1C2 解析筛选）与
 * 这些事件的成员。输出：每轮摘要（星数/满星/出手率）+ 成员出战轮换统计。
 *
 * 口径对齐 docs/RULES.md：单轮理论最大星数 = 参战人数×3（重复进攻不产新星）。
 */
object LeagueSeasonCalculator {

    fun compute(
        year: Int,
        month: Int,
        match: Int,
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): LeagueSeasonStats {
        val eventIds = events.map { it.eventId }.toSet()
        val membersByEvent = allMembers.filter { it.eventId in eventIds }.groupBy { it.eventId }

        // 每轮摘要：按 eventRound 升序（缺失轮次不占位，只列实际有的轮）
        val rounds = events.sortedBy { it.eventRound }.map { ev ->
            val members = membersByEvent[ev.eventId] ?: emptyList()
            val maxStars = members.size * 3
            LeagueRoundSummary(
                round = ev.eventRound,
                eventId = ev.eventId,
                eventName = ev.eventName,
                clanTotalStars = ev.clanTotalStars,
                maxStars = maxStars,
                isFullStar = maxStars > 0 && ev.clanTotalStars >= maxStars,
                attackerCount = members.count { m -> m.attacks.any { it.isUsed() } },
                totalMembers = members.size
            )
        }

        // 成员出战轮换：跨轮聚合
        val byPlayer = allMembers.filter { it.eventId in eventIds }.groupBy { it.playerName }
        val members = byPlayer.map { (name, list) ->
            val role = list.maxByOrNull { m -> m.eventId }?.role ?: "member"
            val attackedRounds = list.count { m -> m.attacks.any { it.isUsed() } }
            val totalStars = list.sumOf { it.totalStars }
            val threeStarCount = list.flatMap { it.attacks }.count { it.isUsed() && it.destructionPercentage == 100 }
            LeagueMemberSeasonStat(
                playerName = name,
                role = role,
                playedRounds = list.size,
                attackedRounds = attackedRounds,
                totalStars = totalStars,
                threeStarCount = threeStarCount,
                absentRounds = (rounds.size - list.size).coerceAtLeast(0)
            )
        }.sortedWith(
            compareByDescending<LeagueMemberSeasonStat> { it.totalStars }
                .thenByDescending { it.attackedRounds }
                .thenBy { it.playerName }
        )

        val totalStars = events.sumOf { it.clanTotalStars }
        val maxStars = rounds.sumOf { it.maxStars }
        val fullRoundCount = rounds.count { it.isFullStar }

        return LeagueSeasonStats(
            year = year,
            month = month,
            match = match,
            rounds = rounds,
            members = members,
            totalStars = totalStars,
            maxStars = maxStars,
            fullRoundCount = fullRoundCount
        )
    }
}
