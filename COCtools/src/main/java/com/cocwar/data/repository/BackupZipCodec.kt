package com.cocwar.data.repository

import com.cocwar.data.csv.CsvCodec
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.db.RosterDao
import com.cocwar.data.db.WarDao
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份编解码与完整还原（导出文件 / 云端同步 / 文件导入共用格式）。
 *
 * 格式：ZIP 容器内含三个 UTF-8 CSV（均带 BOM，Excel/WPS 可直接打开）：
 * - `events.csv`  —— 事件元数据（事件ID/事件名/类型/轮次/总星数/总摧毁率/是否样例/创建时间）
 * - `members.csv` —— 成员（事件ID/排名/成员名/职位/总星数/进攻1摧毁率/进攻2摧毁率）
 * - `roster.csv`  —— 花名册（昵称/职位/在册）
 *
 * 事件 ID 保留原值，还原时原样重建实体，无损 round-trip，不重新解析/重算。
 */
class BackupZipCodec(
    private val dao: WarDao,
    private val rosterDao: RosterDao
) {

    // ==================== 导出 ====================

    /** 导出所有数据（事件 + 成员 + 花名册）为 ZIP 字节。 */
    suspend fun exportAllData(): ByteArray {
        val allEvents = dao.getAllEvents()
        val roster = rosterDao.getAll()
        val allEventIds = allEvents.map { it.eventId }
        val allMembers = if (allEventIds.isEmpty()) emptyList()
        else dao.getMembersByEventIds(allEventIds)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            writeEntry(zip, "events.csv", buildEventsCsv(allEvents))
            writeEntry(zip, "members.csv", buildMembersCsv(allMembers))
            writeEntry(zip, "roster.csv", buildRosterCsv(roster))
        }
        return baos.toByteArray()
    }

    // ==================== 校验 ====================

    /** 校验备份 ZIP 是否合法（必须含 events.csv 且至少一行事件）；非法返回 false。 */
    fun validateBackup(bytes: ByteArray): Boolean {
        return try {
            val entries = readZip(bytes)
            val eventsCsv = entries["events.csv"] ?: return false
            val rows = parseCsv(eventsCsv)
            // 表头 + 至少一行事件
            rows.size >= 2
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 还原 ====================

    /**
     * 解析备份 ZIP 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册替换）。
     * 任一事件损坏或备份无事件则抛异常且不触碰本地数据，避免「假成功」导致数据清空却未还原。
     */
    suspend fun restoreFromBackup(bytes: ByteArray) {
        val entries = readZip(bytes)

        val eventsCsv = entries["events.csv"] ?: throw IllegalStateException("备份缺少 events.csv")
        val membersCsv = entries["members.csv"].orEmpty()
        val rosterCsv = entries["roster.csv"].orEmpty()

        // 第一步：解析事件与成员（任一损坏则整体放弃，不碰本地数据）
        val events = parseEvents(eventsCsv)
        if (events.isEmpty()) {
            throw IllegalStateException("备份中未包含任何战报数据，已中止还原（本地数据未改动）")
        }
        val members = parseMembers(membersCsv, events)

        // 第二步：全部解析成功后才清空本地并写入
        dao.clearAll()

        // 恢复花名册：仅当备份明确包含花名册数据时才替换
        if (rosterCsv.isNotBlank()) {
            // 离队语义为「直接删除」：备份里标记为已离队的成员不再还原回花名册
            val roster = parseRoster(rosterCsv).filter { it.active }
            if (roster.isNotEmpty()) {
                rosterDao.clearAll()
                rosterDao.insertAll(roster)
            }
        }

        events.forEach { dao.insertEvent(it, members[it.eventId] ?: emptyList()) }
    }

    // ==================== CSV 构建 ====================

    private fun buildEventsCsv(events: List<WarEventEntity>): String {
        val sb = StringBuilder(CsvCodec.BOM)
        sb.append(CsvCodec.row(listOf(
            "事件ID", "事件名", "类型", "轮次", "总星数", "总摧毁率", "是否样例", "创建时间"
        ))).append("\n")
        events.sortedBy { it.createdAt }.forEach { ev ->
            sb.append(CsvCodec.row(listOf(
                ev.eventId, ev.eventName, ev.eventType, "${ev.eventRound}",
                "${ev.clanTotalStars}", ev.clanTotalDestruction,
                if (ev.isSample) "1" else "0", "${ev.createdAt}"
            ))).append("\n")
        }
        return sb.toString()
    }

    private fun buildMembersCsv(members: List<MemberEntity>): String {
        val sb = StringBuilder(CsvCodec.BOM)
        sb.append(CsvCodec.row(listOf(
            "事件ID", "排名", "成员名", "职位", "总星数", "进攻1摧毁率", "进攻2摧毁率"
        ))).append("\n")
        members.sortedWith(compareBy({ it.eventId }, { it.rank })).forEach { m ->
            val d1 = m.attacks.firstOrNull { it.attackOrder == 1 }?.destructionPercentage
            val d2 = m.attacks.firstOrNull { it.attackOrder == 2 }?.destructionPercentage
            sb.append(CsvCodec.row(listOf(
                m.eventId, "${m.rank}", m.playerName, m.role, "${m.totalStars}",
                d1?.let { "$it" } ?: "", d2?.let { "$it" } ?: ""
            ))).append("\n")
        }
        return sb.toString()
    }

    private fun buildRosterCsv(roster: List<MemberRosterEntity>): String {
        val sb = StringBuilder(CsvCodec.BOM)
        sb.append(CsvCodec.row(listOf("昵称", "职位", "在册"))).append("\n")
        roster.forEach { e ->
            sb.append(CsvCodec.row(listOf(
                e.name, e.role, if (e.active) "1" else "0"
            ))).append("\n")
        }
        return sb.toString()
    }

    // ==================== CSV 解析 ====================

    private fun parseEvents(csv: String): List<WarEventEntity> {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return emptyList()
        // 首行为表头
        return rows.drop(1).mapNotNull { cells ->
            val eventId = cells.getOrNull(0)?.trim().orEmpty()
            if (eventId.isBlank()) return@mapNotNull null
            WarEventEntity(
                eventId = eventId,
                eventName = cells.getOrNull(1)?.trim().orEmpty(),
                eventType = cells.getOrNull(2)?.trim().orEmpty(),
                eventRound = cells.getOrNull(3)?.trim()?.toIntOrNull() ?: 0,
                clanTotalStars = cells.getOrNull(4)?.trim()?.toIntOrNull() ?: 0,
                clanTotalDestruction = cells.getOrNull(5)?.trim().orEmpty(),
                isSample = cells.getOrNull(6)?.trim() == "1",
                createdAt = cells.getOrNull(7)?.trim()?.toLongOrNull() ?: 0L
            )
        }
    }

    private fun parseMembers(
        csv: String,
        events: List<WarEventEntity>
    ): Map<String, List<MemberEntity>> {
        if (csv.isBlank()) return emptyMap()
        val eventTypeById = events.associate { it.eventId to it.eventType }
        val rows = parseCsv(csv)
        val result = mutableMapOf<String, MutableList<MemberEntity>>()
        rows.drop(1).forEach { cells ->
            val eventId = cells.getOrNull(0)?.trim().orEmpty()
            if (eventId.isBlank()) return@forEach
            val playerName = cells.getOrNull(2)?.trim().orEmpty()
            if (playerName.isBlank()) return@forEach
            val slotCount = if (eventTypeById[eventId] == EVENT_TYPE_LEAGUE) 1 else 2
            val attacks = (1..slotCount).map { order ->
                val raw = cells.getOrNull(if (order == 1) 5 else 6)?.trim().orEmpty()
                Attack(
                    attackOrder = order,
                    destructionPercentage = raw.removeSuffix("%").toIntOrNull() ?: 0
                )
            }
            val member = MemberEntity(
                id = "$eventId#${cells.getOrNull(1)?.trim() ?: "0"}",
                eventId = eventId,
                rank = cells.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                playerName = playerName,
                role = cells.getOrNull(3)?.trim().orEmpty(),
                totalStars = cells.getOrNull(4)?.trim()?.toIntOrNull() ?: 0,
                attacks = attacks
            )
            result.getOrPut(eventId) { mutableListOf() }.add(member)
        }
        return result
    }

    private fun parseRoster(csv: String): List<MemberRosterEntity> {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return emptyList()
        return rows.drop(1).mapNotNull { cells ->
            val name = cells.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            MemberRosterEntity(
                name = name,
                role = cells.getOrNull(1)?.trim().orEmpty(),
                active = cells.getOrNull(2)?.trim() != "0"
            )
        }
    }

    // ==================== 工具 ====================

    private fun parseCsv(text: String): List<List<String>> =
        CsvCodec.parse(text.removePrefix(CsvCodec.BOM)).filter { row -> row.any { it.isNotBlank() } }

    private fun readZip(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zin.readBytes().toString(Charsets.UTF_8)
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        return entries
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
