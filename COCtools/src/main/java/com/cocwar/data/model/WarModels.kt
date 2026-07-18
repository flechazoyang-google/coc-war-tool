package com.cocwar.data.model

import com.google.gson.annotations.SerializedName

/**
 * DTO parsed directly from the user-supplied JSON.
 * Every field is nullable so a missing key never crashes the lenient parser.
 */
data class WarDto(
    @SerializedName("members") val members: List<MemberDto>? = null
)

data class MemberDto(
    @SerializedName("rank") val rank: Int? = 0,
    @SerializedName("player_name") val playerName: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("total_stars") val totalStars: Int? = 0,
    @SerializedName("attacks") val attacks: List<AttackDto>? = null
)

data class AttackDto(
    @SerializedName("attack_order") val attackOrder: Int? = 0,
    @SerializedName("status") val status: String? = null,
    @SerializedName("destruction_percentage") val destructionPercentage: Int? = 0
)

/**
 * Domain model used inside the app (non-null, sanitized).
 */
data class Attack(
    val attackOrder: Int = 0,
    val status: String = "unused",
    val destructionPercentage: Int = 0
)

data class Member(
    val rank: Int,
    val playerName: String,
    val role: String,
    val totalStars: Int,
    val attacks: List<Attack>
)

/** event_type values */
const val EVENT_TYPE_WAR = "war"
const val EVENT_TYPE_LEAGUE = "league"
