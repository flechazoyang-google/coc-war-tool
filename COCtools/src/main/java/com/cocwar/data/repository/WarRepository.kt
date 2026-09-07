package com.cocwar.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.model.ParsedEvent
import com.cocwar.data.samples.SampleDataProvider
import com.cocwar.domain.MemberMerge
import com.cocwar.domain.RosterEntry
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

/**
 * 事件/成员/名单的统一数据入口。按职责拆分为三部分：
 * - 本类：CRUD、名单、更新、同步、示例数据（与 DAO 交互）；
 * - [BackupZipCodec]：备份 ZIP 导出/校验/还原；
 * - [EventNamingRules]：SAABBCC 命名规则纯函数。
 */
class WarRepository(
    private val database: WarDatabase,
    private val appContext: Context
) {
    private val dao = database.warDao()
    private val rosterDao = database.rosterDao()
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_war_prefs", Context.MODE_PRIVATE)

    /** 备份编解码：导出 / 校验 / 还原（ZIP 多 CSV 格式）。 */
    private val backupCodec = BackupZipCodec(dao, rosterDao)

    val events: Flow<List<WarEventEntity>> = dao.observeEvents()

    fun getEvent(id: String): Flow<WarEventEntity?> = dao.observeEvent(id)
    fun getMembers(id: String): Flow<List<MemberEntity>> = dao.observeMembers(id)

    /** 一次性获取单个事件（删除前快照/撤销等场景）。 */
    suspend fun getEventById(id: String): WarEventEntity? = dao.getEventById(id)

    suspend fun importEvent(parsed: ParsedEvent) {
        // 同名多行先合并为一行（OCR 错名映射到同一在册成员等场景），再按花名册映射职位
        val deduped = MemberMerge.dedupeByName(parsed.members, parsed.event.eventType)
        val roleMap = rosterRoleMap()
        val mapped = if (roleMap.isEmpty()) deduped else deduped.map {
            it.copy(role = roleMap[it.playerName] ?: it.role)
        }
        val event = if (deduped.size == parsed.members.size) parsed.event
        else MemberMerge.recomputeTotals(parsed.event, mapped)
        dao.insertEvent(event, mapped)
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

    /**
     * 计算某成员距离上次参战（出现在部落战名单，无论是否进攻）已连续缺席的部落战场次。
     * 「参加了但没进攻」仍算参战；从未参战返回全部部落战场次；无部落战数据返回 0。
     */
    suspend fun getWarAbsentCount(name: String): Int {
        val warEvents = dao.getAllEvents().filter { it.eventType != "league" }.sortedBy { it.createdAt }
        if (warEvents.isEmpty()) return 0
        val participated = dao.getWarEventIdsByPlayerName(name).toSet()
        val lastIndex = warEvents.indexOfLast { it.eventId in participated }
        return warEvents.size - 1 - lastIndex
    }

    /**
     * 批量计算多个成员「距离上次参战已连续缺席的部落战场次」（name → count），
     * 语义与 [getWarAbsentCount] 完全一致，但一次查询完成；同时带出部落战场次总数，
     * 供「疑似离队」判定（count == totalWarCount 表示从未参战，不误报新成员）。
     */
    suspend fun getWarAbsentInfo(names: Collection<String>): WarAbsentInfo {
        val warEvents = dao.getAllEvents().filter { it.eventType != "league" }.sortedBy { it.createdAt }
        if (warEvents.isEmpty()) return WarAbsentInfo(names.associateWith { 0 }, 0)
        val members = dao.getMembersByEventIds(warEvents.map { it.eventId })
        val participated = members.groupBy { it.playerName }
            .mapValues { (_, ms) -> ms.map { it.eventId }.toSet() }
        val counts = names.associateWith { name ->
            val lastIndex = warEvents.indexOfLast { it.eventId in (participated[name] ?: emptySet()) }
            warEvents.size - 1 - lastIndex
        }
        return WarAbsentInfo(counts, warEvents.size)
    }

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

    /**
     * 清除旧版本遗留的「已离队」行（active = 0）。
     * 离队改为直接删除后，花名册里的每一行都是当前成员，这些历史行应当被真正删除。
     * 幂等，可重复调用；每次启动执行一次。
     */
    suspend fun purgeDepartedRows() {
        rosterDao.deleteInactive()
    }

    // === 花名册维护设置 ===

    /** 疑似离队判定阈值：连续缺席部落战 ≥ N 场，默认 3，范围 1..10。 */
    fun suspectThreshold(): Int =
        prefs.getInt(KEY_SUSPECT_THRESHOLD, DEFAULT_SUSPECT_THRESHOLD)
            .coerceIn(SUSPECT_THRESHOLD_MIN, SUSPECT_THRESHOLD_MAX)

    fun setSuspectThreshold(n: Int) {
        prefs.edit().putInt(KEY_SUSPECT_THRESHOLD, n.coerceIn(SUSPECT_THRESHOLD_MIN, SUSPECT_THRESHOLD_MAX)).apply()
    }

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

    /** 撤销删除：把成员连同职位重新写回花名册（已存在的同名条目按新职位覆盖）。 */
    suspend fun restoreRoster(entries: List<MemberRosterEntity>) {
        if (entries.isEmpty()) return
        rosterDao.upsertAll(entries)
    }

    /**
     * 更新花名册（硬替换）：新名单 upsert（职位以新名单为准），不在新名单的**直接删除**。
     * 空名单直接返回——避免误粘贴把整份花名册清空。
     */
    suspend fun replaceRoster(entries: List<RosterEntry>) {
        if (entries.isEmpty()) return
        rosterDao.hardReplace(entries.map { MemberRosterEntity(name = it.name, role = it.role, active = true) })
    }

    // === 更新操作 ===

    /** 更新战报名称，自动从名称重新解析 eventType/eventRound。 */
    suspend fun updateEventName(eventId: String, newName: String) {
        val ev = dao.getEventById(eventId) ?: return
        val (type, round) = EventNamingRules.parseTypeAndRound(newName, ev.eventType, ev.eventRound)
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
        dao.updateEvent(MemberMerge.recomputeTotals(ev, members))
    }

    // === 成员名修正（OCR 错名善后） ===

    /** 某名字在全部战报中的成员行数（全局合并前的影响面预估）。 */
    suspend fun countMemberRows(name: String): Int = dao.countRowsByName(name)

    /**
     * 事件内成员改名；新名在本事件已存在时两行按同一人合并（保留已存在行的 id/排名）。
     * 返回是否发生了合并，供 UI 提示。只影响本场战报，不触碰花名册与其他战报。
     */
    suspend fun renameMemberInEvent(eventId: String, memberId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val members = dao.getMembersByEventIds(listOf(eventId))
        val target = members.find { it.id == memberId } ?: return false
        if (trimmed == target.playerName) return false
        val ev = dao.getEventById(eventId) ?: return false
        val duplicate = members.find { it.id != memberId && it.playerName == trimmed }
        return database.withTransaction {
            if (duplicate != null) {
                dao.updateMember(MemberMerge.mergeRows(duplicate, target, MemberMerge.maxStarsFor(ev.eventType)))
                dao.deleteMemberRow(target.id)
            } else {
                dao.updateMember(target.copy(playerName = trimmed))
            }
            refreshEventStats(eventId)
            duplicate != null
        }
    }

    /**
     * 全局合并：把所有战报中 [fromName] 的成员行并入 [toName]——同场两者都有则两行合一，
     * 只有 from 则直接改名；同名多行也一并合并。花名册同步处理：两者都在册时移除 from
     * （职位以 to 为准），仅 from 在册时条目改名保留职位。返回受影响的成员行数。
     */
    suspend fun mergeMembersGlobally(fromName: String, toName: String): Int {
        if (fromName == toName) return 0
        val events = dao.getAllEvents()
        val allMembers = if (events.isEmpty()) emptyList()
        else dao.getMembersByEventIds(events.map { it.eventId })
        val affectedEventIds = allMembers.filter { it.playerName == fromName }
            .map { it.eventId }.toSet()
        val byEvent = allMembers.filter { it.eventId in affectedEventIds }.groupBy { it.eventId }
        return database.withTransaction {
            var renamed = 0
            for ((eventId, members) in byEvent) {
                val ev = events.find { it.eventId == eventId } ?: continue
                val maxStars = MemberMerge.maxStarsFor(ev.eventType)
                val fromRows = members.filter { it.playerName == fromName }
                val toRow = members.find { it.playerName == toName }
                when {
                    toRow != null -> {
                        var merged: MemberEntity = toRow
                        for (row in fromRows) merged = MemberMerge.mergeRows(merged, row, maxStars)
                        dao.updateMember(merged)
                        fromRows.forEach { dao.deleteMemberRow(it.id) }
                    }
                    fromRows.size == 1 -> dao.updateMember(fromRows.single().copy(playerName = toName))
                    else -> {
                        var merged = fromRows.first().copy(playerName = toName)
                        for (row in fromRows.drop(1)) merged = MemberMerge.mergeRows(merged, row, maxStars)
                        dao.updateMember(merged)
                        fromRows.drop(1).forEach { dao.deleteMemberRow(it.id) }
                    }
                }
                renamed += fromRows.size
                refreshEventStats(eventId)
            }
            val roster = rosterDao.getAll()
            val fromEntry = roster.find { it.name == fromName }
            val toEntry = roster.find { it.name == toName }
            when {
                fromEntry != null && toEntry != null -> rosterDao.delete(fromName)
                fromEntry != null -> rosterDao.rename(fromName, toName)
            }
            // 合并已解决该名字的体检问题；若曾被忽略则解除，同名再次出现时重新提示
            val ignored = healthCheckIgnored()
            if (fromName in ignored) {
                prefs.edit().putStringSet(KEY_HEALTH_IGNORED, ignored - fromName).apply()
            }
            renamed
        }
    }

    // === 数据体检 ===

    /** 各名字在非示例战报中的成员行数（数据体检用）。 */
    suspend fun getMemberRowCounts(): Map<String, Int> =
        dao.getMemberRowCounts().associate { it.name to it.cnt }

    /** 数据体检已忽略的可疑名字（不再提示）。 */
    fun healthCheckIgnored(): Set<String> =
        prefs.getStringSet(KEY_HEALTH_IGNORED, emptySet()) ?: emptySet()

    /** 忽略数据体检中的某可疑名字。 */
    fun ignoreHealthCheckName(name: String) {
        prefs.edit().putStringSet(KEY_HEALTH_IGNORED, healthCheckIgnored() + name).apply()
    }

    // === 导出 ===

    /** 导出所有数据（事件 + 成员 + 花名册）为 ZIP 字节，用于备份。 */
    suspend fun exportAllData(): ByteArray = backupCodec.exportAllData()

    /** 导出单场事件为 CSV 文本（复用全量宽表格式）。 */
    suspend fun exportEventCsv(eventId: String): String {
        val ev = dao.getEventById(eventId) ?: throw IllegalStateException("事件不存在")
        val members = dao.getMembersByEventIds(listOf(eventId))
        return com.cocwar.data.csv.CsvExporter.exportEventsCsv(listOf(ev), mapOf(eventId to members))
    }

    /** 导出全量 CSV 宽表（B2，RULES §4.14）：事件×成员，UTF-8 + BOM。 */
    suspend fun exportAllEventsCsv(): String {
        val allEvents = dao.getAllEvents()
        val allEventIds = allEvents.map { it.eventId }
        val allMembers = if (allEventIds.isEmpty()) emptyList()
        else dao.getMembersByEventIds(allEventIds)
        return com.cocwar.data.csv.CsvExporter.exportEventsCsv(
            allEvents,
            allMembers.groupBy { it.eventId }
        )
    }

    // === 同步（B3，RULES §6） ===

    /** 数据指纹：导出 ZIP 的 SHA-256，用于同步变更判定（两端算法一致）。 */
    suspend fun dataFingerprint(): String {
        val bytes = exportAllData()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 本地是否有数据（事件或花名册任一非空）。 */
    suspend fun hasAnyData(): Boolean =
        dao.countEvents() > 0 || rosterDao.getAll().isNotEmpty()

    /** 本地归档（冲突时采用云端前保存本地版本），返回归档文件路径。 */
    suspend fun saveLocalSyncBackup(bytes: ByteArray): String {
        val dir = java.io.File(appContext.filesDir, "backups").apply { mkdirs() }
        val name = "sync_backup_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                .format(java.util.Date()) + ".zip"
        val file = java.io.File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
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

    /**
     * 自动生成 SAABBCC 事件名：S(类型) + AA(年) + BB(月) + CC(序号/轮次编码)。
     * CC 段计算与规则见 [EventNamingRules]。
     */
    suspend fun generateEventName(eventType: String, eventRound: Int, calendar: Calendar? = null): String {
        val cal = calendar ?: Calendar.getInstance()
        val year = cal.get(Calendar.YEAR) % 100
        val month = cal.get(Calendar.MONTH) + 1
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis; cal.add(Calendar.MONTH, 1)
        val prefix = if (eventType == "league") "1" else "0"

        // 按名称 S 位统计本月同类型事件（RULES §3：名称 S 位是类型权威），不依赖实体
        // eventType 字段——避免名称与字段不一致（历史/异常数据）把两类场次混在一起计数；
        // 非标准名（如示例数据）不参与计数。
        val monthNames = dao.getEventNamesInMonth(monthStart, cal.timeInMillis)
        val cc = EventNamingRules.computeCC(monthNames, eventType, eventRound)
        return "%s%02d%02d%02d".format(prefix, year, month, cc)
    }

    // === 备份 ZIP：校验与完整还原（导出文件/云端同步共用格式） ===

    /** 校验备份 ZIP 是否为合法的备份结构（必须含 events.csv 且至少一场事件）；非法返回 false。 */
    fun validateBackup(bytes: ByteArray): Boolean = backupCodec.validateBackup(bytes)

    /**
     * 解析备份 ZIP 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册替换）。
     * 任一事件损坏或备份无事件则抛异常且不触碰本地数据，避免「假成功」导致数据清空却未还原。
     */
    suspend fun restoreFromBackup(bytes: ByteArray) {
        backupCodec.restoreFromBackup(bytes)
    }

    companion object {
        private const val KEY_SAMPLES = "samples_inserted"
        private const val KEY_SUSPECT_THRESHOLD = "suspect_depart_threshold"
        private const val KEY_HEALTH_IGNORED = "health_check_ignored"
        private const val DEFAULT_SUSPECT_THRESHOLD = 3
        private const val SUSPECT_THRESHOLD_MIN = 1
        private const val SUSPECT_THRESHOLD_MAX = 10
    }
}

/** 批量连续缺席计算结果：各成员缺席场次 + 部落战场次总数（疑似离队判定用）。 */
data class WarAbsentInfo(
    val counts: Map<String, Int>,
    val totalWarCount: Int
)
