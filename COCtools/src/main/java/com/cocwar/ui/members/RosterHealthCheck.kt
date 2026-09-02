package com.cocwar.ui.members

import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.ui.util.StringMatcher

/** 数据体检发现的可疑项。 */
data class HealthIssue(
    /** 可疑名字（事件中出现）。 */
    val name: String,
    /** 该名字在非示例战报中的行数。 */
    val rowCount: Int,
    /** 建议合并到的成员名。 */
    val suggestion: String,
    /** 建议目标的行数。 */
    val suggestionRowCount: Int,
    /** true = 花名册低频条目（疑似同一人拆成两条）；false = 不在花名册（疑似识图错名）。 */
    val inRoster: Boolean,
    /** 与建议目标的相似度 0..1。 */
    val score: Float
)

/**
 * 数据体检纯函数：扫描 OCR 错名残留与花名册重复条目。
 *
 * 两类可疑项（均要求存在相似度 ≥ 0.5 或等长一字之差的目标，见
 * [StringMatcher.isLikelySameName]；无相似目标的名单外名字多为已删册成员，不报）：
 * 1. 事件中出现但不在花名册的名字，且与某在册成员疑似同名 → 多为识图错名；
 * 2. 花名册中出现 ≤ [LOW_COUNT_MAX] 行的低频条目，且与出现 ≥ [HIGH_COUNT_MIN] 行
 *    的其他条目疑似同名 → 疑似同一人被拆成两条。
 *
 * 结果按相似度降序（最可疑的在前），同分按名字升序，保证稳定可测。
 */
object RosterHealthCheck {

    /** 低频判定：名字出现 ≤ N 行视为可疑。 */
    const val LOW_COUNT_MAX = 2

    /** 高频判定：建议目标需出现 ≥ N 行。 */
    const val HIGH_COUNT_MIN = 3

    fun scan(
        memberRowCounts: Map<String, Int>,
        roster: List<MemberRosterEntity>,
        ignored: Set<String> = emptySet()
    ): List<HealthIssue> {
        if (memberRowCounts.isEmpty() || roster.isEmpty()) return emptyList()
        val rosterNames = roster.map { it.name }
        val rosterNameSet = rosterNames.toSet()
        val issues = mutableListOf<HealthIssue>()

        // ① 事件中出现但不在花名册：疑似识图错名
        for ((name, count) in memberRowCounts) {
            if (name in rosterNameSet || name in ignored) continue
            StringMatcher.bestLikelyMatch(name, rosterNames)?.let { (target, score) ->
                issues.add(
                    HealthIssue(
                        name = name,
                        rowCount = count,
                        suggestion = target,
                        suggestionRowCount = memberRowCounts[target] ?: 0,
                        inRoster = false,
                        score = score
                    )
                )
            }
        }

        // ② 花名册低频条目与高频名字相似：疑似同一人拆成两条
        for (entry in roster) {
            val count = memberRowCounts[entry.name] ?: continue
            if (count > LOW_COUNT_MAX || entry.name in ignored) continue
            StringMatcher.bestLikelyMatch(entry.name, rosterNames.filter { it != entry.name })
                ?.let { (target, score) ->
                    val targetCount = memberRowCounts[target] ?: 0
                    if (targetCount >= HIGH_COUNT_MIN) {
                        issues.add(
                            HealthIssue(
                                name = entry.name,
                                rowCount = count,
                                suggestion = target,
                                suggestionRowCount = targetCount,
                                inRoster = true,
                                score = score
                            )
                        )
                    }
                }
        }

        return issues.sortedWith(
            compareByDescending<HealthIssue> { it.score }.thenBy { it.name }
        )
    }
}
