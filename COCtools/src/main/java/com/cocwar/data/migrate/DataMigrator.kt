package com.cocwar.data.migrate

import com.cocwar.data.db.WarDao
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.repository.WarRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 单条迁移项：把旧编码联赛名称重编码为新的 C1C2 语义。 */
data class MigrationItem(
    val eventId: String,
    val oldName: String,
    val newName: String,
    val newRound: Int        // 新 C2 轮次（1..7）；溢出项为 0（名称显式无效 99）
)

/** 迁移计划：待迁移条目 + 溢出统计。 */
data class MigrationPlan(
    val items: List<MigrationItem>,
    val overflowCount: Int
)

/** 迁移结果：备份路径 + 迁移/跳过/溢出统计。 */
data class MigrationResult(
    val backupPath: String,
    val migrated: Int,
    val skipped: Int,
    val overflow: Int
)

/**
 * 数据迁移引擎：把历史版本按旧语义编码的联赛事件名修复为新 C1C2 编码。
 *
 * 背景：联赛 CC 编码经历过两次语义变迁——
 *  ① v3.9 及更早：CC = 当月第 N 场（自增，1..99）；
 *  ② v4.x 旧版：CC = 段偏移编码（1..7 月初场 / 8..14 月中场）；
 *  ③ 现版：CC = C1C2（C1=0 月初场/1 月中场，C2=轮次 1..7，合法值 01..07 / 11..17）。
 * 本引擎把不符合新合法集（名称本身是合法 SAABBCC、S=1 但 CC 非法）的联赛事件
 * 按月分组重编码：组内按 createdAt 升序，前 7 个 → 月初场（CC=01..07），
 * 第 8~14 个 → 月中场（CC=11..17），溢出 → 显式无效 CC=99（不丢数据，解析端回退）。
 * 只重写 eventName / eventRound，其余字段（成员、星数、时间戳）一律不动。
 *
 * 安全约定：execute() 先写全量备份到备份目录，备份失败立即中止，不触碰数据。
 */
class DataMigrator(
    private val dao: WarDao,
    private val repo: WarRepository
) {

    /**
     * 判断联赛名称是否属于「合法 SAABBCC 但 CC 不符合新 C1C2 合法集」的旧编码。
     * 非标准名（如示例数据的「示例·15人联赛（第3轮）」）返回 false，不参与迁移。
     */
    fun needsMigration(name: String): Boolean {
        if (name.length < 7 || name[0] != '1') return false
        if (!name.substring(1, 7).all { it.isDigit() }) return false
        val month = name.substring(3, 5).toIntOrNull() ?: return false
        if (month !in 1..12) return false
        val cc = name.substring(5, 7).toIntOrNull() ?: return false
        return !(cc in 1..7 || cc in 11..17)
    }

    /**
     * 纯函数：把同一（年,月）组内的事件按 createdAt 升序重编码。
     * 前 7 个 → 月初场（CC=01..07，round=序号）；第 8~14 个 → 月中场（CC=11..17）；
     * 溢出（>14）→ CC=99、round=0。名称前缀（S+AA+BB）原样保留。
     */
    fun remapMonth(events: List<WarEventEntity>): List<MigrationItem> {
        return events.sortedBy { it.createdAt }.mapIndexed { index, ev ->
            val cc = when {
                index < 7 -> index + 1            // 01..07 月初场第 1~7 轮
                index < 14 -> 10 + (index - 6)    // 11..17 月中场第 1~7 轮（index=7→11，index=13→17）
                else -> 99                        // 溢出：显式无效
            }
            val newName = ev.eventName.substring(0, 5) + "%02d".format(cc)
            MigrationItem(
                eventId = ev.eventId,
                oldName = ev.eventName,
                newName = newName,
                newRound = if (cc <= 17) cc % 10 else 0
            )
        }
    }

    /** 扫描全库，生成迁移计划（不写任何数据）。 */
    suspend fun scan(): MigrationPlan {
        val leagues = dao.getAllEvents().filter { it.eventType == "league" }
        val targets = leagues.filter { needsMigration(it.eventName) }
        // 按 AA+BB（年月）分组
        val items = targets.groupBy { it.eventName.substring(1, 5) }
            .flatMap { (_, list) -> remapMonth(list) }
        return MigrationPlan(items = items, overflowCount = items.count { it.newRound == 0 })
    }

    /**
     * 执行迁移：① 先导出全量备份写入 [backupDir]（失败即抛异常中止，不触碰数据）；
     * ② 按计划逐条重写 eventName/eventRound；③ 返回结果统计。
     */
    suspend fun execute(backupDir: File): MigrationResult {
        // 第一步：备份（防丢是核心诉求，写失败必须中止）
        val backupFile = File(
            backupDir,
            "migration_backup_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".json"
        )
        backupDir.mkdirs()
        val json = repo.exportAllDataJson()
        backupFile.writeText(json, Charsets.UTF_8)

        // 第二步：执行迁移（以最新扫描为准，备份与更新间数据被外部改动时按当前值覆盖）
        val plan = scan()
        var migrated = 0
        var skipped = 0
        for (item in plan.items) {
            val ev = dao.getEventById(item.eventId)
            if (ev == null) { skipped++; continue }
            dao.updateEvent(ev.copy(eventName = item.newName, eventRound = item.newRound))
            migrated++
        }
        return MigrationResult(
            backupPath = backupFile.absolutePath,
            migrated = migrated,
            skipped = skipped,
            overflow = plan.overflowCount
        )
    }
}
