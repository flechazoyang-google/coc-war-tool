package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity

/**
 * 疑似离队成员：连续缺席部落战 ≥ 阈值且此前至少参战过一场的在册成员。
 */
data class SuspectMember(
    val name: String,
    val role: String,
    val absentCount: Int
)

/**
 * 花名册维护纯函数：疑似离队筛选。
 *
 * 判定口径（与 RULES.md 一致）：
 * - 仅在册（active=true）成员参与判定；
 * - 「连续缺席部落战场次」沿用 `WarRepository.getWarAbsentInfo` 的语义
 *   （只统计非联赛事件；参加但未进攻算参战；从未参战 = 全部场次）；
 * - 排除从未参战的成员（count == totalWarCount，多为新加入者，不误报）；
 * - 入选条件：`threshold <= count < totalWarCount`；
 * - 结果按缺席场次降序（最可能离队的在前），同场次按名字升序，保证稳定可测。
 */
object RosterMaintenance {

    fun filterSuspectedDeparted(
        roster: List<MemberRosterEntity>,
        absentCounts: Map<String, Int>,
        totalWarCount: Int,
        threshold: Int
    ): List<SuspectMember> {
        if (totalWarCount <= 0) return emptyList()
        val n = threshold.coerceAtLeast(1)
        return roster
            .asSequence()
            .filter { it.active }
            .mapNotNull { entry ->
                val count = absentCounts[entry.name] ?: return@mapNotNull null
                // count == totalWarCount 表示从未参战（新成员），不判疑似离队
                if (count in n until totalWarCount) {
                    SuspectMember(entry.name, entry.role, count)
                } else null
            }
            .sortedWith(compareByDescending<SuspectMember> { it.absentCount }.thenBy { it.name })
            .toList()
    }
}
