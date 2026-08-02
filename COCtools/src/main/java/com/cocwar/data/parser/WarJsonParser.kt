package com.cocwar.data.parser

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException

/**
 * Parses the user-supplied clan-war / league JSON into persistable entities.
 * Parsing is lenient: missing fields fall back to safe defaults and never throw
 * at the Gson layer. Structural problems are reported through [ParseResult.Error].
 */
object WarJsonParser {

    private val gson: Gson = GsonBuilder().setLenient().create()

    /** Trim and drop blank strings, returning null for missing/blank values. */
    private fun String?.clean(): String? =
        this?.trim()?.takeIf { it != null && it.isNotBlank() }

    /** 同名去重：按顺序，首次出现的保留原名，后续依次编号「原名1」「原名2」。 */
    private fun deduplicateNames(members: List<MemberEntity>): List<MemberEntity> {
        val counts = mutableMapOf<String, Int>()
        return members.map { m ->
            val base = m.playerName
            val count = counts.getOrDefault(base, 0)
            counts[base] = count + 1
            if (count == 0) m
            else m.copy(playerName = "$base$count")
        }
    }

    data class ParsedEvent(
        val event: WarEventEntity,
        val members: List<MemberEntity>
    )

    sealed interface ParseResult {
        data class Success(val data: ParsedEvent) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    fun parse(
        json: String,
        isSample: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        eventType: String = EVENT_TYPE_WAR,
        eventRound: Int = 0,
        rosterRoles: Map<String, String> = emptyMap()
    ): ParseResult {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return ParseResult.Error("JSON 内容为空")

        val dto = try {
            gson.fromJson(trimmed, WarDto::class.java)
        } catch (e: JsonSyntaxException) {
            return ParseResult.Error("JSON 格式错误：${e.message?.lineSequence()?.firstOrNull() ?: e.message}")
        } catch (e: Exception) {
            return ParseResult.Error("解析失败：${e.message}")
        } ?: return ParseResult.Error("JSON 解析结果为空")

        return try {
            ParseResult.Success(fromDto(dto, isSample, createdAt, eventType, eventRound, rosterRoles))
        } catch (e: Exception) {
            ParseResult.Error("数据校验失败：${e.message}")
        }
    }

    /** Build entities from an already-parsed DTO (used by samples too).
     *  @param rosterRoles 花名册职位映射（名字→职位），职位一律以花名册为准，忽略 JSON 中的 role。 */
    fun fromDto(
        dto: WarDto,
        isSample: Boolean,
        createdAt: Long,
        eventType: String = EVENT_TYPE_WAR,
        eventRound: Int = 0,
        rosterRoles: Map<String, String> = emptyMap()
    ): ParsedEvent {
        val round = eventRound

        // event_id 自动生成；eventName 由用户在导入时填写
        val eventId = "${eventType}_${createdAt}"
        // 每人进攻槽位：部落战 2 槽，联赛 1 槽（attackOrder 从 1 开始）。
        // 官方 CoC API 中未进攻成员没有 attacks 字段，这里补 unused 占位，
        // 使进攻率/未进攻统计口径正确；已有攻击记录保留原样。
        val slotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2

        val members = (dto.members ?: emptyList()).mapIndexed { index, m ->
            val rank = (m.rank ?: (index + 1)).coerceAtLeast(1)
            val playerName = m.playerName.clean() ?: "未知玩家#$rank"
            // 职位一律来自花名册（rosterRoles），JSON 中的 role 字段已不再使用
            val role = rosterRoles[playerName] ?: "member"
            // 精简结构：attack 只有 attack_order 与 destruction_percentage，
            // status 语义由摧毁率推导（>0 即已使用）；旧数据源的 status 字段在 DTO 层被忽略
            val rawAttacks = (m.attacks ?: emptyList()).map { a ->
                Attack(
                    attackOrder = (a.attackOrder ?: 0).coerceAtLeast(0),
                    destructionPercentage = (a.destructionPercentage ?: 0).coerceIn(0, 100)
                )
            }
            val attacks = rawAttacks + (1..slotCount)
                .filterNot { order -> rawAttacks.any { it.attackOrder == order } }
                .map { Attack(attackOrder = it, destructionPercentage = 0) }
            MemberEntity(
                id = "$eventId#$rank",
                eventId = eventId,
                rank = rank,
                playerName = playerName,
                role = role,
                totalStars = (m.totalStars ?: 0).coerceAtLeast(0),
                attacks = attacks
            )
        }

        // 同名去重：按 rank 顺序，重名者依次编号「原名」「原名1」「原名2」
        val deduped = deduplicateNames(members)

        // 我方总星数 = 所有成员 total_stars 之和
        val clanStars = deduped.sumOf { it.totalStars }.coerceAtLeast(0)
        // 我方总摧毁率 = 已使用攻击（摧毁率 > 0）的平均摧毁率
        val usedAttacks = deduped.flatMap { it.attacks }.filter { it.isUsed() }
        val clanDestructionAvg = if (usedAttacks.isNotEmpty())
            usedAttacks.map { it.destructionPercentage }.average() else 0.0

        val event = WarEventEntity(
            eventId = eventId,
            eventName = "",  // 用户导入时填写
            eventType = eventType,
            eventRound = round,
            clanTotalStars = clanStars,
            clanTotalDestruction = "%.1f%%".format(clanDestructionAvg),
            isSample = isSample,
            createdAt = createdAt
        )

        return ParsedEvent(event, deduped)
    }
}
