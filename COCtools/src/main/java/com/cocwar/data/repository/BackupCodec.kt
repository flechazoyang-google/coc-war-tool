package com.cocwar.data.repository

import com.cocwar.data.db.RosterDao
import com.cocwar.data.db.WarDao
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.parser.WarJsonParser
import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * 备份 JSON 编解码与完整还原（导出文件 / 云端同步 / 文件导入共用格式）。
 * 序列化保持与原手工拼接完全一致的字段名与结构，保证新旧备份互兼容。
 */
class BackupCodec(
    private val dao: WarDao,
    private val rosterDao: RosterDao,
    /** 导入单场事件（复用 repository 的角色映射等逻辑）。 */
    private val importEvent: suspend (WarJsonParser.ParsedEvent) -> Unit
) {

    /** 导出所有数据（事件 + 成员 + 花名册）为 JSON 字符串，用于备份。 */
    suspend fun exportAllDataJson(): String {
        val allEvents = dao.getAllEvents()
        val roster = rosterDao.getAll()
        val sb = StringBuilder()
        sb.append("{\n")

        // 花名册（含职位与在册状态，与新版结构一致）
        sb.append("  \"roster\": [\n")
        roster.forEachIndexed { i, entry ->
            sb.append(
                "    {\"name\": \"${escapeJson(entry.name)}\", " +
                    "\"role\": \"${escapeJson(entry.role)}\", \"active\": ${entry.active}}"
            )
            if (i < roster.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        // 事件列表
        sb.append("  \"events\": [\n")
        if (allEvents.isEmpty()) {
            sb.append("  ]\n")
        } else {
            val allEventIds = allEvents.map { it.eventId }
            val allMembers = dao.getMembersByEventIds(allEventIds)
            val membersByEvent = allMembers.groupBy { it.eventId }
            allEvents.forEachIndexed { i, event ->
                val members = membersByEvent[event.eventId] ?: emptyList()
                sb.append("    {\n")
                sb.append("      \"event_name\": \"${escapeJson(event.eventName)}\",\n")
                sb.append("      \"event_type\": \"${escapeJson(event.eventType)}\",\n")
                sb.append("      \"event_round\": ${event.eventRound},\n")
                sb.append("      \"clan_total_stars\": ${event.clanTotalStars},\n")
                sb.append("      \"clan_total_destruction\": \"${escapeJson(event.clanTotalDestruction)}\",\n")
                sb.append("      \"created_at\": ${event.createdAt},\n")
                sb.append("      \"is_sample\": ${event.isSample},\n")
                sb.append("      \"members\": [\n")
                members.forEachIndexed { j, m ->
                    sb.append("        {\n")
                    sb.append("          \"player_name\": \"${escapeJson(m.playerName)}\",\n")
                    sb.append("          \"rank\": ${m.rank},\n")
                    sb.append("          \"role\": \"${escapeJson(m.role)}\",\n")
                    sb.append("          \"total_stars\": ${m.totalStars},\n")
                    sb.append("          \"attacks\": [\n")
                    m.attacks.forEachIndexed { k, a ->
                        sb.append("            {\n")
                        sb.append("              \"attack_order\": ${a.attackOrder},\n")
                        sb.append("              \"destruction_percentage\": ${a.destructionPercentage}\n")
                        sb.append("            }${if (k < m.attacks.lastIndex) "," else ""}\n")
                    }
                    sb.append("          ]\n")
                    sb.append("        }${if (j < members.lastIndex) "," else ""}\n")
                }
                sb.append("      ]\n")
                sb.append("    }${if (i < allEvents.lastIndex) "," else ""}\n")
            }
            sb.append("  ]\n")
        }
        sb.append("}")
        return sb.toString()
    }

    /** 导出单场事件为 JSON 字符串。 */
    suspend fun exportEventJson(eventId: String): String {
        val ev = dao.getEventById(eventId) ?: throw IllegalStateException("事件不存在")
        val memberList = dao.getMembersByEventIds(listOf(eventId))
        return buildJson(ev, memberList)
    }

    /** 校验备份 JSON 是否为合法的备份结构（必须含 events 数组）；非法返回 false。 */
    fun validateBackupJson(json: String): Boolean {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return false
        return try {
            val root = Gson().fromJson(trimmed, BackupData::class.java)
            root != null && root.events != null && root.events.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析备份 JSON 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册替换）。
     * 任一事件损坏或备份无事件则抛异常且不触碰本地数据，避免「假成功」导致数据清空却未还原。
     */
    suspend fun restoreFromBackupJson(json: String) {
        val root = runCatching {
            Gson().fromJson(json, BackupData::class.java)
        }.getOrElse { e ->
            throw IllegalStateException("备份 JSON 格式错误：${e.message}", e)
        } ?: throw IllegalStateException("备份 JSON 解析结果为空")

        // 必须包含事件才允许还原，避免仅含花名册的备份清空全部战报
        if (root.events.isNullOrEmpty()) {
            throw IllegalStateException("备份中未包含任何战报数据，已中止还原（本地数据未改动）")
        }

        // 第一步：解析全部事件并保留原始名称，任一事件解析失败则整体放弃（不碰本地数据）
        val parsedEvents = mutableListOf<WarJsonParser.ParsedEvent>()
        root.events.forEach { eventDto ->
            val parsed = WarJsonParser.parse(
                eventDto.toWarJson(),
                isSample = eventDto.is_sample == true,
                createdAt = eventDto.created_at?.takeIf { it > 0 } ?: System.currentTimeMillis(),
                eventType = eventDto.event_type ?: EVENT_TYPE_WAR,
                eventRound = eventDto.event_round ?: 0
            )
            if (parsed is WarJsonParser.ParseResult.Success) {
                // 用备份里的原始名称覆盖，避免被重置为空
                val src = parsed.data
                parsedEvents += src.copy(
                    event = src.event.copy(eventName = eventDto.event_name ?: src.event.eventName)
                )
            } else {
                throw IllegalStateException("备份中某场战报数据损坏，已中止还原（本地数据未改动）")
            }
        }

        // 第二步：全部解析成功后才清空本地并写入
        dao.clearAll()

        // 恢复花名册：仅当备份明确包含花名册数据时才清空并替换；否则保留本地花名册不变
        val rosterJson = root.roster
        if (rosterJson != null) {
            val entries = rosterJson.mapNotNull { e ->
                when {
                    // 旧版字符串数组：默认成员、在册
                    e.isJsonPrimitive -> Triple(e.asString, "member", true)
                    e.isJsonObject -> {
                        val obj = e.asJsonObject
                        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                        val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString ?: "member"
                        // 旧版对象无 active 字段：默认在册
                        val active = obj.get("active")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                        Triple(name, role, active)
                    }
                    else -> null
                }
            }.filter { it.first.isNotBlank() }
            if (entries.isNotEmpty()) {
                rosterDao.clearAll()
                rosterDao.insertAll(entries.map {
                    com.cocwar.data.db.MemberRosterEntity(name = it.first, role = it.second, active = it.third)
                })
            }
        }

        parsedEvents.forEach { restored ->
            importEvent(restored)
        }
    }

    private fun buildJson(event: WarEventEntity, members: List<MemberEntity>): String {
        val sb = StringBuilder()
        sb.append("{\n  \"members\": [\n")
        members.forEachIndexed { i, m ->
            sb.append("    {\n")
            sb.append("      \"player_name\": \"${escapeJson(m.playerName)}\",\n")
            sb.append("      \"total_stars\": ${m.totalStars},\n")
            sb.append("      \"attacks\": [\n")
            m.attacks.forEachIndexed { j, a ->
                sb.append("        {\n")
                sb.append("          \"attack_order\": ${a.attackOrder},\n")
                sb.append("          \"destruction_percentage\": ${a.destructionPercentage}\n")
                sb.append("        }${if (j < m.attacks.lastIndex) "," else ""}\n")
            }
            sb.append("      ]\n")
            sb.append("    }${if (i < members.lastIndex) "," else ""}\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }
}

/** JSON 字符串转义（备份导出与 DTO 往返共用，与原实现一致）。 */
internal fun escapeJson(s: String): String = buildString(s.length + 8) {
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (c < '\u0020') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

/** 备份 JSON 的顶层结构（导出文件 / 云端同步 / 文件导入共用格式）。
 *  roster 兼容两种形态：新版为 {"name":"..","role":".."} 对象数组，旧版为字符串数组（默认成员）。 */
internal data class BackupData(
    val roster: List<JsonElement>? = null,
    val events: List<BackupEvent>? = null
)

internal data class BackupEvent(
    val event_name: String? = null,
    val event_type: String? = null,
    val event_round: Int? = 0,
    val clan_total_stars: Int? = 0,
    val clan_total_destruction: String? = "0%",
    val created_at: Long? = 0,
    val is_sample: Boolean? = null,
    val members: List<BackupMember>? = null
) {
    /** 将备份格式转换为 WarJsonParser 可解析的导入 JSON 格式（含 rank 以保持双程往返一致性）。 */
    fun toWarJson(): String {
        val sb = StringBuilder()
        sb.append("{\n  \"members\": [\n")
        members?.forEachIndexed { i, m ->
            sb.append("    {\n")
            sb.append("      \"player_name\": \"${escapeJson(m.player_name ?: "")}\",\n")
            if (m.rank != null) sb.append("      \"rank\": ${m.rank},\n")
            sb.append("      \"total_stars\": ${m.total_stars ?: 0},\n")
            sb.append("      \"attacks\": [\n")
            m.attacks?.forEachIndexed { j, a ->
                sb.append("        {\n")
                sb.append("          \"attack_order\": ${a.attack_order ?: 0},\n")
                sb.append("          \"destruction_percentage\": ${a.destruction_percentage ?: 0}\n")
                sb.append("        }${if (j < (m.attacks?.lastIndex ?: 0)) "," else ""}\n")
            }
            sb.append("      ]\n")
            sb.append("    }${if (i < (members?.lastIndex ?: 0)) "," else ""}\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }
}

internal data class BackupMember(
    val player_name: String? = null,
    val rank: Int? = null,
    val role: String? = null,
    val total_stars: Int? = 0,
    val attacks: List<BackupAttack>? = null
)

internal data class BackupAttack(
    val attack_order: Int? = 0,
    val destruction_percentage: Int? = 0
)
