package com.cocwar.data.repository

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.db.RosterDao
import com.cocwar.data.db.WarDao
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.parser.WarJsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BackupCodec 备份格式兼容性测试：导出 → 校验 → 还原往返（内存 fake DAO，零新依赖）。
 * 保证手工拼接格式与解析格式自洽，旧备份可读、新导出可还原。
 */
class BackupCodecTest {

    // ─── 内存 fake DAO ───

    private class FakeWarDao : WarDao {
        val events = mutableMapOf<String, WarEventEntity>()
        val membersByEvent = mutableMapOf<String, MutableList<MemberEntity>>()

        override suspend fun getAllEvents(): List<WarEventEntity> =
            events.values.sortedByDescending { it.createdAt }

        override suspend fun getEventById(id: String): WarEventEntity? = events[id]

        override suspend fun getMembersByEventIdsInternal(eventIds: List<String>): List<MemberEntity> =
            eventIds.flatMap { membersByEvent[it] ?: emptyList() }

        override suspend fun insertEventOnly(event: WarEventEntity) {
            events[event.eventId] = event
        }

        override suspend fun insertMembers(members: List<MemberEntity>) {
            members.forEach { membersByEvent.getOrPut(it.eventId) { mutableListOf() }.add(it) }
        }

        override suspend fun updateEvent(event: WarEventEntity) { events[event.eventId] = event }

        override suspend fun updateMember(member: MemberEntity) {
            membersByEvent[member.eventId]?.replaceAll { if (it.id == member.id) member else it }
        }

        override suspend fun deleteEvent(id: String) { events.remove(id); membersByEvent.remove(id) }

        override suspend fun deleteAllMembers() { membersByEvent.clear() }
        override suspend fun deleteAllEvents() { events.clear() }

        override suspend fun countEvents(): Int = events.size

        override suspend fun getEventNamesInMonth(monthStart: Long, nextMonthStart: Long): List<String> =
            events.values.filter { it.createdAt in monthStart until nextMonthStart }.map { it.eventName }

        override suspend fun getEventsInRange(start: Long, end: Long): List<WarEventEntity> =
            events.values.filter { it.createdAt in start until end }

        override suspend fun getAllPlayerNames(): List<String> =
            membersByEvent.values.flatten().map { it.playerName }.distinct().sorted()

        override suspend fun getWarEventIdsByPlayerName(name: String): List<String> =
            membersByEvent.entries
                .filter { (eventId, list) -> list.any { it.playerName == name } && events[eventId]?.eventType != "league" }
                .map { it.key }

        override fun observeEvents(): Flow<List<WarEventEntity>> = flowOf(getAllEventsSafe())
        override fun observeEvent(id: String): Flow<WarEventEntity?> = flowOf(events[id])
        override fun observeMembers(eventId: String): Flow<List<MemberEntity>> =
            flowOf(membersByEvent[eventId] ?: emptyList())

        private fun getAllEventsSafe(): List<WarEventEntity> = events.values.sortedByDescending { it.createdAt }
    }

    private class FakeRosterDao : RosterDao {
        val entries = mutableListOf<MemberRosterEntity>()

        override suspend fun getAll(): List<MemberRosterEntity> = entries.toList()
        override suspend fun insertAll(names: List<MemberRosterEntity>) {
            names.forEach { n -> if (entries.none { it.name == n.name }) entries.add(n) }
        }
        override suspend fun delete(name: String) { entries.removeAll { it.name == name } }
        override suspend fun updateRole(name: String, role: String) {
            entries.replaceAll { if (it.name == name) it.copy(role = role) else it }
        }
        override suspend fun clearAll() { entries.clear() }
        override fun observeAll(): Flow<List<MemberRosterEntity>> = flowOf(entries.toList())
    }

    private fun event(id: String, name: String, stars: Int, createdAt: Long) = WarEventEntity(
        eventId = id, eventName = name, eventType = "war", eventRound = 0,
        clanTotalStars = stars, clanTotalDestruction = "100%",
        isSample = false, createdAt = createdAt
    )

    private fun member(eventId: String, rank: Int, name: String, stars: Int) = MemberEntity(
        id = "$eventId#$rank", eventId = eventId, rank = rank, playerName = name,
        role = "member", totalStars = stars,
        attacks = listOf(Attack(1, if (stars > 0) 100 else 0))
    )

    private class Fixture {
        val warDao = FakeWarDao()
        val rosterDao = FakeRosterDao()
        val restored = mutableListOf<WarJsonParser.ParsedEvent>()
        /** importEvent 委托：真正写入内存 DAO（模拟 repository.importEvent 落库），并记录解析结果 */
        val codec = BackupCodec(warDao, rosterDao) { parsed ->
            restored.add(parsed)
            warDao.insertEventOnly(parsed.event)
            warDao.insertMembers(parsed.members)
        }
    }

