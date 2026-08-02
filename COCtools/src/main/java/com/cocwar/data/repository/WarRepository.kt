package com.cocwar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.model.isUsed
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.samples.SampleDataProvider
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class WarRepository(
    private val database: WarDatabase,
    private val appContext: Context
) {
    private val dao = database.warDao()
    private val rosterDao = database.rosterDao()
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_war_prefs", Context.MODE_PRIVATE)

    val events: Flow<List<WarEventEntity>> = dao.observeEvents()

    fun getEvent(id: String): Flow<WarEventEntity?> = dao.observeEvent(id)
    fun getMembers(id: String): Flow<List<MemberEntity>> = dao.observeMembers(id)

    /** 一次性获取单个事件（删除前快照/撤销等场景）。 */
    suspend fun getEventById(id: String): WarEventEntity? = dao.getEventById(id)

    suspend fun importEvent(parsed: WarJsonParser.ParsedEvent) {
        // 职位一律以花名册为准：导入前按名字映射 role（解析阶段未传 rosterRoles 时在此兑底）
        val roleMap = rosterRoleMap()
        val mapped = if (roleMap.isEmpty()) parsed else parsed.copy(
            members = parsed.members.map {
                it.copy(role = roleMap[it.playerName] ?: it.role)
            }
        )
        dao.insertEvent(mapped.event, mapped.members)
    }

    suspend fun deleteEvent(id: String) {
        dao.deleteEvent(id)
    }

    /** 清空全部战报与成员（云端备份完整还原时使用）。 */
    suspend fun clearAllEvents() {
        dao.clearAll()
    }

    suspend fun getEventsInRange(start: Long, end: Long): List<WarEventEntity> =
        dao.getEventsInRange(start, end)

    suspend fun getMembersByEventIds(eventIds: List<String>): List<MemberEntity> =
        dao.getMembersByEventIds(eventIds)

    suspend fun getAllPlayerNames(): List<String> = dao.getAllPlayerNames()

    /** 一次性获取所有事件（用于统计页月份选择等）。 */
    suspend fun getAllEventsSync(): List<WarEventEntity> = dao.getAllEvents()

    // === 正式成员名单 (roster) ===

    /** 获取名单流（供 UI 订阅）。 */
    fun observeRoster(): Flow<List<MemberRosterEntity>> = rosterDao.observeAll()

    /** 一次性获取名单。 */
    suspend fun getRoster(): List<String> = rosterDao.getAll().map { it.name }

    /** 一次性获取名单（含职位）。 */
    suspend fun getRosterWithRoles(): List<MemberRosterEntity> = rosterDao.getAll()

    /** 花名册职位映射：名字 → role（职位以花名册为准）。 */
    suspend fun rosterRoleMap(): Map<String, String> =
        rosterDao.getAll().associate { it.name to it.role }

    /** 批量添加新成员到名单（默认职位：成员）。 */
    suspend fun addToRoster(names: List<String>) {
        rosterDao.insertAll(names.map { MemberRosterEntity(name = it.trim(), role = "member") })
    }

    /** 设置名单成员的职位（leader/coLeader/elder/member）。 */
    suspend fun updateRosterRole(name: String, role: String) {
        rosterDao.updateRole(name, role)
    }

    /** 从名单中删除。 */
    suspend fun removeFromRoster(name: String) {
        rosterDao.delete(name)
    }

    // === 更新操作 ===

    /** 更新战报名称，自动从名称重新解析 eventType/eventRound。 */
    suspend fun updateEventName(eventId: String, newName: String) {
        val ev = dao.getEventById(eventId) ?: return
        val (type, round) = parseTypeAndRound(newName, ev.eventType, ev.eventRound)
        dao.updateEvent(ev.copy(eventName = newName, eventType = type, eventRound = round))
    }

    /** 更新单个成员的进攻数据，并同步刷新事件的聚合统计。 */
    suspend fun updateMember(member: MemberEntity) {
        dao.updateMember(member)
        refreshEventStats(member.eventId)
    }

    /** 根据成员数据重新计算事件的总星数和总摧毁率。 */
    private suspend fun refreshEventStats(eventId: String) {
        val ev = dao.getEventById(eventId) ?: return
        val members = dao.getMembersByEventIds(listOf(eventId))
        val totalStars = members.sumOf { it.totalStars }
        val usedAttacks = members.flatMap { it.attacks }.filter { it.isUsed() }
        val totalDestruction = if (usedAttacks.isEmpty()) "0%"
        else {
            val avg = usedAttacks.map { it.destructionPercentage }.average()
            "%.1f%%".format(avg)
        }
        dao.updateEvent(ev.copy(
            clanTotalStars = totalStars,
            clanTotalDestruction = totalDestruction
        ))
    }

    // === 导出 ===

    /** 导出所有数据（事件 + 成员 + 花名册）为 JSON 字符串，用于备份。 */
    suspend fun exportAllDataJson(): String {
        val allEvents = dao.getAllEvents()
        val roster = getRosterWithRoles()
        val sb = StringBuilder()
        sb.append("{\n")

        // 花名册（含职位，与新版结构一致）
        sb.append("  \"roster\": [\n")
        roster.forEachIndexed { i, entry ->
            sb.append("    {\"name\": \"${escapeJson(entry.name)}\", \"role\": \"${escapeJson(entry.role)}\"}")
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

    /** 导出事件为 JSON 字符串。 */
    suspend fun exportEventJson(eventId: String): String {
        val ev = dao.getEventById(eventId) ?: throw IllegalStateException("事件不存在")
        val memberList = dao.getMembersByEventIds(listOf(eventId))
        return buildJson(ev, memberList)
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

    private fun escapeJson(s: String): String = buildString(s.length + 8) {
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

    /** 获取当月时间范围。 */
    fun currentMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    suspend fun ensureSamples() {
        val inserted = prefs.getBoolean(KEY_SAMPLES, false)
        if (inserted) return
        if (dao.countEvents() > 0) { prefs.edit().putBoolean(KEY_SAMPLES, true).apply(); return }
        val base = System.currentTimeMillis()
        val war = SampleDataProvider.warSample(base - 2_000)
        val league = SampleDataProvider.leagueSample(base - 1_000)
        dao.insertEvent(war.event, war.members)
        dao.insertEvent(league.event, league.members)
        prefs.edit().putBoolean(KEY_SAMPLES, true).apply()
    }

    suspend fun restoreSamples() {
        val base = System.currentTimeMillis()
        val war = SampleDataProvider.warSample(base - 2_000)
        val league = SampleDataProvider.leagueSample(base - 1_000)
        dao.insertEvent(war.event, war.members)
        dao.insertEvent(league.event, league.members)
        prefs.edit().putBoolean(KEY_SAMPLES, true).apply()
    }

    suspend fun generateEventName(eventType: String, eventRound: Int): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis; cal.add(Calendar.MONTH, 1)
        val prefix = if (eventType == "league") "1" else "0"
        val count = dao.countByTypeInMonth(eventType, monthStart, cal.timeInMillis)
        // 统一自增：根据本月已有同类型战报数量自动编号，切换类型时由调用方重新生成全名
        val seq = count + 1
        return "%s%02d%02d%02d".format(prefix, year, month, seq)
    }

    companion object { private const val KEY_SAMPLES = "samples_inserted" }

    /** 从 SAABBCC 格式名称解析类型和轮次；无法解析时保留原值。 */
    private fun parseTypeAndRound(name: String, fallbackType: String, fallbackRound: Int): Pair<String, Int> {
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
            // cc 合法范围 1..14（两场联赛各 7 轮），越界视为无法解析
            if (cc !in 1..14) return fallbackType to fallbackRound
            (cc - 1) % 7 + 1
        } else 0
        return type to round
    }

    // === 备份 JSON：校验与完整还原（导出文件/云端同步共用格式） ===

    /** 校验备份 JSON 是否为合法的备份结构（顶层含 roster 或 events）；非法返回 false。 */
    fun validateBackupJson(json: String): Boolean {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return false
        return try {
            val root = Gson().fromJson(trimmed, BackupData::class.java)
            root != null && (root.events != null || root.roster != null)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 解析备份 JSON 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册）。
     * 任一事件损坏则抛异常且不触碰本地数据，避免「假成功」导致数据清空却未还原。
     */
    suspend fun restoreFromBackupJson(json: String) {
        val root = Gson().fromJson(json, BackupData::class.java) ?: return

        // 第一步：解析全部事件并保留原始名称，任一事件解析失败则整体放弃（不碰本地数据）
        val parsedEvents = mutableListOf<WarJsonParser.ParsedEvent>()
        root.events?.forEach { eventDto ->
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
        clearAllEvents()

        // 恢复花名册：兼容新版对象数组（含职位）与旧版字符串数组（默认成员）
        val rosterEntries = root.roster
            ?.mapNotNull { e ->
                when {
                    e.isJsonPrimitive -> e.asString to "member"
                    e.isJsonObject -> {
                        val obj = e.asJsonObject
                        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: return@mapNotNull null
                        val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString ?: "member"
                        name to role
                    }
                    else -> null
                }
            }
            ?.filter { it.first.isNotBlank() }
            ?: emptyList()
        if (rosterEntries.isNotEmpty()) {
            addToRoster(rosterEntries.map { it.first })
            rosterEntries.forEach { (name, role) -> updateRosterRole(name, role) }
        }

        parsedEvents.forEach { restored ->
            importEvent(restored)
        }
    }
}

/** 备份 JSON 的顶层结构（导出文件 / 云端同步 / 文件导入共用格式）。
 *  roster 兼容两种形态：新版为 {"name":"..","role":".."} 对象数组，旧版为字符串数组（默认成员）。 */
private data class BackupData(
    val roster: List<JsonElement>? = null,
    val events: List<BackupEvent>? = null
)

private data class BackupEvent(
    val event_name: String? = null,
    val event_type: String? = null,
    val event_round: Int? = 0,
    val clan_total_stars: Int? = 0,
    val clan_total_destruction: String? = "0%",
    val created_at: Long? = 0,
    val is_sample: Boolean? = null,
    val members: List<BackupMember>? = null
) {
    /** 将备份格式转换为 WarJsonParser 可解析的精简导入 JSON 格式（旧备份中的 rank/role/status 键由 Gson 自然忽略） */
    fun toWarJson(): String {
        val sb = StringBuilder()
        sb.append("{\n  \"members\": [\n")
        members?.forEachIndexed { i, m ->
            sb.append("    {\n")
            sb.append("      \"player_name\": \"${escape(m.player_name ?: "")}\",\n")
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

    private fun escape(s: String): String = buildString(s.length + 8) {
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
}

private data class BackupMember(
    val player_name: String? = null,
    val total_stars: Int? = 0,
    val attacks: List<BackupAttack>? = null
)

private data class BackupAttack(
    val attack_order: Int? = 0,
    val destruction_percentage: Int? = 0
)
