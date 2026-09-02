package com.cocwar.domain

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.isUsed
import java.util.Locale

/**
 * 成员名合并纯函数：OCR 错名修正/导入映射导致同一事件内出现同名多行时，
 * 把多行合成一行（保留首行的 id/排名/职位）。
 */
object MemberMerge {

    /** 单成员单场星数上限：联赛 1 次进攻 3 星，部落战 2 次进攻 6 星。 */
    fun maxStarsFor(eventType: String): Int = if (eventType == EVENT_TYPE_LEAGUE) 3 else 6

    /**
     * 合并两行成员数据：以 [keep] 为主（保留其 id/排名/名字/职位），
     * 进攻按次序取摧毁率较高的一方，总星数求和后按上限封顶——
     * 摧毁率无法可靠还原星数，取和在「一行有数据、一行为空」的常见
     * 场景下正确，完全重复的极端情况由单场星数上限兜底。
     */
    fun mergeRows(keep: MemberEntity, other: MemberEntity, maxStars: Int): MemberEntity =
        keep.copy(
            totalStars = (keep.totalStars + other.totalStars).coerceAtMost(maxStars),
            attacks = (keep.attacks + other.attacks)
                .groupBy { it.attackOrder }
                .map { (_, list) -> list.maxBy { it.destructionPercentage } }
                .sortedBy { it.attackOrder }
        )

    /**
     * 事件内按名字去重：同名多行合并为一行（保留首行）。
     * 无重名时原样返回（同一个实例，避免无谓复制）。
     */
    fun dedupeByName(members: List<MemberEntity>, eventType: String): List<MemberEntity> {
        val names = HashSet<String>()
        if (members.none { !names.add(it.playerName) }) return members
        val maxStars = maxStarsFor(eventType)
        val byName = LinkedHashMap<String, MemberEntity>()
        for (m in members) {
            val first = byName[m.playerName]
            byName[m.playerName] =
                if (first == null) m else mergeRows(first, m, maxStars)
        }
        return byName.values.toList()
    }

    /**
     * 重算事件聚合：总星数 = 成员星数之和；摧毁率 = 已用进攻的平均值
     * （口径与 WarRepository.refreshEventStats 一致，供改名/合并后刷新）。
     */
    fun recomputeTotals(event: WarEventEntity, members: List<MemberEntity>): WarEventEntity {
        val totalStars = members.sumOf { it.totalStars }
        val used = members.flatMap { it.attacks }.filter { it.isUsed() }
        val destruction = if (used.isEmpty()) "0%"
        else "%.1f%%".format(Locale.US, used.map { it.destructionPercentage }.average())
        return event.copy(clanTotalStars = totalStars, clanTotalDestruction = destruction)
    }
}
