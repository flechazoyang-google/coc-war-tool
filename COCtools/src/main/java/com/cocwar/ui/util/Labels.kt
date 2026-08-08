package com.cocwar.ui.util

import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR

fun eventTypeLabel(type: String): String = when (type) {
    EVENT_TYPE_WAR -> "部落战"
    EVENT_TYPE_LEAGUE -> "联赛"
    else -> type
}

/** 归一化 role 字符串：去连字符/下划线，全小写 */
private fun normRole(role: String): String =
    role.lowercase().replace("-", "").replace("_", "")

fun roleLabel(role: String): String = when (normRole(role)) {
    "leader" -> "首领"
    "coleader", "viceleader" -> "副首领"
    "elder" -> "长老"
    "member" -> "成员"
    else -> role
}

fun formatPercent(value: Float): String = "%.1f%%".format(java.util.Locale.US, value)

fun formatPercent(value: Int): String = "$value%"

/**
 * 校验是否为合法的 SAABBCC 名称，并返回 (year, month)。
 * 要求：S ∈ {'0','1'}，AA/BB/CC 均为两位数字，BB(月) ∈ 1..12。
 * 非标准名称（如"示例·30人部落战"、"1212战报01"）返回 null，避免误判。
 */
private fun parseNameParts(name: String): Pair<Int, Int>? {
    if (name.length < 7) return null
    val s = name[0]
    if (s != '0' && s != '1') return null
    val digits = name.substring(1, 7)
    if (!digits.all { it.isDigit() }) return null
    val year = digits.substring(0, 2).toIntOrNull() ?: return null
    val month = digits.substring(2, 4).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return year to month
}

/** 解析 SAABBCC 格式名称为可读文本。
 * 部落战：S=0, CC=场次 → "第X场"
 * 联赛：S=1, CC=C1C2 → "月初场 第X轮" / "月中场 第X轮"（C1=0 月初场，C1=1 月中场，C2=轮次）
 */
fun parseEventDisplayName(name: String): String {
    val parts = parseNameParts(name) ?: return name
    val s = name[0]
    val cc = name.substring(5, 7).toIntOrNull() ?: return name
    return if (s == '0') {
        "第${cc}场"
    } else {
        // CC = C1C2：C1 场次归属（0=月初场，1=月中场），C2 该场第几轮（1..7）
        if (cc !in 1..7 && cc !in 11..17) return name
        "${if (cc < 10) "月初场" else "月中场"} 第${cc % 10}轮"
    }
}

/**
 * 从联赛名称解析所属场次：C1=0（CC 01..07）→ 1（月初场），C1=1（CC 11..17）→ 2（月中场）。
 * 非联赛/无法解析返回 null。
 */
fun parseLeagueMatchFromName(name: String): Int? {
    parseNameParts(name) ?: return null
    val s = name[0]
    if (s != '1') return null  // 非联赛
    val cc = name.substring(5, 7).toIntOrNull() ?: return null
    return when {
        cc in 1..7 -> 1
        cc in 11..17 -> 2
        else -> null
    }
}

/**
 * 从部落战名称解析场次序号（CC = 当月第 N 场，1..99）。
 * S=0 且整体为合法 SAABBCC 时返回 CC；非部落战/无法解析/CC 越界（如 00）返回 null。
 */
fun parseWarSeqFromName(name: String): Int? {
    parseNameParts(name) ?: return null
    if (name[0] != '0') return null  // 非部落战
    val cc = name.substring(5, 7).toIntOrNull() ?: return null
    return cc.takeIf { it in 1..99 }
}

/**
 * 部落战视图排序比较器：年份倒序 → 月份倒序 → 场次序号（CC）升序（第 1 场在前）。
 * 名称无法解析年份/月份/序号的排最后（descending 下用 MIN_VALUE 哨兵垫底、CC 用 MAX_VALUE 垫底）。
 */
fun compareWarEventsBySeq(): Comparator<WarEventEntity> =
    compareByDescending<WarEventEntity> { parseYearFromName(it.eventName) ?: Int.MIN_VALUE }
        .thenByDescending { parseMonthFromName(it.eventName) ?: Int.MIN_VALUE }
        .thenBy { parseWarSeqFromName(it.eventName) ?: Int.MAX_VALUE }

/**
 * 联赛组内轮次排序比较器（第 1 轮在前）：名称 C2 轮次解析优先（与列表页展示口径一致），
 * 失败回退实体 eventRound（1..7），再失败（0/无效）排组内末尾。
 */
fun compareLeagueRound(): Comparator<WarEventEntity> =
    compareBy<WarEventEntity> { ev ->
        parseEventRoundFromName(ev.eventName).takeIf { it in 1..7 }
            ?: ev.eventRound.takeIf { it in 1..7 }
            ?: 99
    }

/**
 * 联赛轮次展示文案（如 " · 月初场 第3轮" / " · 月中场 第1轮" / " · 第3轮"）。
 * 轮次优先取实体 eventRound 字段（数据源权威），月初/月中从名称 C1 解析；
 * 均无法确定时返回空串（如非联赛）。
 */
fun leagueRoundLabel(name: String, round: Int): String {
    val match = parseLeagueMatchFromName(name)?.let { if (it == 1) "月初场" else "月中场" }
    return when {
        round in 1..7 && match != null -> " · $match 第${round}轮"
        round in 1..7 -> " · 第${round}轮"
        match != null -> " · $match"
        else -> ""
    }
}

/** 从名称中提取类型过滤键：null=无法解析, "0"=部落战, "1"=联赛 */
fun parseEventTypeFromName(name: String): String? {
    // 仅当整体是合法 SAABBCC 时按首字符判断类型，避免"1212战报01"这类名称误判为联赛
    return parseNameParts(name)?.let { name[0].toString() }
}

/** 从名称中提取年份（后两位） */
fun parseYearFromName(name: String): Int? = parseNameParts(name)?.first

/** 从名称中提取月份 */
fun parseMonthFromName(name: String): Int? = parseNameParts(name)?.second

/**
 * 从名称解析轮次。联赛名称 SAABBCC 中 CC = C1C2，C2 即轮次（1..7）。
 * 部落战返回 0（无轮次概念）。
 */
fun parseEventRoundFromName(name: String): Int {
    val parts = parseNameParts(name) ?: return 0
    val s = name[0]
    if (s != '1') return 0  // 非联赛，无轮次
    val cc = name.substring(5, 7).toIntOrNull() ?: return 0
    // CC = C1C2：C2 即轮次；仅合法值 01..07 / 11..17 可解析
    return if (cc in 1..7 || cc in 11..17) cc % 10 else 0
}
