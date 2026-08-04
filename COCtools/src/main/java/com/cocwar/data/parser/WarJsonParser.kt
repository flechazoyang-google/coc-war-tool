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

        // event_id 自动生成；追加 nanoTime 防同一毫秒导入多个事件时 ID 碰撞
        val eventId = "${eventType}_${createdAt}_${System.nanoTime()}"
        // 每人进攻槽位：部落战 2 槽，联赛 1 槽（attackOrder 从 1 开始）。
        // 官方 CoC API 中未进攻成员没有 attacks 字段，这里补 unused 占位，
        // 使进攻率/未进攻统计口径正确；已有攻击记录保留原样。
        val slotCount = if (eventType == EVENT_TYPE_LEAGUE) 1 else 2

        // filterNotNull 防止 JSON 数组中 null 元素导致整个导入失败
        val members = (dto.members ?: emptyList()).filterNotNull().mapIndexed { index, m ->
            val rank = (m.rank ?: (index + 1)).coerceAtLeast(1)
            val playerName = m.playerName.clean() ?: "未知玩家#$rank"
            // 职位一律来自花名册（rosterRoles），JSON 中的 role 字段已不再使用
            val role = rosterRoles[playerName] ?: "member"
            // 精简结构：attack 只有 attack_order 与 destruction_percentage，
            // status 语义由摧毁率推导（>0 即已使用）；旧数据源的 status 字段在 DTO 层被忽略
            // filterNotNull 防止 attacks 数组中 null 元素导致整个导入失败
            val rawAttacks = (m.attacks ?: emptyList()).filterNotNull().map { a ->
                Attack(
                    attackOrder = (a.attackOrder ?: 0).coerceAtLeast(0),
                    destructionPercentage = (a.destructionPercentage ?: 0).coerceIn(0, 100)
                )
            }
            // 进攻顺序规范化（RULES §4.11）：attack_order 缺失或 ≤0 的记录按原始
            // 出现顺序重新编号（从 1 开始，跳过已被合法 order 占用的编号），
            // 防止不规范 JSON 中多条记录折叠为 order=0 后被去重合并丢失数据
            val occupiedOrders = rawAttacks.map { it.attackOrder }.filter { it > 0 }.toMutableSet()
            var nextOrder = 1
            val normalizedAttacks = rawAttacks.map { a ->
                if (a.attackOrder > 0) a
                else {
                    while (nextOrder in occupiedOrders) nextOrder++
                    occupiedOrders.add(nextOrder)
                    a.copy(attackOrder = nextOrder)
                }
            }
            // 去重：按 attackOrder 分组，每个 order 只保留摧毁率最高的一条（防止重复记录导致 used 超 slot）
            val dedupedAttacks = normalizedAttacks
                .groupBy { it.attackOrder }
                .map { (_, list) -> list.maxByOrNull { it.destructionPercentage }!! }
            val attacks = dedupedAttacks + (1..slotCount)
                .filterNot { order -> dedupedAttacks.any { it.attackOrder == order } }
                .map { Attack(attackOrder = it, destructionPercentage = 0) }
            // 用 index 确保主键唯一，避免 JSON 中重复 rank 导致成员被 REPLACE 静默覆盖
            MemberEntity(
                id = "$eventId#${index}",
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
            clanTotalDestruction = "%.1f%%".format(java.util.Locale.US, clanDestructionAvg),
            isSample = isSample,
            createdAt = createdAt
        )

        return ParsedEvent(event, deduped)
    }
}
