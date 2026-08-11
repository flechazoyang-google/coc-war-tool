package com.cocwar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.model.isUsed
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.samples.SampleDataProvider
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 事件/成员/名单的统一数据入口。按职责拆分为三部分：
 * - 本类：CRUD、名单、更新、同步、示例数据（与 DAO 交互）；
 * - [BackupCodec]：备份 JSON 导出/校验/还原；
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

    /** 备份编解码：导出 / 校验 / 还原（复用本类的 importEvent 角色映射）。 */
    private val backupCodec = BackupCodec(dao, rosterDao) { importEvent(it) }

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
        val totalStars = members.sumOf { it.totalStars }
        val usedAttacks = members.flatMap { it.attacks }.filter { it.isUsed() }
        val totalDestruction = if (usedAttacks.isEmpty()) "0%"
        else {
            val avg = usedAttacks.map { it.destructionPercentage }.average()
            "%.1f%%".format(java.util.Locale.US, avg)
        }
        dao.updateEvent(ev.copy(
            clanTotalStars = totalStars,
            clanTotalDestruction = totalDestruction
        ))
    }

    // === 导出 ===

    /** 导出所有数据（事件 + 成员 + 花名册）为 JSON 字符串，用于备份。 */
    suspend fun exportAllDataJson(): String = backupCodec.exportAllDataJson()

    /** 导出事件为 JSON 字符串。 */
    suspend fun exportEventJson(eventId: String): String = backupCodec.exportEventJson(eventId)

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

    /** 数据指纹：导出 JSON 的 SHA-256，用于同步变更判定（两端算法一致）。 */
    suspend fun dataFingerprint(): String {
        val json = exportAllDataJson()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 本地是否有数据（事件或花名册任一非空）。 */
    suspend fun hasAnyData(): Boolean =
        dao.countEvents() > 0 || rosterDao.getAll().isNotEmpty()

    /** 本地归档（冲突时采用云端前保存本地版本），返回归档文件路径。 */
    suspend fun saveLocalSyncBackup(json: String): String {
        val dir = java.io.File(appContext.filesDir, "backups").apply { mkdirs() }
        val name = "sync_backup_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                .format(java.util.Date()) + ".json"
        val file = java.io.File(dir, name)
        file.writeText(json, Charsets.UTF_8)
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
    suspend fun generateEventName(eventType: String, eventRound: Int): String {
        val cal = Calendar.getInstance()
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

    // === 备份 JSON：校验与完整还原（导出文件/云端同步共用格式） ===

    /** 校验备份 JSON 是否为合法的备份结构（必须含 events 数组）；非法返回 false。 */
    fun validateBackupJson(json: String): Boolean = backupCodec.validateBackupJson(json)

    /**
     * 解析备份 JSON 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册替换）。
     * 任一事件损坏或备份无事件则抛异常且不触碰本地数据，避免「假成功」导致数据清空却未还原。
     */
    suspend fun restoreFromBackupJson(json: String) {
        backupCodec.restoreFromBackupJson(json)
    }

    companion object { private const val KEY_SAMPLES = "samples_inserted" }
}
