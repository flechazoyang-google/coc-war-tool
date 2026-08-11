package com.cocwar.data.model

import com.google.gson.annotations.SerializedName

/**
 * DTO parsed directly from the user-supplied JSON.
 * Every field is nullable so a missing key never crashes the lenient parser.
 */
data class WarDto(
    @SerializedName("members") val members: List<MemberDto>? = null
)

/**
 * 精简后的成员结构：仅 player_name / total_stars / attacks。
 * rank/role 字段仅用于兼容旧版数据源解析，新数据不再依赖：
 * - rank 缺省时按 members 数组顺序（index+1）
 * - role 一律以花名册为准，JSON 中的 role 被忽略
 */
data class MemberDto(
    @SerializedName("rank") val rank: Int? = null,
    @SerializedName("player_name") val playerName: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("total_stars") val totalStars: Int? = 0,
    @SerializedName("attacks") val attacks: List<AttackDto>? = null
)

/** 精简后的进攻结构：仅 attack_order / destruction_percentage（status 由摧毁率是否为 0 推导）。 */
data class AttackDto(
    @SerializedName("attack_order") val attackOrder: Int? = 0,
    @SerializedName("destruction_percentage") val destructionPercentage: Int? = 0
)

/**
 * Domain model used inside the app (non-null, sanitized).
 */
data class Attack(
    val attackOrder: Int = 0,
    val destructionPercentage: Int = 0
)

/** 是否已发起进攻：摧毁率 > 0 视为已使用（原 status 字段语义由摧毁率是否为 0 推导）。 */
fun Attack.isUsed(): Boolean = destructionPercentage > 0

/** event_type values */
const val EVENT_TYPE_WAR = "war"
const val EVENT_TYPE_LEAGUE = "league"
