package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.isUsed

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
    val missedCount: Int,
    val totalStars: Int = 0,       // 当月总星数
    val avgStars: Float = 0f,      // 场均星数（仅参战场次）
    val avgDestruction: Float = 0f,// 场均摧毁率
    val threeStarCount: Int = 0,   // 三星次数
    val threeStarRate: Float = 0f, // 三星率
    val starRate: Float = 0f       // 归一化星率（每次进攻可得星数 0~1，部落战/联赛可比）
)

/** 单类型（部落战/联赛）汇总统计 */
data class TypeStats(
    val eventCount: Int,
    val totalStars: Int,
    val avgStarsPerEvent: Float,
    val avgDestruction: Float,
    val totalUsedAttacks: Int,
    val totalPossibleAttacks: Int,
    val attackRate: Float,
    val threeStarCount: Int,
    val threeStarRate: Float
)

/** 月度/选定范围总览统计数据 */
data class StatsOverview(
    val totalEvents: Int,
    val warCount: Int,
    val leagueCount: Int,
    // 合并统计
    val totalStars: Int,
    val fullStarRate: Float,        // 满星率：获得总星数达到理论最大值（参与人数×3）的场次占比
    val avgStarsPerEvent: Float,
    val avgDestruction: Float,
    val totalUsedAttacks: Int,
    val totalPossibleAttacks: Int,
    val overallAttackRate: Float,
    val threeStarCount: Int,
    val threeStarRate: Float,
    // 分类型统计
    val war: TypeStats?,
    val league: TypeStats?
)

/** 单场战报摘要，用于战报列表展示 */
data class EventStatSummary(
    val eventId: String,
    val eventName: String,
    val eventType: String,
    val eventRound: Int,
    val createdAt: Long,
    val participantCount: Int,
    val attackerCount: Int,
    val totalStars: Int,
    val avgDestruction: Float,
    val threeStarCount: Int,
    val threeStarRate: Float,
    val isSample: Boolean
)

/** 近n次未进攻排行榜条目 */
data class RecentMissedRank(
    val playerName: String,
    val role: String,
    val missedCount: Int           // 参与但未进攻次数
)

