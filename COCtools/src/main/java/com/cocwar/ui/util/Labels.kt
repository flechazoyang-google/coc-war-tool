package com.cocwar.ui.util

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
    "coleader" -> "副首领"
    "elder" -> "长老"
    "member" -> "成员"
    else -> role
}

fun formatPercent(value: Float): String = "%.1f%%".format(value)

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
 * 联赛：S=1, CC=轮次 → "第X场联赛第Y轮"（CC 1~7第一场，8~14第二场）
 */
fun parseEventDisplayName(name: String): String {
    val parts = parseNameParts(name) ?: return name
    val s = name[0]
    val cc = name.substring(5, 7).toIntOrNull() ?: return name
    return if (s == '0') {
        "第${cc}场"
    } else {
        val match = (cc - 1) / 7 + 1  // 1 or 2
        val round = (cc - 1) % 7 + 1   // 1..7
        "第${match}场联赛第${round}轮"
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
 * 从名称解析轮次。联赛名称 SAABBCC 中 CC = (场次-1)*7 + 轮次。
 * 部落战返回 0（无轮次概念）。
 */
fun parseEventRoundFromName(name: String): Int {
    val parts = parseNameParts(name) ?: return 0
    val s = name[0]
    if (s != '1') return 0  // 非联赛，无轮次
    val cc = name.substring(5, 7).toIntOrNull() ?: return 0
    return (cc - 1) % 7 + 1  // 1..7
}
