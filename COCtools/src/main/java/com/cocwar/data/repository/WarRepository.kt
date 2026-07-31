package com.cocwar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.samples.SampleDataProvider
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

    suspend fun importEvent(parsed: WarJsonParser.ParsedEvent) {
        dao.insertEvent(parsed.event, parsed.members)
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

    /** 批量添加新成员到名单。 */
    suspend fun addToRoster(names: List<String>) {
        rosterDao.insertAll(names.map { MemberRosterEntity(it.trim()) })
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
        val usedAttacks = members.flatMap { it.attacks }.filter { it.status == "used" }
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
        val roster = getRoster()
        val sb = StringBuilder()
        sb.append("{\n")

        // 花名册
        sb.append("  \"roster\": [\n")
        roster.forEachIndexed { i, name ->
            sb.append("    \"${escapeJson(name)}\"")
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
                sb.append("      \"members\": [\n")
                members.forEachIndexed { j, m ->
                    sb.append("        {\n")
                    sb.append("          \"rank\": ${m.rank},\n")
                    sb.append("          \"player_name\": \"${escapeJson(m.playerName)}\",\n")
                    sb.append("          \"role\": \"${escapeJson(m.role)}\",\n")
                    sb.append("          \"total_stars\": ${m.totalStars},\n")
                    sb.append("          \"attacks\": [\n")
                    m.attacks.forEachIndexed { k, a ->
                        sb.append("            {\n")
                        sb.append("              \"attack_order\": ${a.attackOrder},\n")
                        sb.append("              \"status\": \"${escapeJson(a.status)}\",\n")
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
            sb.append("      \"rank\": ${m.rank},\n")
            sb.append("      \"player_name\": \"${escapeJson(m.playerName)}\",\n")
            sb.append("      \"role\": \"${escapeJson(m.role)}\",\n")
            sb.append("      \"total_stars\": ${m.totalStars},\n")
            sb.append("      \"attacks\": [\n")
            m.attacks.forEachIndexed { j, a ->
                sb.append("        {\n")
                sb.append("          \"attack_order\": ${a.attackOrder},\n")
                sb.append("          \"status\": \"${escapeJson(a.status)}\",\n")
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
}
