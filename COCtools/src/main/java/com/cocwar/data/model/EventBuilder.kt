package com.cocwar.data.model

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity

/**
 * 事件构建：把已解析的基础成员数据（CSV 导入 / 样例数据）组装为可持久化的
 * [ParsedEvent]。职责：事件 ID 生成、进攻槽位补齐、同名去重、职位注入（以花名册为准）、
 * 部落汇总统计。CSV 与样例数据两条数据源共用，避免重复实现。
 */
object EventBuilder {

    /** 一条待入库的基础成员数据（不含 eventId / id，由本类统一生成）。 */
    data class MemberInput(
        val rank: Int,
        val playerName: String,
        val totalStars: Int,
        val attacks: List<Attack>
    )

    /**
     * 组装事件。
     *
     * @param inputs 基础成员列表（rank 从 1 起、attacks 的 attackOrder 从 1 起）
     * @param rosterRoles 花名册职位映射（名字→职位），职位一律以花名册为准
     */
    fun build(
        inputs: List<MemberInput>,
        isSample: Boolean,
        createdAt: Long,
        eventType: String = EVENT_TYPE_WAR,
        eventRound: Int = 0,
        rosterRoles: Map<String, String> = emptyMap()
    ): ParsedEvent {
        val round = eventRound
        // event_id 自动生成；追加 nanoTime 防同一毫秒导入多个事件时 ID 碰撞
        val eventId = "$eventType${createdAt}_${System.nanoTime()}"
        // 每人进攻槽位：部落战 2 槽，联赛 1 槽（attackOrder 从 1 开始）
        val slotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2

        val members = inputs.mapIndexed { index, m ->
            val role = rosterRoles[m.playerName] ?: "member"
            // 去重：按 attackOrder 分组，每个 order 只保留摧毁率最高的一条，防止重复记录导致 used 超 slot
            val dedupedAttacks = m.attacks
                .groupBy { it.attackOrder }
                .map { (_, list) -> list.maxByOrNull { it.destructionPercentage }!! }
            // 补齐未使用槽位为 destruction=0 占位（保证进攻率/未进攻统计口径正确）
            val attacks = dedupedAttacks + (1..slotCount)
                .filterNot { order -> dedupedAttacks.any { it.attackOrder == order } }
                .map { Attack(attackOrder = it, destructionPercentage = 0) }
            // 用 index 确保主键唯一，避免重复 rank 导致成员被 REPLACE 静默覆盖
            MemberEntity(
                id = "$eventId#$index",
                eventId = eventId,
                rank = m.rank.coerceAtLeast(1),
                playerName = m.playerName,
                role = role,
                totalStars = m.totalStars,
                attacks = attacks
            )
        }

        // 同名去重：按 rank 顺序，重名者依次编号「原名」「原名1」「原名2」
        val deduped = deduplicateNames(members)

        // 我方总星数 = 所有成员 total_stars 之和（-1「看不清」按 0 计，导入预览确认后归 0）
        val clanStars = deduped.sumOf { it.totalStars.coerceAtLeast(0) }
        // 我方总摧毁率 = 已使用攻击（摧毁率 > 0）的平均摧毁率；-1 不入平均
        val usedAttacks = deduped.flatMap { it.attacks }.filter { it.isUsed() }
        val clanDestructionAvg = if (usedAttacks.isNotEmpty())
            usedAttacks.map { it.destructionPercentage }.average() else 0.0

        val event = WarEventEntity(
            eventId = eventId,
            eventName = "",  // 用户导入时填写
            eventType = eventType,
            eventRound = round,
            clanTotalStars = clanStars,
            clanTotalDestruction = "%.1f%%".format(java.util.Locale.US, clanDestructionAvg),
            isSample = isSample,
            createdAt = createdAt
        )

        return ParsedEvent(event, deduped)
    }

    /** 同名去重：按顺序，首次出现的保留原名，后续依次编号「原名1」「原名2」。 */
    private fun deduplicateNames(members: List<MemberEntity>): List<MemberEntity> {
        val counts = mutableMapOf<String, Int>()
        return members.map { m ->
            val base = m.playerName
            val count = counts.getOrDefault(base, 0)
            counts[base] = count + 1
            if (count == 0) m
            else m.copy(playerName = "$base$count")
        }
    }
}