    // ─── 用例 ───

    @Test
    fun `导出包含名单-事件-成员-进攻字段`() = runBlocking {
        val f = Fixture()
        f.rosterDao.entries += MemberRosterEntity(name = "陈平安", role = "leader")
        f.warDao.events["e1"] = event("e1", "0030701", 3, 1_000L)
        f.warDao.membersByEvent["e1"] = mutableListOf(member("e1", 1, "陈平安", 3))

        val json = f.codec.exportAllDataJson()
        assertTrue(json.contains("\"roster\""))
        assertTrue(json.contains("\"name\": \"陈平安\""))
        assertTrue(json.contains("\"role\": \"leader\""))
        assertTrue(json.contains("\"event_name\": \"0030701\""))
        assertTrue(json.contains("\"player_name\": \"陈平安\""))
        assertTrue(json.contains("\"attack_order\": 1"))
        assertTrue(json.contains("\"destruction_percentage\": 100"))
        // 自身导出的备份应通过校验
        assertTrue(f.codec.validateBackupJson(json))
    }

    @Test
    fun `导出对特殊字符转义-引号反斜杠`() = runBlocking {
        val f = Fixture()
        f.rosterDao.entries += MemberRosterEntity(name = "a\"b\\c", role = "member")
        f.warDao.events["e1"] = event("e1", "0030701", 3, 1_000L)
        val json = f.codec.exportAllDataJson()
        assertTrue(json.contains("a\\\"b"))
        assertTrue(json.contains("b\\\\c"))
        // 转义后仍应能解析（events 非空才可通过校验）
        assertTrue(f.codec.validateBackupJson(json))
    }

    @Test
    fun `校验拒绝空 events 与非法 JSON`() {
        val f = Fixture()
        assertFalse(f.codec.validateBackupJson("{}"))
        assertFalse(f.codec.validateBackupJson("{\"roster\":[]}"))
        assertFalse(f.codec.validateBackupJson("not json"))
        assertFalse(f.codec.validateBackupJson(""))
        assertFalse(f.codec.validateBackupJson("   "))
    }

    @Test
    fun `往返-导出后完整还原事件成员与名单`() = runBlocking {
        val f = Fixture()
        f.rosterDao.entries += MemberRosterEntity(name = "甲", role = "elder")
        f.warDao.events["e1"] = event("e1", "0030701", 6, 1_000L)
        f.warDao.membersByEvent["e1"] = mutableListOf(
            member("e1", 1, "甲", 3),
            member("e1", 2, "乙", 3)
        )

        val json = f.codec.exportAllDataJson()

        // 还原到"另一个"空库（模拟换机）
        val target = Fixture()
        target.codec.restoreFromBackupJson(json)

        // 名单来自备份 roster（仅甲）；事件成员（甲/乙）随事件完整还原
        // 注意：备份不含 eventId，还原后由解析器重新生成，用实际 id 查成员
        assertEquals(listOf("甲"), target.rosterDao.entries.map { it.name })
        assertEquals(1, target.warDao.events.size)
        val restoredEvent = target.warDao.events.values.first()
        assertEquals("0030701", restoredEvent.eventName)
        assertEquals(6, restoredEvent.clanTotalStars)
        assertEquals(
            setOf("甲", "乙"),
            target.warDao.membersByEvent[restoredEvent.eventId]!!.map { it.playerName }.toSet()
        )
    }

    @Test
    fun `还原-损坏事件中止且不清空本地数据`() = runBlocking {
        val f = Fixture()
        f.warDao.events["local"] = event("local", "0030701", 3, 1_000L)
        val broken = """
            {
              "events": [
                {"event_name": "0030701", "members": [{"player_name": "甲", "attacks": "broken"}]}
              ]
            }
        """.trimIndent()

        try {
            f.codec.restoreFromBackupJson(broken)
            fail("损坏备份应抛出异常")
        } catch (e: IllegalStateException) {
            // 期望中止
        }
        // 本地数据未被清空
        assertEquals(1, f.warDao.events.size)
        assertEquals("local", f.warDao.events.keys.first())
    }

    @Test
    fun `还原-旧版字符串数组名单兼容`() = runBlocking {
        val f = Fixture()
        val legacy = """
            {
              "roster": ["张三", "李四"],
              "events": [
                {"event_name": "0030701", "clan_total_stars": 0,
                 "members": [{"player_name": "张三", "total_stars": 0, "attacks": []}]}
              ]
            }
        """.trimIndent()
        f.codec.restoreFromBackupJson(legacy)
        // 旧版字符串名单 → 默认职位 member
        assertEquals(setOf("张三", "李四"), f.rosterDao.entries.map { it.name }.toSet())
        assertTrue(f.rosterDao.entries.all { it.role == "member" })
    }
}
