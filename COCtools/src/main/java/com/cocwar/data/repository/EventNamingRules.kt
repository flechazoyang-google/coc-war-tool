package com.cocwar.data.repository

/**
 * SAABBCC 事件命名规则（纯函数，可测试）。
 *
 * 名称结构：S(类型) + AA(年) + BB(月) + CC(序号/轮次编码)
 * - 部落战：CC = 当月第 N 场（上限 99）。
 * - 联赛：CC 拆为 C1C2 —— C1 场次归属（0=月初场，1=月中场），C2 该场第几轮（1..7），
 *   合法值 01..07（月初场）/ 11..17（月中场）。
 * 口径对齐 docs/RULES.md 与 DataMigrator。
 */
object EventNamingRules {

    /**
     * 计算 CC 段（纯函数）：输入本月同类型事件的合法名称列表与调用方轮次提示。
     * 联赛：优先续填本月已有联赛所在场次的最小空缺轮次；月初场录满 7 轮后开月中场；
     * 两场都录满（14 轮）时无法用 CC 表达，返回 99 兜底（解析端视为无法解析）。
     * 部落战：CC = 当月第 N 场（上限 99，避免 SAABBCC 7 位解析断裂）。
     */
    fun computeCC(monthNames: List<String>, eventType: String, eventRound: Int): Int {
        if (eventType == "league") {
            val usedCC = monthNames.mapNotNull { name ->
                if (!isValidSeqName(name, '1')) null   // 仅统计合法联赛名（S='1'，口径与部落战一致）
                else name.substring(5, 7).toIntOrNull()?.takeIf { it in 1..7 || it in 11..17 }
            }
            val seg0 = usedCC.filter { it in 1..7 }.toSet()                     // 月初场已占轮次（C2=1..7）
            val seg1 = usedCC.filter { it in 11..17 }.map { it - 10 }.toSet()   // 月中场已占轮次（C2=1..7，归一化）
            val hint = eventRound.takeIf { it in 1..7 }
            val (c1, round) = when {
                seg0.isEmpty() -> 0 to (hint ?: 1)
                seg0.size < 7 -> 0 to (hint?.takeIf { it !in seg0 } ?: (1..7).first { it !in seg0 })
                seg1.isEmpty() -> 1 to (hint ?: 1)
                seg1.size < 7 -> 1 to (hint?.takeIf { it !in seg1 } ?: (1..7).first { it !in seg1 })
                else -> -1 to 0  // 两场联赛共 14 轮已录满，CC 无法表达
            }
            return if (c1 >= 0) c1 * 10 + round else 99
        }
        // 部落战：CC = 当月第 N 场（上限 99）
        val warCount = monthNames.count { isValidSeqName(it, '0') }
        return (warCount + 1).coerceAtMost(99)
    }

    /** 是否为合法 SAABBCC 名称：S 位 = 指定类型、第 1~6 位全为数字、月份合法。 */
    fun isValidSeqName(name: String, s: Char): Boolean {
        if (name.length < 7 || name[0] != s) return false
        if (!name.substring(1, 7).all { it.isDigit() }) return false
        val month = name.substring(3, 5).toIntOrNull() ?: return false
        return month in 1..12
    }

    /** 从 SAABBCC 格式名称解析类型和轮次；无法解析时保留原值。 */
    fun parseTypeAndRound(name: String, fallbackType: String, fallbackRound: Int): Pair<String, Int> {
        // 严格校验：S ∈ {'0','1'} 且第 1~6 位全为数字、月份合法，避免非标准名称误判
        if (name.length < 7) return fallbackType to fallbackRound
        val s = name[0]
        if (s != '0' && s != '1') return fallbackType to fallbackRound
        if (!name.substring(1, 7).all { it.isDigit() }) return fallbackType to fallbackRound
        val month = name.substring(3, 5).toIntOrNull() ?: return fallbackType to fallbackRound
        if (month !in 1..12) return fallbackType to fallbackRound
        val cc = name.substring(5, 7).toIntOrNull() ?: return fallbackType to fallbackRound
        val type = if (s == '1') "league" else "war"
        val round = if (s == '1') {
            // 联赛 CC = C1C2：C1 场次归属（0=月初场，1=月中场），C2 该场第几轮（1..7）
            // 合法值 01..07 / 11..17，其余视为无法解析（与 computeCC 一致）
            if (cc !in 1..7 && cc !in 11..17) return fallbackType to fallbackRound
            cc % 10
        } else 0
        return type to round
    }
}
