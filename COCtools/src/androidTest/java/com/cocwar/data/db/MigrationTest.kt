package com.cocwar.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB Migration 链式升级测试（androidTest，需在设备/模拟器运行）。
 *
 * 覆盖 v1 库 → 链式迁移到 v6：
 * - v1→v2：war_events 重建（保留 8 列，数据不丢）
 * - v2→v3：members 新增 totalStars 并回填（摧毁率 >=100→3、>=50→2、>0→1）
 * - v3→v5 / v4→v5：创建 member_roster
 * - v5→v6：member_roster 新增 role 列（默认 member）
 *
 * 项目 exportSchema=false 无 schema 文件，故手动构造 v1 库（依赖迁移的列），
 * 用 Room 打开触发真实链式迁移，迁移后经 DAO 与 SQL 断言结构与数据。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var context: Context
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** 手动创建 v1 结构（各 migration 依赖的列）并插入样例数据。 */
    private fun createV1Database() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        db.version = 1
        db.execSQL(
            "CREATE TABLE war_events (" +
                "eventId TEXT PRIMARY KEY NOT NULL, " +
                "eventName TEXT NOT NULL, " +
                "eventType TEXT NOT NULL, " +
                "eventRound INTEGER NOT NULL, " +
                "clanTotalStars INTEGER NOT NULL, " +
                "clanTotalDestruction TEXT NOT NULL, " +
                "isSample INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE members (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "eventId TEXT NOT NULL, " +
                "rank INTEGER NOT NULL, " +
                "playerName TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "attacks TEXT NOT NULL, " +
                "FOREIGN KEY(eventId) REFERENCES war_events(eventId) ON DELETE CASCADE)"
        )
        // 样例数据：1 场部落战 + 2 名成员（attacks 为 Gson JSON）
        db.execSQL("INSERT INTO war_events VALUES ('e1','0260801','war',1,4,'66.7%',0,1000)")
        db.execSQL(
            "INSERT INTO members VALUES ('e1#1','e1',1,'张三','member'," +
                "'[{\"attackOrder\":1,\"destructionPercentage\":100},{\"attackOrder\":2,\"destructionPercentage\":33}]')"
        )
        db.execSQL(
            "INSERT INTO members VALUES ('e1#2','e1',2,'李四','member'," +
                "'[{\"attackOrder\":1,\"destructionPercentage\":0},{\"attackOrder\":2,\"destructionPercentage\":0}]')"
        )
        db.close()
    }

    @Test
    fun migrateV1ToV6_keepsDataAndFinalSchema() = runBlocking {
        createV1Database()

        // Room 打开 v1 库 → 自动链式迁移到 v6（缺任一环会抛 IllegalStateException）
        val roomDb = Room.databaseBuilder(context, WarDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_5, MIGRATION_4_5, MIGRATION_5_6)
            .build()
        roomDb.openHelper.writableDatabase

        // 1. 数据保留：战报与成员仍在
        val events = roomDb.warDao().getAllEvents()
        assertEquals(1, events.size)
        assertEquals("0260801", events[0].eventName)
        val members = roomDb.warDao().getMembersByEventIds(listOf("e1"))
        assertEquals(2, members.size)

        // 2. v2→v3 回填 totalStars：张三 100%→3 + 33%→1 = 4；李四全空 = 0
        val zhang = members.first { it.playerName == "张三" }
        assertEquals(4, zhang.totalStars)
        val li = members.first { it.playerName == "李四" }
        assertEquals(0, li.totalStars)

        // 3. v5→v6 member_roster 有 role 列，默认 member
        roomDb.execSQL("INSERT INTO member_roster (name) VALUES ('王五')")
        val roster = roomDb.rosterDao().getAll()
        assertEquals(listOf("王五"), roster.map { it.name })
        assertEquals("member", roster.single().role)

        roomDb.close()
    }
}
