package com.cocwar.domain

import com.cocwar.data.db.MemberRosterEntity

/**
 * 解析后的花名册条目（name 已 trim，role 为规范值 leader/coLeader/elder/member）。
 */
data class RosterEntry(val name: String, val role: String)

/**
 * 花名册文本解析结果：条目保持输入顺序、已去重；warnings / errors 供预览页展示。
 * - [warnings]：能解析但需提示（如职位无法识别，已按成员处理、重名去重）。
 * - [errors]：整行无法解析、已直接忽略（仅限缺名字的脏行，如「,成员」），
 *   预览页会单独标红，用户需手动修正被忽略的行。
 */
data class ParsedRoster(
    val entries: List<RosterEntry>,
    val warnings: List<String>,
    val errors: List<String> = emptyList()
)

/**
 * 花名册文本解析（「更新花名册」用）。
 *
 * 输入格式：一行一个成员，`名字,职位`（半角逗号为主；名字内部可含全角逗号「，」）。
 * 为承接 AI（豆包等）粘贴输出做了容错：BOM、Markdown 围栏、表头行、
 * 无职位行（默认成员）、中文/英文职位、重名（首次出现优先）。
 *
 * 健壮性：
 * - 表头（`名字/昵称/姓名,职位` 等多种写法）自动跳过；
 * - 用「最后一个半角逗号」切分，名字内的全角逗号保留在名字中（如「小样，我就这样」）；
 *   找不到半角逗号时退回「最后一个全角逗号」切分；
 * - 有逗号但切出的「职位」不是合法职位（AI 混入说明文字等）→ 整行判为格式异常，
 *   列入 errors 供用户手动修正，不静默入库。
 */
object RosterTextParser {

    private val FENCE = Regex("```[^`\n]*\\s*([\\s\\S]*?)```")
    private val HEADER_NAMES = setOf("名字", "昵称", "姓名")

    /** 职位归一化：中文（首领/副首领/长老/长者/成员）与英文变体 → 规范值；无法识别返回 null。 */
    fun normalizeRole(raw: String): String? {
        val norm = raw.trim().lowercase().replace("-", "").replace("_", "")
        return when (norm) {
            "leader", "首领" -> "leader"
            "coleader", "viceleader", "副首领", "副族长" -> "coLeader"
            // 游戏内成员页显示「长者」，社区习惯写作「长老」，两者等价
            "elder", "长老", "长者" -> "elder"
            "member", "成员" -> "member"
            else -> null
        }
    }

    fun parse(text: String): ParsedRoster {
        val cleaned = stripFence(text.trim().removePrefix("\uFEFF"))
        val entries = mutableListOf<RosterEntry>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val seen = HashSet<String>()
        for (rawLine in cleaned.split('\n')) {
            val line = rawLine.trimEnd('\r').trim()
            if (line.isEmpty()) continue
            // 表头行跳过（兼容 名字/昵称/姓名 + 职位 多种写法，半角/全角逗号与空格）
            if (isHeader(line)) continue
            // 部落名字内部常含全角逗号「，」，而 AI 按提示词用半角逗号「,」分隔 名字,职位。
            // 故优先用「最后一个半角逗号」切分：把名字里的全角逗号保留在名字中；
            // 找不到半角逗号时（AI 全程用全角）再退而用「最后一个全角逗号」切分。
            val split = splitLast(line, ',') ?: splitLast(line, '，')
            if (split == null) {
                // 整行无逗号：视为「只给了名字、未给职位」→ 按成员处理
                if (!seen.add(line)) {
                    warnings.add("「$line」重复出现，已忽略后面的记录")
                    continue
                }
                entries.add(RosterEntry(line, "member"))
                continue
            }
            val name = split.name
            if (name.isEmpty()) {
                // 如「,成员」这类缺名字的脏行：整行无法解析，列进 errors 供用户修正
                errors.add("已忽略无法解析的行：「$line」")
                continue
            }
            if (!seen.add(name)) {
                warnings.add("「$name」重复出现，已忽略后面的记录")
                continue
            }
            val role = if (split.roleRaw.isEmpty()) {
                "member"
            } else {
                normalizeRole(split.roleRaw) ?: run {
                    // 职位无法识别（如 AI 把角色误识别成「大长老」）：保留成员、按成员处理并告警，
                    // 不整行丢弃——用户在预览里可手动把职位改回正确值
                    warnings.add("「$name」职位「${split.roleRaw}」无法识别，已按成员处理")
                    "member"
                }
            }
            entries.add(RosterEntry(name, role))
        }
        return ParsedRoster(entries, warnings, errors)
    }

    /** 表头行：含「职位」列且列名是 名字/昵称/姓名 之一（兼容半角/全角逗号与空格）。 */
    private fun isHeader(line: String): Boolean {
        val l = line.replace(" ", "")
        return l.contains("职位") && HEADER_NAMES.any { it in l }
    }

    /** 以最后一个 [delim] 切分出的 名字 + 职位原文；无该分隔符返回 null。 */
    private data class Split(val name: String, val roleRaw: String)

    private fun splitLast(line: String, delim: Char): Split? {
        val idx = line.lastIndexOf(delim)
        if (idx < 0) return null
        return Split(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
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
 * 硬替换差异（预览用，四类互斥）：
 * - [added] 新名单有、花名册没有 → 确认后加入；
 * - [departing] 花名册有、新名单没有 → 确认后**删除**；
 * - [roleChanged] 两边都有但职位不同 → 以新名单为准；
 * - [unchangedCount] 名字与职位都没变的人数。
 */
data class RosterDiff(
    val added: List<RosterEntry>,
    val roleChanged: List<RoleChange>,
    val departing: List<MemberRosterEntity>,
    val unchangedCount: Int
)

/**
 * 计算硬替换差异：新名单 vs 现有花名册。
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
    val roleChanged = mutableListOf<RoleChange>()
    var unchangedCount = 0
    for (entry in incoming) {
        val existing = byName[entry.name]
        when {
            existing == null -> added.add(entry)
            sameRole(existing.role, entry.role) -> unchangedCount++
            else -> roleChanged.add(RoleChange(entry.name, existing.role, entry.role))
        }
    }
    val departing = current.filter { it.name !in incomingNames }
    return RosterDiff(added, roleChanged, departing, unchangedCount)
}

/** 职位等价比较：均归一化后比较；任一侧无法识别时退回精确比较。 */
private fun sameRole(a: String, b: String): Boolean {
    val na = RosterTextParser.normalizeRole(a)
    val nb = RosterTextParser.normalizeRole(b)
    return if (na != null && nb != null) na == nb else a == b
}
