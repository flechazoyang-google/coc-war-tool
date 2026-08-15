package com.cocwar.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MemberManageViewModel(private val repo: WarRepository) : ViewModel() {

    private val _roster = MutableStateFlow<List<MemberRosterEntity>>(emptyList())
    val roster: StateFlow<List<MemberRosterEntity>> = _roster

    // 各成员「距离上次参战已连续缺席的部落战场次」（name → count），供花名册排序使用
    private val _absentCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val absentCounts: StateFlow<Map<String, Int>> = _absentCounts

    // 下拉刷新进度：名单由 Room Flow 自动保持最新，下拉仅提供手动重读与反馈
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init {
        // 花名册或战报任一变化（导入/删除/改职位/云端还原）都会重算连续缺席场次，
        // 保证同职位排序随数据实时更新
        viewModelScope.launch {
            combine(repo.observeRoster(), repo.events) { roster, _ -> roster }
                .collect { roster ->
                    _roster.value = roster
                    _absentCounts.value = repo.getWarAbsentCounts(roster.map { it.name })
                }
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
                .onSuccess {
                    _roster.value = it
                    _absentCounts.value = repo.getWarAbsentCounts(it.map { m -> m.name })
                }
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

    /** 查询某成员距离上次参战（出现在部落战名单）已连续缺席的部落战场次。 */
    suspend fun getWarAbsentCount(name: String): Int = repo.getWarAbsentCount(name)
}
