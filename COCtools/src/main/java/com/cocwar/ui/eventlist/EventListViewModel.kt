package com.cocwar.ui.eventlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 已删除战报的快照，用于撤销重插 */
data class DeletedWar(
    val event: WarEventEntity,
    val members: List<MemberEntity>
)

class EventListViewModel(private val repo: WarRepository) : ViewModel() {

    val events: StateFlow<List<WarEventEntity>> = repo.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 删除战报前先快照事件与成员，落库删除后返回快照；
     * 事件不存在返回 null（UI 提示删除失败）。
     */
    suspend fun deleteEventWithSnapshot(id: String): DeletedWar? {
        val event = repo.getEventById(id) ?: return null
        val members = repo.getMembersByEventIds(listOf(id))
        repo.deleteEvent(id)
        return DeletedWar(event, members)
    }

    /** 撤销删除：按原样重插事件与成员（含进攻记录）。 */
    suspend fun undoDelete(snapshot: DeletedWar) {
        repo.importEvent(WarJsonParser.ParsedEvent(snapshot.event, snapshot.members))
    }
}
