package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity

/**
 * 月度成员参战统计。
 * 部落战和联赛不区分，统一计算。
 */
data class MemberMonthlyStat(
    val playerName: String,
    val role: String,
    val participated: Int,
    val attacked: Int,
    val totalEvents: Int,          // 当月总事件数
    val participationRate: Float,
    val effectiveRate: Float,
    val missedCount: Int
)

/** 近n次未进攻排行榜条目 */
data class RecentMissedRank(
    val playerName: String,
    val role: String,
    val missedCount: Int           // 参与但未进攻次数
)

/**
 * Aggregate statistics computed from one event's members.
 */
data class WarStats(
    val totalMembers: Int,
    val attackerCount: Int,
    val nonAttackerCount: Int,
    val nonAttackerNames: List<String>,
    val totalUsedAttacks: Int,
    val totalStarsObtained: Int,
    val threeStarCount: Int,
    val threeStarRate: Float,
    val avgDestruction: Float,
    val fullDestructionCount: Int
)

data class RoleStat(
    val role: String,
    val total: Int,
    val attackers: Int
)

object StatsCalculator {

    fun compute(event: WarEventEntity, members: List<MemberEntity>): WarStats {
        val usedAttacks = members.flatMap { it.attacks }.filter { it.status == "used" }
        val totalUsedAttacks = usedAttacks.size

        val attackerCount = members.count { m -> m.attacks.any { it.status == "used" } }
        val nonAttackers = members.filter { m -> m.attacks.none { it.status == "used" } }
        val nonAttackerNames = nonAttackers.sortedBy { it.rank }.map { it.playerName }

        // 三星次数：摧毁率百分之百即认定为三星
        val threeStarCount = usedAttacks.count { it.destructionPercentage == 100 }

        val threeStarRate = if (totalUsedAttacks > 0) threeStarCount.toFloat() / totalUsedAttacks else 0f
        val avgDestruction = if (usedAttacks.isNotEmpty())
            usedAttacks.map { it.destructionPercentage }.average().toFloat() else 0f
        val fullDestructionCount = usedAttacks.count { it.destructionPercentage == 100 }

        // 总星数 = 所有成员 totalStars 之和
        val totalStarsObtained = members.sumOf { it.totalStars }

        return WarStats(
            totalMembers = members.size,
            attackerCount = attackerCount,
            nonAttackerCount = members.size - attackerCount,
            nonAttackerNames = nonAttackerNames,
            totalUsedAttacks = totalUsedAttacks,
            totalStarsObtained = totalStarsObtained,
            threeStarCount = threeStarCount,
            threeStarRate = threeStarRate,
            avgDestruction = avgDestruction,
            fullDestructionCount = fullDestructionCount
        )
    }

    /**
     * 按月统计每个成员的参战率和有效参战率。
     * @param events 当月所有事件
     * @param allMembers 当月所有事件的成员（跨事件汇总）
     */
    fun computeMonthly(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): List<MemberMonthlyStat> {
        if (events.isEmpty()) return emptyList()

        val totalEvents = events.size
        val eventIds = events.map { it.eventId }.toSet()

        // 按 playerName 分组，统计每个玩家在当月各事件中的参与情况
        val byPlayer = allMembers.groupBy { it.playerName }

        return byPlayer.map { (name, members) ->
            // 只统计当月事件的参与
            val monthMembers = members.filter { it.eventId in eventIds }
            val participated = monthMembers.size
            val attacked = monthMembers.count { m -> m.attacks.any { a -> a.status == "used" } }
            val role = monthMembers.lastOrNull()?.role ?: "member"

            MemberMonthlyStat(
                playerName = name,
                role = role,
                participated = participated,
                attacked = attacked,
                totalEvents = totalEvents,
                participationRate = if (totalEvents > 0) participated.toFloat() / totalEvents else 0f,
                effectiveRate = if (participated > 0) attacked.toFloat() / participated else 0f,
                missedCount = participated - attacked
            )
        }.sortedByDescending { it.effectiveRate }
    }

    /**
     * 近n次事件中参与但未进攻次数排行榜，0次不显示。
     * @param events 按时间倒序排列的事件列表
     * @param allMembers 这些事件的所有成员
     * @param n 取最近的 n 个事件，<=0 表示全部
     */
    fun computeRecentMissed(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>,
        n: Int
    ): List<RecentMissedRank> {
        val selected = if (n <= 0 || n >= events.size) events else events.take(n)
        if (selected.isEmpty()) return emptyList()

        val eventIds = selected.map { it.eventId }.toSet()
        val byPlayer = allMembers.filter { it.eventId in eventIds }.groupBy { it.playerName }

        return byPlayer.mapNotNull { (name, members) ->
            val attacked = members.count { m -> m.attacks.any { a -> a.status == "used" } }
            val missed = members.size - attacked
            if (missed <= 0) null
            else RecentMissedRank(
                playerName = name,
                role = members.lastOrNull()?.role ?: "member",
                missedCount = missed
            )
        }.sortedByDescending { it.missedCount }
    }
}
