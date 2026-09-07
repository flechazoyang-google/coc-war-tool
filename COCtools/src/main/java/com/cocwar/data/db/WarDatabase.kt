package com.cocwar.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cocwar.data.model.Attack
import kotlinx.coroutines.flow.Flow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "war_events")
data class WarEventEntity(
    @PrimaryKey val eventId: String,
    val eventName: String,
    val eventType: String,        // "war" | "league"
    val eventRound: Int,          // 0 for war, 1..7 for league
    val clanTotalStars: Int,
    val clanTotalDestruction: String,
    val isSample: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "members",
    foreignKeys = [ForeignKey(
        entity = WarEventEntity::class,
        parentColumns = ["eventId"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("eventId")]
)
data class MemberEntity(
    @PrimaryKey val id: String,   // "$eventId#$rank"
    val eventId: String,
    val rank: Int,
    val playerName: String,
    val role: String,
    val totalStars: Int,
    val attacks: List<Attack>
)

@Entity(tableName = "member_roster")
data class MemberRosterEntity(
    @PrimaryKey val name: String,
    val role: String = "member",
    /**
     * 是否在册。离队改为「直接删除」后本字段恒为 true，仅保留列以兼容旧库；
     * 旧版本遗留的 active = 0 行在启动清理时被删除，业务代码不再读写该字段。
     */
    val active: Boolean = true
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun attacksToJson(list: List<Attack>): String = gson.toJson(list)

    @TypeConverter
    fun jsonToAttacks(json: String?): List<Attack> {
        // DB 列可能为 NULL 或字面量 "null"，统一安全兜底，避免 NPE 或返回 null 击穿非空类型
        if (json == null || json.isBlank() || json == "null") return emptyList()
        return runCatching {
            gson.fromJson<List<Attack>>(json, object : TypeToken<List<Attack>>() {}.type)
                ?: emptyList()
        }.getOrDefault(emptyList())
    }
}

/** 名字 → 行数投影（数据体检用）。 */
data class NameCount(
    val name: String,
    val cnt: Int
)

@Dao
interface WarDao {

    @Transaction
    @Query("SELECT * FROM war_events ORDER BY createdAt DESC")
    suspend fun getAllEvents(): List<WarEventEntity>

    @Query("SELECT * FROM war_events ORDER BY createdAt DESC")
    fun observeEvents(): Flow<List<WarEventEntity>>

    @Query("SELECT * FROM war_events WHERE eventId = :id")
    fun observeEvent(id: String): Flow<WarEventEntity?>

    @Query("SELECT * FROM war_events WHERE eventId = :id LIMIT 1")
    suspend fun getEventById(id: String): WarEventEntity?

    @Query("SELECT * FROM members WHERE eventId = :eventId ORDER BY rank ASC")
    fun observeMembers(eventId: String): Flow<List<MemberEntity>>

    @Transaction
    suspend fun insertEvent(event: WarEventEntity, members: List<MemberEntity>) {
        insertEventOnly(event)
        insertMembers(members)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventOnly(event: WarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Update
    suspend fun updateEvent(event: WarEventEntity)

    @Update
    suspend fun updateMember(member: MemberEntity)

    /** 删除单个成员行（同名两行合并为一行后移除多余行）。 */
    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMemberRow(id: String)

    /** 某名字在全部战报中的成员行数（全局合并的影响面预估）。 */
    @Query("SELECT COUNT(*) FROM members WHERE playerName = :name")
    suspend fun countRowsByName(name: String): Int

    /** 各名字在非示例战报中的成员行数（数据体检用，示例数据不参与）。 */
    @Query("SELECT m.playerName AS name, COUNT(*) AS cnt FROM members m INNER JOIN war_events e ON m.eventId = e.eventId WHERE e.isSample = 0 GROUP BY m.playerName")
    suspend fun getMemberRowCounts(): List<NameCount>

    @Query("DELETE FROM war_events WHERE eventId = :id")
    suspend fun deleteEvent(id: String)

    /** 清空全部战报与成员（用于从云端备份完整还原）。 */
    @Transaction
    suspend fun clearAll() {
        deleteAllMembers()
        deleteAllEvents()
    }

    @Query("DELETE FROM members")
    suspend fun deleteAllMembers()

    @Query("DELETE FROM war_events")
    suspend fun deleteAllEvents()

    @Query("SELECT COUNT(*) FROM war_events")
    suspend fun countEvents(): Int

    @Query("SELECT eventName FROM war_events WHERE createdAt >= :monthStart AND createdAt < :nextMonthStart")
    suspend fun getEventNamesInMonth(monthStart: Long, nextMonthStart: Long): List<String>

    @Query("SELECT * FROM war_events WHERE createdAt >= :start AND createdAt < :end ORDER BY createdAt DESC")
    suspend fun getEventsInRange(start: Long, end: Long): List<WarEventEntity>

    @Query("SELECT * FROM members WHERE eventId IN (:eventIds) ORDER BY rank ASC")
    suspend fun getMembersByEventIds(eventIds: List<String>): List<MemberEntity> {
        // Room 对空列表生成 "IN ()" 非法 SQL 会崩溃，这里在 DAO 层兜底
        if (eventIds.isEmpty()) return emptyList()
        return getMembersByEventIdsInternal(eventIds)
    }

    @Query("SELECT * FROM members WHERE eventId IN (:eventIds) ORDER BY rank ASC")
    suspend fun getMembersByEventIdsInternal(eventIds: List<String>): List<MemberEntity>

    @Query("SELECT DISTINCT playerName FROM members ORDER BY playerName")
    suspend fun getAllPlayerNames(): List<String>

    /** 某成员参与过的部落战 eventId（用于计算连续缺席场次；联赛不参与）。 */
    @Query("SELECT m.eventId FROM members m INNER JOIN war_events e ON m.eventId = e.eventId WHERE m.playerName = :name AND e.eventType != 'league'")
    suspend fun getWarEventIdsByPlayerName(name: String): List<String>
}

@Dao
interface RosterDao {
    @Query("SELECT * FROM member_roster ORDER BY name")
    fun observeAll(): Flow<List<MemberRosterEntity>>

    @Query("SELECT * FROM member_roster ORDER BY name")
    suspend fun getAll(): List<MemberRosterEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(names: List<MemberRosterEntity>)

    @Query("DELETE FROM member_roster WHERE name = :name")
    suspend fun delete(name: String)

    @Query("UPDATE member_roster SET role = :role WHERE name = :name")
    suspend fun updateRole(name: String, role: String)

    /** 花名册改名：合并目标不在册时沿用原条目（职位/在册状态保留）。调用方需保证 to 不存在，避免主键冲突。 */
    @Query("UPDATE member_roster SET name = :to WHERE name = :from")
    suspend fun rename(from: String, to: String)

    @Query("DELETE FROM member_roster")
    suspend fun clearAll()

    /** upsert：主键冲突时整体更新（role/active 以传入为准）。硬替换核心写入，不能复用 insertAll（IGNORE 不更新旧行）。 */
    @Upsert
    suspend fun upsertAll(entries: List<MemberRosterEntity>)

    /** 删除不在给定名单中的成员。空名单会清空整表——NOT IN () 是非法 SQL 且语义危险，由调用方保证非空。 */
    @Query("DELETE FROM member_roster WHERE name NOT IN (:names)")
    suspend fun deleteNotIn(names: List<String>)

    /**
     * 清除历史遗留的「已离队」行（active = 0）。
     * 离队改为直接删除后不再产生新行，仅用于升级时清理旧版本标记过离队的成员。
     */
    @Query("DELETE FROM member_roster WHERE active = 0")
    suspend fun deleteInactive()

    /**
     * 硬替换花名册（事务）：新名单 upsert（职位以新名单为准），不在新名单的**直接删除**。
     * @Transaction 保证 observeAll 不发射「已 upsert 未删除」的中间态。
     */
    @Transaction
    suspend fun hardReplace(entries: List<MemberRosterEntity>) {
        upsertAll(entries)
        deleteNotIn(entries.map { it.name })
    }
}

// v1→v2: 移除 war_events 中的敌方部落字段。
// 重建 war_events 前先关闭外键约束，避免 DROP TABLE 触发器联级删除 members 表数据。
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS war_events_new (eventId TEXT PRIMARY KEY NOT NULL, eventName TEXT NOT NULL, eventType TEXT NOT NULL, eventRound INTEGER NOT NULL, clanTotalStars INTEGER NOT NULL, clanTotalDestruction TEXT NOT NULL, isSample INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO war_events_new SELECT eventId, eventName, eventType, eventRound, clanTotalStars, clanTotalDestruction, isSample, createdAt FROM war_events")
            db.execSQL("DROP TABLE war_events")
            db.execSQL("ALTER TABLE war_events_new RENAME TO war_events")
        } finally {
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}

// v2→v3: members 表新增 totalStars 列（幂等：已存在则跳过），并回填历史数据
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val hasColumn = db.query("PRAGMA table_info(members)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "totalStars") { found = true; break }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE members ADD COLUMN totalStars INTEGER NOT NULL DEFAULT 0")
            // 回填历史数据：从 attacks JSON 中按摧毁率推导星数（>=100→3, >=50→2, >0→1）
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<com.cocwar.data.model.Attack>>() {}.type
            db.query("SELECT id, attacks FROM members").use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("id")
                val attIdx = cursor.getColumnIndexOrThrow("attacks")
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx)
                    val json = cursor.getString(attIdx)
                    if (json == null || json.isBlank() || json == "null") continue
                    val attacks = runCatching {
                        gson.fromJson<List<com.cocwar.data.model.Attack>>(json, type) ?: emptyList()
                    }.getOrDefault(emptyList())
                    val stars = attacks.filter { it.destructionPercentage > 0 }.fold(0) { acc, a ->
                        acc + when {
                            a.destructionPercentage >= 100 -> 3
                            a.destructionPercentage >= 50 -> 2
                            else -> 1  // >0 已经由 filter 保证
                        }
                    }
                    if (stars > 0) {
                        db.execSQL("UPDATE members SET totalStars = $stars WHERE id = ?", arrayOf(id))
                    }
                }
            }
        }
    }
}

