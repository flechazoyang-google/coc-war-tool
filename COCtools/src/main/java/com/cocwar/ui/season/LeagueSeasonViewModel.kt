package com.cocwar.ui.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.repository.WarRepository
import com.cocwar.domain.LeagueSeasonCalculator
import com.cocwar.domain.LeagueSeasonStats
import com.cocwar.ui.util.parseLeagueMatchFromName
import com.cocwar.ui.util.parseMonthFromName
import com.cocwar.ui.util.parseYearFromName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 联赛赛季视图：按（年, 月, 场次归属）聚合某一场联赛的全部轮次，
 * 计算 7 轮总览与成员出战轮换统计。
 */
class LeagueSeasonViewModel(private val repo: WarRepository) : ViewModel() {

    private val _stats = MutableStateFlow<LeagueSeasonStats?>(null)
    val stats: StateFlow<LeagueSeasonStats?> = _stats

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 加载某场联赛（year=年份后两位, month=1..12, match=1 月初/2 月中）的全部轮次并聚合。 */
    fun load(year: Int, month: Int, match: Int) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val all = repo.getAllEventsSync()
                val selected = all.filter { ev ->
                    ev.eventType == "league" &&
                        parseYearFromName(ev.eventName) == year &&
                        parseMonthFromName(ev.eventName) == month &&
                        parseLeagueMatchFromName(ev.eventName) == match
                }
                val members = if (selected.isNotEmpty()) {
                    repo.getMembersByEventIds(selected.map { it.eventId })
                } else emptyList()
                _stats.value = LeagueSeasonCalculator.compute(year, month, match, selected, members)
            } finally {
                _loading.value = false
            }
        }
    }
}
