package com.cocwar.data.migrate

import com.cocwar.data.db.WarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataMigrator 纯函数测试：needsMigration / remapMonth。
 * 语义见 docs/RULES.md 联赛命名（S=1, AA+BB=年月, CC=轮次；合法新编码 01..07 / 11..17）。
 */
class DataMigratorTest {

    private fun event(id: String, name: String, createdAt: Long = id.hashCode().toLong()) =
        WarEventEntity(
            eventId = id,
            eventName = name,
            eventType = "league",
            eventRound = 0,
            clanTotalStars = 0,
            clanTotalDestruction = "0",
            isSample = false,
            createdAt = createdAt
        )

    // ─── needsMigration：合法新编码不迁移 ───

    @Test
    fun `needsMigration 合法新编码-月初场 01到07 不迁移`() {
        (1..7).forEach { cc ->
            assertFalse("CC=$cc 应视为合法月初场", DataMigrator.needsMigration("10307%02d".format(cc)))
        }
    }

    @Test
    fun `needsMigration 合法新编码-月中场 11到17 不迁移`() {
        (11..17).forEach { cc ->
            assertFalse("CC=$cc 应视为合法月中场", DataMigrator.needsMigration("10307$cc"))
        }
    }

    @Test
    fun `needsMigration 旧段偏移编码 08到10 需迁移`() {
        (8..10).forEach { cc ->
            assertTrue("CC=$cc 应为旧段偏移编码", DataMigrator.needsMigration("10307%02d".format(cc)))
        }
    }

    @Test
    fun `needsMigration 溢出段编码 18到99 需迁移`() {
        listOf("18", "19", "77", "99").forEach { cc ->
            assertTrue("CC=$cc 应视为需迁移的非法段", DataMigrator.needsMigration("10307$cc"))
        }
    }

    @Test
    fun `needsMigration 非标准名不参与迁移`() {
        // 长度不足
        assertFalse(DataMigrator.needsMigration("103070"))
        // 首位不是 S=1（部落战/未知）
        assertFalse(DataMigrator.needsMigration("2030707"))
        // 含非数字字符
        assertFalse(DataMigrator.needsMigration("10307A7"))
        // 月份非法（00 / 13）
        assertFalse(DataMigrator.needsMigration("1030099"))
        assertFalse(DataMigrator.needsMigration("1031399"))
        // 中文示例名
        assertFalse(DataMigrator.needsMigration("示例·15人联赛（第3轮）"))
    }

    // ─── remapMonth：按 createdAt 升序重编码 ───

    @Test
    fun `remapMonth 乱序输入按 createdAt 升序重编码为 01到07`() {
        // 故意乱序传入：第 0 个 createdAt 最大
        val events = (1..7).map { event("id$it", "1030701", createdAt = 1000L + (7 - it)) }
        val items = DataMigrator.remapMonth(events)
        assertEquals(7, items.size)
        // 按 createdAt 升序后：createdAt=1001 的事件(原 id6)应排第 0 位 → CC=01
        items.forEachIndexed { index, item ->
            assertEquals("轮次索引 $index 应重编码为 %02d".format(index + 1), "10307%02d".format(index + 1), item.newName)
            assertEquals(index + 1, item.newRound)
        }
    }

    @Test
    fun `remapMonth 第8到第14个重编码为 11到17`() {
        val events = (1..14).map { event("id$it", "1030799", createdAt = it.toLong()) }
        val items = DataMigrator.remapMonth(events)
        assertEquals(14, items.size)
        // 前 7 个 → 01..07；后 7 个 → 11..17
        assertEquals("1030701", items[0].newName)
        assertEquals("1030707", items[6].newName)
        assertEquals("1030711", items[7].newName)
        assertEquals(1, items[7].newRound)
        assertEquals("1030717", items[13].newName)
        assertEquals(7, items[13].newRound)
    }

    @Test
    fun `remapMonth 超过14个溢出为 CC=99 round=0 不丢数据`() {
        val events = (1..15).map { event("id$it", "1030799", createdAt = it.toLong()) }
        val items = DataMigrator.remapMonth(events)
        assertEquals(15, items.size)
        val overflow = items[14]
        assertEquals("1030799", overflow.newName)
        assertEquals(0, overflow.newRound)
        // 溢出统计口径：newRound == 0 即溢出
        assertEquals(1, items.count { it.newRound == 0 })
    }

    @Test
    fun `remapMonth 保留名称前缀并映射原事件`() {
        val events = listOf(
            event("e1", "1030701", createdAt = 2L),
            event("e2", "1030702", createdAt = 1L),
        )
        val items = DataMigrator.remapMonth(events)
        // createdAt 升序：e2(1L) 在前 → CC=01；e1(2L) 在后 → CC=02
        assertEquals("e2", items[0].eventId)
        assertEquals("1030701", items[0].newName)
        assertEquals("1030702", items[0].oldName)
        assertEquals("e1", items[1].eventId)
        assertEquals("1030702", items[1].newName)
    }
}