/** 本月最佳积分制评分条目（仅统计部落战） */
data class TopMemberScore(
    val playerName: String,
    val role: String,
    val totalStars: Int,
    val attacked: Int,              // 有效参战次数（部落战）
    val totalWarEvents: Int,        // 本月部落战总次数
    val threeStarCount: Int,        // 三星次数
    val threeStarRate: Float,       // 三星率（分母为已使用进攻）
    val fullStarEvents: Int = 0,    // 单场拿满 6 星场次（+2）
    val missedAttackCount: Int = 0, // 空 1 个进攻机会场次（-3）
    val noAttackCount: Int = 0,     // 两次进攻全空场次（-10）
    val absentCount: Int = 0,       // 名单成员未参与场次（-4）
    val score: Float                // 总得分
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

    /**
     * 计算事件范围内理论上可发动的进攻槽位数：
     * 部落战每名成员 2 槽，联赛每名成员 1 槽。
     * 官方 CoC API 数据中未进攻成员没有 attacks 字段（无 unused 占位），
     * 因此不能拿攻击记录条数当分母，否则进攻率恒为 100%。
     */
    private fun possibleAttackSlots(
        events: List<WarEventEntity>,
        members: List<MemberEntity>
    ): Int {
        if (members.isEmpty()) return 0
        val typeByEvent = events.associate { it.eventId to it.eventType }
        val eventIdSet = events.map { it.eventId }.toSet()
        // 部落战 2 槽、联赛 1 槽；仅统计属于已知事件的成员，未知事件按部落战 2 槽兜底
        return members
            .filter { it.eventId in eventIdSet }
            .map { m ->
                if (typeByEvent[m.eventId] == "league") 1 else 2
            }.sum()
    }

    /** 该成员所属事件的创建时间，用于取「最近一次」的角色等；未知事件按 0 处理。 */
    private fun eventTimeByEvent(events: List<WarEventEntity>): Map<String, Long> =
        events.associate { it.eventId to it.createdAt }

    fun compute(event: WarEventEntity, members: List<MemberEntity>): WarStats {
        val usedAttacks = members.flatMap { it.attacks }.filter { it.isUsed() }
        val totalUsedAttacks = usedAttacks.size

        val attackerCount = members.count { m -> m.attacks.any { it.isUsed() } }
        val nonAttackers = members.filter { m -> m.attacks.none { it.isUsed() } }
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
            val attacked = monthMembers.count { m -> m.attacks.any { a -> a.isUsed() } }
            // 角色取「最近一次参与事件」中的职务，避免 lastOrNull 随机取到任意一场
            val timeByEvent = eventTimeByEvent(events)
            val role = monthMembers.maxByOrNull { timeByEvent[it.eventId] ?: 0L }?.role ?: "member"

            // 增强指标
            val totalStars = monthMembers.sumOf { it.totalStars }
            val allAttacks = monthMembers.flatMap { it.attacks }
            val allUsedAttacks = allAttacks.filter { it.isUsed() }
            val avgStars = if (participated > 0) totalStars.toFloat() / participated else 0f
            val avgDestruction = if (allUsedAttacks.isNotEmpty())
                allUsedAttacks.map { it.destructionPercentage }.average().toFloat() else 0f
            val threeStarCount = allUsedAttacks.count { it.destructionPercentage == 100 }
            // 三星率分母统一为已使用进攻（与总览口径一致），避免被 unused 槽位稀释
            val threeStarRate = if (allUsedAttacks.isNotEmpty()) threeStarCount.toFloat() / allUsedAttacks.size else 0f
            // 归一化：部落战单成员最多 6 星（2 次×3），联赛最多 3 星（1 次×3）；
            // 用「每次进攻可得星数」归一，使两类战报的星率可比（0~1）。
            val starRate = if (allUsedAttacks.isNotEmpty())
                (totalStars.toFloat() / (allUsedAttacks.size * 3f)).coerceIn(0f, 1f) else 0f

            MemberMonthlyStat(
                playerName = name,
                role = role,
                participated = participated,
                attacked = attacked,
                totalEvents = totalEvents,
                participationRate = if (totalEvents > 0) participated.toFloat() / totalEvents else 0f,
                effectiveRate = if (participated > 0) attacked.toFloat() / participated else 0f,
                missedCount = participated - attacked,
                totalStars = totalStars,
                avgStars = avgStars,
                avgDestruction = avgDestruction,
                threeStarCount = threeStarCount,
                threeStarRate = threeStarRate,
                starRate = starRate
            )
        }.sortedByDescending { it.effectiveRate }
    }

    /**
     * 计算选定范围的总览统计。
     */
    fun computeOverview(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): StatsOverview {
        if (events.isEmpty()) {
            return StatsOverview(
                totalEvents = 0, warCount = 0, leagueCount = 0,
                totalStars = 0, fullStarRate = 0f, avgStarsPerEvent = 0f, avgDestruction = 0f,
                totalUsedAttacks = 0, totalPossibleAttacks = 0,
                overallAttackRate = 0f, threeStarCount = 0, threeStarRate = 0f,
                war = null, league = null
            )
        }

        val eventIds = events.map { it.eventId }.toSet()
        val members = allMembers.filter { it.eventId in eventIds }
        val totalStars = events.sumOf { it.clanTotalStars }
        val warEvents = events.filter { it.eventType != "league" }
        val leagueEvents = events.filter { it.eventType == "league" }

        val allAttacks = members.flatMap { it.attacks }
        val usedAttacks = allAttacks.filter { it.isUsed() }
        val totalUsedAttacks = usedAttacks.size
        val totalPossibleAttacks = possibleAttackSlots(events, members)
        val overallAttackRate = if (totalPossibleAttacks > 0)
            (totalUsedAttacks.toFloat() / totalPossibleAttacks).coerceIn(0f, 1f) else 0f
        val threeStarCount = usedAttacks.count { it.destructionPercentage == 100 }
        val threeStarRate = if (totalUsedAttacks > 0) threeStarCount.toFloat() / totalUsedAttacks else 0f
        val avgDestruction = if (usedAttacks.isNotEmpty())
            usedAttacks.map { it.destructionPercentage }.average().toFloat() else 0f

        return StatsOverview(
            totalEvents = events.size,
            warCount = warEvents.size,
            leagueCount = leagueEvents.size,
            totalStars = totalStars,
            fullStarRate = computeFullStarRate(events, members),
            avgStarsPerEvent = if (events.isNotEmpty()) totalStars.toFloat() / events.size else 0f,
            avgDestruction = avgDestruction,
            totalUsedAttacks = totalUsedAttacks,
            totalPossibleAttacks = totalPossibleAttacks,
            overallAttackRate = overallAttackRate,
            threeStarCount = threeStarCount,
            threeStarRate = threeStarRate,
            war = computeTypeStats(warEvents, members),
            league = computeTypeStats(leagueEvents, members)
        )
    }

    /**
     * 满星率：单场战报获得的总星数达到该场理论最大星数（参与人数×3）即计为满星。
     * 无成员数据（最大星数为 0）的场次不参与统计。
     */
    private fun computeFullStarRate(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): Float {
        if (events.isEmpty()) return 0f
        val membersByEvent = allMembers.groupBy { it.eventId }
        var fullStarEvents = 0
        var evaluableEvents = 0
        for (event in events) {
            val eventMembers = membersByEvent[event.eventId] ?: emptyList()
            val maxStars = eventMembers.size * 3
            if (maxStars <= 0) continue
            evaluableEvents++
            if (event.clanTotalStars >= maxStars) fullStarEvents++
        }
        return if (evaluableEvents > 0) fullStarEvents.toFloat() / evaluableEvents else 0f
    }

    private fun computeTypeStats(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): TypeStats? {
        if (events.isEmpty()) return null
        val eventIds = events.map { it.eventId }.toSet()
        val members = allMembers.filter { it.eventId in eventIds }
        val totalStars = events.sumOf { it.clanTotalStars }
        val allAttacks = members.flatMap { it.attacks }
        val usedAttacks = allAttacks.filter { it.isUsed() }
        val totalUsedAttacks = usedAttacks.size
        val totalPossibleAttacks = possibleAttackSlots(events, members)
        val attackRate = if (totalPossibleAttacks > 0) totalUsedAttacks.toFloat() / totalPossibleAttacks else 0f
        val threeStarCount = usedAttacks.count { it.destructionPercentage == 100 }
        val threeStarRate = if (totalUsedAttacks > 0) threeStarCount.toFloat() / totalUsedAttacks else 0f
        val avgDestruction = if (usedAttacks.isNotEmpty())
            usedAttacks.map { it.destructionPercentage }.average().toFloat() else 0f

        return TypeStats(
            eventCount = events.size,
            totalStars = totalStars,
            avgStarsPerEvent = totalStars.toFloat() / events.size,
            avgDestruction = avgDestruction,
            totalUsedAttacks = totalUsedAttacks,
            totalPossibleAttacks = totalPossibleAttacks,
            attackRate = attackRate,
            threeStarCount = threeStarCount,
            threeStarRate = threeStarRate
        )
    }

    /**
     * 计算每场战报的摘要统计。
     */
    fun computeEventSummaries(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>
    ): List<EventStatSummary> {
        if (events.isEmpty()) return emptyList()
        val membersByEvent = allMembers.groupBy { it.eventId }

        return events.map { event ->
            val members = membersByEvent[event.eventId] ?: emptyList()
            val usedAttacks = members.flatMap { it.attacks }.filter { it.isUsed() }
            val attackerCount = members.count { m -> m.attacks.any { it.isUsed() } }
            val threeStarCount = usedAttacks.count { it.destructionPercentage == 100 }
            val avgDestruction = if (usedAttacks.isNotEmpty())
                usedAttacks.map { it.destructionPercentage }.average().toFloat() else 0f
            val threeStarRate = if (usedAttacks.isNotEmpty()) threeStarCount.toFloat() / usedAttacks.size else 0f

            EventStatSummary(
                eventId = event.eventId,
                eventName = event.eventName,
                eventType = event.eventType,
                eventRound = event.eventRound,
                createdAt = event.createdAt,
                participantCount = members.size,
                attackerCount = attackerCount,
                totalStars = event.clanTotalStars,
                avgDestruction = avgDestruction,
                threeStarCount = threeStarCount,
                threeStarRate = threeStarRate,
                isSample = event.isSample
            )
        }
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
        val timeByEvent = eventTimeByEvent(selected)

        return byPlayer.mapNotNull { (name, members) ->
            val attacked = members.count { m -> m.attacks.any { a -> a.isUsed() } }
            val missed = members.size - attacked
            if (missed <= 0) null
            else RecentMissedRank(
                playerName = name,
                role = members.maxByOrNull { timeByEvent[it.eventId] ?: 0L }?.role ?: "member",
                missedCount = missed
            )
        }.sortedByDescending { it.missedCount }
    }

    /**
     * 本月最佳积分制评选（仅统计部落战）。
     *
     * 积分规则：
     * - 每获得一颗星 +1
     * - 每次 100% 摧毁率 +1
     * - 单场部落战拿满 6 星 +2
     * - 参战但空 1 个进攻机会 -3；两次进攻全空 -10
     * - 名单成员未参与该场部落战 -4
     *
     * @param roster 正式成员名单（花名册），用于未参战扣分。
     */
    fun computeTopMembers(
        events: List<WarEventEntity>,
        allMembers: List<MemberEntity>,
        roster: List<String> = emptyList(),
        rosterRoles: Map<String, String> = emptyMap()
    ): List<TopMemberScore> {
        val warEvents = events.filter { it.eventType != "league" }
        if (warEvents.isEmpty()) return emptyList()

        val warEventIds = warEvents.map { it.eventId }.toSet()
        val totalWar = warEvents.size
        val warMembers = allMembers.filter { it.eventId in warEventIds }
        val byPlayer = warMembers.groupBy { it.playerName }
        val timeByEvent = eventTimeByEvent(warEvents)
        val rosterSet = roster.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        // 评选范围 = 名单成员 ∪ 参战成员，保证未参战的名单成员也被计入扣分
        val playerNames = (rosterSet + byPlayer.keys).toSet()

        return playerNames.map { name ->
            val members = byPlayer[name] ?: emptyList()
            val allAttacks = members.flatMap { it.attacks }
            val usedAttacks = allAttacks.filter { it.isUsed() }
            val threeStarCount = usedAttacks.count { it.destructionPercentage == 100 }
            val totalStars = members.sumOf { it.totalStars }
            val attacked = members.count { m -> m.attacks.any { a -> a.isUsed() } }
            val role = members.maxByOrNull { timeByEvent[it.eventId] ?: 0L }?.role
                ?: rosterRoles[name]
                ?: "member"

            // 逐场累计积分
            val membersByEvent = members.groupBy { it.eventId }
            var score = 0f
            var fullStarEvents = 0
            var missedAttackCount = 0
            var noAttackCount = 0
            var absentCount = 0
            for (event in warEvents) {
                val m = membersByEvent[event.eventId]?.firstOrNull()
                if (m == null) {
                    // 名单成员未出现在该场战报数据中：未参战扣 4 分
                    if (name in rosterSet) {
                        score -= 4f
                        absentCount++
                    }
                    continue
                }
                // 每颗星 +1
                score += m.totalStars
                // 每次 100% 摧毁率 +1
                score += m.attacks.count { it.isUsed() && it.destructionPercentage == 100 }
                // 单场拿满 6 星 +2
                if (m.totalStars >= 6) {
                    score += 2f
                    fullStarEvents++
                }
                // 空 1 个进攻机会 -3；两次进攻全空 -10
                val unused = m.attacks.count { !it.isUsed() }
                when {
                    unused >= 2 -> {
                        score -= 10f
                        noAttackCount++
                    }
                    unused == 1 -> {
                        score -= 3f
                        missedAttackCount++
                    }
                }
            }

            // 三星率分母统一为已使用进攻，与其余统计口径一致
            val threeStarRate = if (usedAttacks.isNotEmpty()) threeStarCount.toFloat() / usedAttacks.size else 0f

            TopMemberScore(
                playerName = name,
                role = role,
                totalStars = totalStars,
                attacked = attacked,
                totalWarEvents = totalWar,
                threeStarCount = threeStarCount,
                threeStarRate = threeStarRate,
                fullStarEvents = fullStarEvents,
                missedAttackCount = missedAttackCount,
                noAttackCount = noAttackCount,
                absentCount = absentCount,
                score = score
            )
        }.sortedByDescending { it.score }
    }
}
