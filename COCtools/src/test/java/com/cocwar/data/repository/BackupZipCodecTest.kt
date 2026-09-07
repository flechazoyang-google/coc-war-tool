package com.cocwar.data.repository

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.db.NameCount
import com.cocwar.data.db.RosterDao
import com.cocwar.data.db.WarDao
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BackupZipCodec 备份/还原单元测试（ZIP 多 CSV，替代已删的 BackupCodecTest）。
 * 用内存 Fake DAO 验证 round-trip 无损、花名册替换语义、非法备份拒绝。
 */
class BackupZipCodecTest {

    private class FakeWarDao : WarDao {
        val events = mutableListOf<WarEventEntity>()
        val members = mutableListOf<MemberEntity>()

        override suspend fun getAllEvents(): List<WarEventEntity> = events.toList()
        override fun observeEvents(): Flow<List<WarEventEntity>> = emptyFlow()
        override fun observeEvent(id: String): Flow<WarEventEntity?> = emptyFlow()
        override suspend fun getEventById(id: String): WarEventEntity? =
            events.firstOrNull { it.eventId == id }
        override fun observeMembers(eventId: String): Flow<List<MemberEntity>> = emptyFlow()
        // insertEvent 是 @Transaction 默认方法，落到 insertEventOnly + insertMembers
        override suspend fun insertEventOnly(event: WarEventEntity) { events += event }
        override suspend fun insertMembers(members: List<MemberEntity>) { this.members += members }
        override suspend fun updateEvent(event: WarEventEntity) {}
        override suspend fun updateMember(member: MemberEntity) {}
        override suspend fun deleteMemberRow(id: String) {}
        override suspend fun countRowsByName(name: String): Int = 0
        override suspend fun getMemberRowCounts(): List<NameCount> = emptyList()
        override suspend fun deleteEvent(id: String) { events.removeAll { it.eventId == id } }
        override suspend fun deleteAllMembers() { members.clear() }
        override suspend fun deleteAllEvents() { events.clear() }
        override suspend fun countEvents(): Int = events.size
        override suspend fun getEventNamesInMonth(monthStart: Long, nextMonthStart: Long): List<String> = emptyList()
        override suspend fun getEventsInRange(start: Long, end: Long): List<WarEventEntity> = emptyList()
        override suspend fun getMembersByEventIdsInternal(eventIds: List<String>): List<MemberEntity> =
            members.filter { it.eventId in eventIds }
        override suspend fun getAllPlayerNames(): List<String> = emptyList()
        override suspend fun getWarEventIdsByPlayerName(name: String): List<String> = emptyList()
    }

    private class FakeRosterDao : RosterDao {
        val roster = mutableListOf<MemberRosterEntity>()

        override fun observeAll(): Flow<List<MemberRosterEntity>> = emptyFlow()
        override suspend fun getAll(): List<MemberRosterEntity> = roster.toList()
        override suspend fun insertAll(names: List<MemberRosterEntity>) { roster += names }
        override suspend fun delete(name: String) {}
        override suspend fun updateRole(name: String, role: String) {}
        override suspend fun rename(from: String, to: String) {}
        override suspend fun clearAll() { roster.clear() }
        override suspend fun upsertAll(entries: List<MemberRosterEntity>) {}
        override suspend fun deleteNotIn(names: List<String>) {}
        override suspend fun deleteInactive() {}
        override suspend fun hardReplace(entries: List<MemberRosterEntity>) {}
    }

    private fun event(id: String, name: String, type: String = "war", stars: Int = 6, createdAt: Long = 1L) =
        WarEventEntity(
            eventId = id, eventName = name, eventType = type, eventRound = 0,
            clanTotalStars = stars, clanTotalDestruction = "50.0%", isSample = false, createdAt = createdAt
        )

    private fun member(eventId: String, name: String, rank: Int, stars: Int, attacks: List<Attack>) =
        MemberEntity(
            id = "$eventId#$rank", eventId = eventId, rank = rank, playerName = name,
            role = "member", totalStars = stars, attacks = attacks
        )

    @Test
    fun `round trip preserves events members and roster`() = runBlocking {
        val dao = FakeWarDao()
        val rosterDao = FakeRosterDao()
        val codec = BackupZipCodec(dao, rosterDao)

        dao.events += event("e1", "0260801", "war", 9, 100L)
        dao.events += event("e2", "1260801", "league", 3, 200L)
        dao.members += member("e1", "张三", 1, 6, listOf(Attack(1, 100), Attack(2, 80)))
        dao.members += member("e1", "李四", 2, 3, listOf(Attack(1, 50), Attack(2, 0)))
        dao.members += member("e2", "王五", 1, 3, listOf(Attack(1, 100)))
        rosterDao.roster += MemberRosterEntity("张三", "leader", true)
        rosterDao.roster += MemberRosterEntity("李四", "member", false)

        val bytes = codec.exportAllData()
        assertTrue(codec.validateBackup(bytes))

        val dao2 = FakeWarDao()
        val rosterDao2 = FakeRosterDao()
        BackupZipCodec(dao2, rosterDao2).restoreFromBackup(bytes)

        assertEquals(2, dao2.events.size)
        assertEquals(3, dao2.members.size)
        // 李四在备份中标记为已离队 → 还原时不再回到花名册（离队即删除）
        assertEquals(1, rosterDao2.roster.size)
        // 事件无损
        assertEquals("0260801", dao2.events.first { it.eventId == "e1" }.eventName)
        assertEquals("league", dao2.events.first { it.eventId == "e2" }.eventType)
        // 成员无损（含进攻摧毁率）
        val zhang = dao2.members.first { it.playerName == "张三" }
        assertEquals(listOf(100, 80), zhang.attacks.map { it.destructionPercentage })
        // 花名册还原：职位保留，已离队成员被丢弃
        val leader = rosterDao2.roster.first { it.name == "张三" }
        assertEquals("leader", leader.role)
        assertTrue(leader.active)
        assertFalse(rosterDao2.roster.any { it.name == "李四" })
    }

    @Test
    fun `restore keeps -1 sentinel in destruction round trip`() = runBlocking {
        val dao = FakeWarDao()
        dao.events += event("e1", "0260801")
        dao.members += member("e1", "张三", 1, -1, listOf(Attack(1, -1), Attack(2, 100)))

        val bytes = BackupZipCodec(dao, FakeRosterDao()).exportAllData()

        val dao2 = FakeWarDao()
        BackupZipCodec(dao2, FakeRosterDao()).restoreFromBackup(bytes)
        val zhang = dao2.members.first { it.playerName == "张三" }
        assertEquals(-1, zhang.totalStars)
        assertEquals(listOf(-1, 100), zhang.attacks.map { it.destructionPercentage })
    }

    @Test
    fun `validateBackup rejects garbage and empty backup`() {
        val codec = BackupZipCodec(FakeWarDao(), FakeRosterDao())
        assertFalse(codec.validateBackup("garbage".toByteArray()))
        assertFalse(codec.validateBackup(ByteArray(0)))
    }
}
