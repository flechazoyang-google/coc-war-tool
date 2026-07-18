package com.cocwar.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.repository.WarRepository
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.domain.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel(private val repo: WarRepository) : ViewModel() {

    val monthlyStats: StateFlow<List<MemberMonthlyStat>> = MutableStateFlow(emptyList())
    val recentMissed: StateFlow<List<RecentMissedRank>> = MutableStateFlow(emptyList())
    val loading: StateFlow<Boolean> = MutableStateFlow(false)

    private var cachedEvents: List<WarEventEntity> = emptyList()
    private var cachedMembers: List<MemberEntity> = emptyList()

    init {
        loadMonthly()
    }

    fun loadMonthly() {
        viewModelScope.launch {
            (loading as MutableStateFlow).value = true
            val (start, end) = repo.currentMonthRange()
            val events = repo.getEventsInRange(start, end)
            val eventIds = events.map { it.eventId }
            val members = if (eventIds.isNotEmpty()) repo.getMembersByEventIds(eventIds) else emptyList()

            cachedEvents = events
            cachedMembers = members

            (monthlyStats as MutableStateFlow).value =
                StatsCalculator.computeMonthly(events, members)
            (loading as MutableStateFlow).value = false
        }
    }

    fun loadRecentMissed(n: Int) {
        viewModelScope.launch {
            // 需要获取所有事件（按时间倒序）
            val (start, end) = repo.currentMonthRange()
            val events = if (n <= 0) {
                // 当月全部
                if (cachedEvents.isEmpty()) repo.getEventsInRange(start, end) else cachedEvents
            } else {
                // 取最近的 n 个（需要查全部再截取）
                repo.getEventsInRange(0, Long.MAX_VALUE).sortedByDescending { it.createdAt }
            }
            val selected = if (n <= 0) events else events.take(n)
            val eventIds = selected.map { it.eventId }
            val members = if (eventIds.isNotEmpty()) repo.getMembersByEventIds(eventIds) else emptyList()

            (recentMissed as MutableStateFlow).value =
                StatsCalculator.computeRecentMissed(selected, members, n)
        }
    }
}