// v3→v5: 清理旧别名表 + 创建 member_roster（跳过 v4 直接到 v5）
val MIGRATION_3_5 = object : Migration(3, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS member_aliases")
        db.execSQL("CREATE TABLE IF NOT EXISTS member_roster (name TEXT PRIMARY KEY NOT NULL)")
    }
}

// v4→v5: 清理旧别名表残留
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS member_aliases")
        db.execSQL("CREATE TABLE IF NOT EXISTS member_roster (name TEXT PRIMARY KEY NOT NULL)")
    }
}

// v5→v6: member_roster 新增 role 列（职位由花名册维护，默认成员；幂等：已存在则跳过）
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val hasColumn = db.query("PRAGMA table_info(member_roster)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "role") { found = true; break }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE member_roster ADD COLUMN role TEXT NOT NULL DEFAULT 'member'")
        }
    }
}

// v6→v7: member_roster 新增 active 列（false = 已标记离队；幂等：已存在则跳过）
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val hasColumn = db.query("PRAGMA table_info(member_roster)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "active") { found = true; break }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE member_roster ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
        }
    }
}

// v7→v8: 新增 pending_imports（后台批量识图的待确认草稿，不入备份）
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_imports (" +
            "id TEXT PRIMARY KEY NOT NULL, status TEXT NOT NULL, csvText TEXT NOT NULL, " +
            "errorMessage TEXT NOT NULL, imagePaths TEXT NOT NULL, totalImages INTEGER NOT NULL, " +
            "processedImages INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
    }
}

// v8→v9: 移除 pending_imports（AI 识图功能下线，App 只提供提示词、由外部软件识别）
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS pending_imports")
    }
}

private const val DB_VERSION = 9

@Database(
    entities = [WarEventEntity::class, MemberEntity::class, MemberRosterEntity::class],
    version = DB_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WarDatabase : RoomDatabase() {
    abstract fun warDao(): WarDao
    abstract fun rosterDao(): RosterDao

    companion object {
        const val NAME = "coc_war.db"

        fun build(context: android.content.Context): WarDatabase =
            Room.databaseBuilder(context.applicationContext, WarDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_5, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                .build()
    }
}
