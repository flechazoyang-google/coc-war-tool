package com.cocwar.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.repository.WarRepository
import com.cocwar.domain.RosterEntry
import com.cocwar.domain.RosterMaintenance
import com.cocwar.domain.SuspectMember
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

    // 部落战场次总数（疑似离队判定：count == totalWarCount 表示从未参战，不误报新成员）
    private val _totalWarCount = MutableStateFlow(0)

    // 疑似离队成员（在册 + 连续缺席 ≥ 阈值 + 此前参战过），由 UI 逐人确认后标记离队
    private val _suspects = MutableStateFlow<List<SuspectMember>>(emptyList())
    val suspects: StateFlow<List<SuspectMember>> = _suspects

    // 疑似离队阈值 N（默认 3，范围 1..10，持久化在 repo prefs）
    private val _suspectThreshold = MutableStateFlow(repo.suspectThreshold())
    val suspectThreshold: StateFlow<Int> = _suspectThreshold

    // 已离队成员（active=false，职位保留，可一键恢复）
    private val _departed = MutableStateFlow<List<MemberRosterEntity>>(emptyList())
    val departed: StateFlow<List<MemberRosterEntity>> = _departed

    // 下拉刷新进度：名单由 Room Flow 自动保持最新，下拉仅提供手动重读与反馈
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init {
        // 花名册或战报任一变化（导入/删除/改职位/标记离队/云端还原）都会重算
        // 连续缺席场次与已离队列表，保证排序与维护入口随数据实时更新
        viewModelScope.launch {
            combine(repo.observeRoster(), repo.events) { roster, _ -> roster }
                .collect { roster ->
                    _roster.value = roster
                    val info = repo.getWarAbsentInfo(roster.map { it.name })
                    _absentCounts.value = info.counts
                    _totalWarCount.value = info.totalWarCount
                    _departed.value = roster.filter { !it.active }
                }
        }
        // 疑似离队 = 在册 + 连续缺席 ≥ N 场 + 此前参战过；调阈值即时重算
        viewModelScope.launch {
            combine(_roster, _absentCounts, _totalWarCount, _suspectThreshold) {
                    roster, absent, total, n ->
                RosterMaintenance.filterSuspectedDeparted(roster, absent, total, n)
            }.collect { _suspects.value = it }
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
                    val info = repo.getWarAbsentInfo(it.map { m -> m.name })
                    _absentCounts.value = info.counts
                    _totalWarCount.value = info.totalWarCount
                    _departed.value = it.filter { m -> !m.active }
                }
            delay(600)
            _refreshing.value = false
        }
    }

    fun addNames(names: List<String>) {
        viewModelScope.launch { repo.addToRoster(names) }
    }

    /** 更新花名册（软替换）：新名单 upsert，在册但不在新名单的标记离队；列表经 observeRoster 自动刷新。 */
    fun replaceRoster(entries: List<RosterEntry>) {
        viewModelScope.launch { repo.replaceRoster(entries) }
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

    // === 离队管理 ===

    /** 调整疑似离队阈值（1..10，持久化），生效于下一次疑似名单重算。 */
    fun setSuspectThreshold(n: Int) {
        // 钳制与持久化以 repo 为唯一权威，这里回读钳制后的值，避免两端口径漂移
        repo.setSuspectThreshold(n)
        _suspectThreshold.value = repo.suspectThreshold()
    }

    /** 标记某成员为已离队（active=false，职位保留）。 */
    fun markDeparted(name: String) {
        viewModelScope.launch { repo.setRosterActive(listOf(name), false) }
    }

    /** 恢复某已离队成员（active=true，回到在册名单）。 */
    fun restoreDeparted(name: String) {
        viewModelScope.launch { repo.setRosterActive(listOf(name), true) }
    }

    /** 一键恢复全部已离队成员。 */
    fun restoreAllDeparted() {
        val names = _departed.value.map { it.name }
        if (names.isNotEmpty()) viewModelScope.launch { repo.setRosterActive(names, true) }
    }
}
