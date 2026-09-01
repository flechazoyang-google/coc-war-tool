package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity

/**
 * 解析后的花名册条目（name 已 trim，role 为规范值 leader/coLeader/elder/member）。
 */
data class RosterEntry(val name: String, val role: String)

/**
 * 花名册文本解析结果：条目保持输入顺序、已去重；warnings 供预览页展示。
 */
data class ParsedRoster(val entries: List<RosterEntry>, val warnings: List<String>)

/**
 * 花名册文本解析（「更新花名册」用）。
 *
 * 输入格式：一行一个成员，`昵称,职位`（英文逗号；中文逗号同样接受）。
 * 为承接 AI（豆包等）粘贴输出做了容错：BOM、Markdown 围栏、表头行、
 * 无职位行（默认成员）、中文/英文职位、重名（首次出现优先）。
 */
object RosterTextParser {

    private val FENCE = Regex("```[^`\n]*\\s*([\\s\\S]*?)```")

    /** 职位归一化：中文（首领/副首领/长老/成员）与英文变体 → 规范值；无法识别返回 null。 */
    fun normalizeRole(raw: String): String? {
        val norm = raw.trim().lowercase().replace("-", "").replace("_", "")
        return when (norm) {
            "leader", "首领" -> "leader"
            "coleader", "viceleader", "副首领", "副族长" -> "coLeader"
            "elder", "长老" -> "elder"
            "member", "成员" -> "member"
            else -> null
        }
    }

    fun parse(text: String): ParsedRoster {
        val cleaned = stripFence(text.trim().removePrefix("\uFEFF"))
        val entries = mutableListOf<RosterEntry>()
        val warnings = mutableListOf<String>()
        val seen = HashSet<String>()
        for (rawLine in cleaned.split('\n')) {
            val line = rawLine.trimEnd('\r').trim()
            if (line.isEmpty()) continue
            // 表头行跳过（AI 输出常带「昵称,职位」表头）
            if ("昵称" in line && "职位" in line) continue
            val idx = line.indexOfAny(charArrayOf(',', '，'))
            val name = if (idx >= 0) line.substring(0, idx).trim() else line
            val roleRaw = if (idx >= 0) line.substring(idx + 1).trim() else ""
            if (name.isEmpty()) continue
            if (!seen.add(name)) {
                warnings.add("「$name」重复出现，已忽略后面的记录")
                continue
            }
            val role = if (roleRaw.isEmpty()) {
                "member"
            } else {
                normalizeRole(roleRaw) ?: run {
                    warnings.add("「$name」职位「$roleRaw」无法识别，已按成员处理")
                    "member"
                }
            }
            entries.add(RosterEntry(name, role))
        }
        return ParsedRoster(entries, warnings)
    }

    /** 剥离 Markdown 代码围栏（```/```csv/```text 等），无围栏时原样返回。 */
    private fun stripFence(text: String): String {
        val match = FENCE.find(text) ?: return text
        return match.groupValues[1]
    }
}

/**
 * 职位变化明细（预览用）。
 */
data class RoleChange(val name: String, val oldRole: String, val newRole: String)

/**
 * 软替换差异（预览用，五类互斥；原已离队且不在新名单的成员不进任何列表，保持离队不动）。
 */
data class RosterDiff(
    val added: List<RosterEntry>,
    val restored: List<RosterEntry>,
    val roleChanged: List<RoleChange>,
    val departing: List<MemberRosterEntity>,
    val unchangedCount: Int
)

/**
 * 计算软替换差异：新名单 vs 现有花名册。
 *
 * 职位比较沿用 [RosterTextParser.normalizeRole] 的归一化口径（忽略大小写与连字符/下划线），
 * 容错数据库中遗留的 `co-leader` 等写法，避免误报职位变化。
 */
fun computeRosterDiff(
    current: List<MemberRosterEntity>,
    incoming: List<RosterEntry>
): RosterDiff {
    val byName = current.associateBy { it.name }
    val incomingNames = incoming.map { it.name }.toSet()
    val added = mutableListOf<RosterEntry>()
    val restored = mutableListOf<RosterEntry>()
    val roleChanged = mutableListOf<RoleChange>()
    var unchangedCount = 0
    for (entry in incoming) {
        val existing = byName[entry.name]
        when {
            existing == null -> added.add(entry)
            !existing.active -> restored.add(entry)
            sameRole(existing.role, entry.role) -> unchangedCount++
            else -> roleChanged.add(RoleChange(entry.name, existing.role, entry.role))
        }
    }
    val departing = current.filter { it.active && it.name !in incomingNames }
    return RosterDiff(added, restored, roleChanged, departing, unchangedCount)
}

/** 职位等价比较：均归一化后比较；任一侧无法识别时退回精确比较。 */
private fun sameRole(a: String, b: String): Boolean {
    val na = RosterTextParser.normalizeRole(a)
    val nb = RosterTextParser.normalizeRole(b)
    return if (na != null && nb != null) na == nb else a == b
}
