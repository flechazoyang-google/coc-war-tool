package com.cocwar.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MemberManageViewModel(private val repo: WarRepository) : ViewModel() {

    private val _roster = MutableStateFlow<List<MemberRosterEntity>>(emptyList())
    val roster: StateFlow<List<MemberRosterEntity>> = _roster

    // 下拉刷新进度：名单由 Room Flow 自动保持最新，下拉仅提供手动重读与反馈
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init {
        viewModelScope.launch {
            repo.observeRoster().collect { list -> _roster.value = list }
        }
    }

    /** 下拉刷新：强制从数据库重读一次花名册，并提供进度反馈。 */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            // 同步查询瞬间完成，若立即置 false，UI 在同一帧内收到 true→false，
            // Compose 重组只能看到最终值，刷新状态边沿丢失，指示器无法正常收起。
            // 延迟一小段时间让「正在刷新…」真实可见，边沿检测与完成反馈才能生效。
            runCatching { repo.getRosterWithRoles() }
                .onSuccess { _roster.value = it }
            delay(600)
            _refreshing.value = false
        }
    }

    fun addNames(names: List<String>) {
        viewModelScope.launch { repo.addToRoster(names) }
    }

    fun removeName(name: String) {
        viewModelScope.launch { repo.removeFromRoster(name) }
    }

    /** 设置名单成员职位（leader/coLeader/elder/member）。 */
    fun updateRole(name: String, role: String) {
        viewModelScope.launch { repo.updateRosterRole(name, role) }
    }
}
