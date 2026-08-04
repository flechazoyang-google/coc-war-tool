package com.cocwar.ui.eventlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
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

    // 下拉刷新进度：列表本身由 Room Flow 自动保持最新，下拉仅提供手动重读与反馈
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** 下拉刷新：强制从数据库重读一次（Flow 后续发射会自动更新列表），并提供进度反馈。 */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { repo.getAllEventsSync() }
            _refreshing.value = false
        }
    }

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

    /** 解析剪切板战报 JSON：注入花名册职位映射，使预览阶段名字颜色即按花名册职位展示。 */
    suspend fun parseWarJson(text: String): WarJsonParser.ParseResult {
        val roleMap = repo.rosterRoleMap()
        return WarJsonParser.parse(text, rosterRoles = roleMap)
    }
}
