package com.cocwar.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.repository.WarRepository
import com.cocwar.domain.EventStatSummary
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.domain.StatsCalculator
import com.cocwar.domain.StatsOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** 成员排序方式 */
enum class MemberSortBy(val label: String) {
    EFFECTIVE_RATE("有效参战率"),
    TOTAL_STARS("总星数"),
    PARTICIPATED("参战次数"),
    ATTACKED("进攻次数")
}

/** 类型筛选 */
enum class TypeFilter(val label: String) {
    ALL("全部"),
    WAR("部落战"),
    LEAGUE("联赛")
}

/** 可选月份 */
data class MonthOption(
    val year: Int,
    val month: Int,        // 1..12
    val label: String,     // "2026年7月"
    val startMs: Long,
    val endMs: Long
)

class StatsViewModel(private val repo: WarRepository) : ViewModel() {

    // --- 月份选择 ---
    val availableMonths: StateFlow<List<MonthOption>> = MutableStateFlow(emptyList())
    val selectedMonth: StateFlow<MonthOption?> = MutableStateFlow(null)

    // --- 数据 ---
    val overview: StateFlow<StatsOverview?> = MutableStateFlow(null)
    val memberStats: StateFlow<List<MemberMonthlyStat>> = MutableStateFlow(emptyList())
    val eventSummaries: StateFlow<List<EventStatSummary>> = MutableStateFlow(emptyList())
    val recentMissed: StateFlow<List<RecentMissedRank>> = MutableStateFlow(emptyList())
    val loading: StateFlow<Boolean> = MutableStateFlow(false)

    // --- 排序 ---
    val sortBy: StateFlow<MemberSortBy> = MutableStateFlow(MemberSortBy.EFFECTIVE_RATE)

    // --- 类型筛选 ---
    val typeFilter: StateFlow<TypeFilter> = MutableStateFlow(TypeFilter.ALL)

    // 缓存当月原始数据
    private var currentEvents: List<WarEventEntity> = emptyList()
    private var currentMembers: List<MemberEntity> = emptyList()

    // 缓存所有事件用于近N次计算
    private var allEventsDesc: List<WarEventEntity> = emptyList()

    init {
        loadAvailableMonths()
    }

    /** 扫描所有战报，构建可选月份列表，默认选中当前月份。 */
    fun loadAvailableMonths() {
        viewModelScope.launch {
            val allEvents = repo.getAllEventsSync()
            allEventsDesc = allEvents.sortedByDescending { it.createdAt }

            val months = allEvents
                .map { event ->
                    val cal = Calendar.getInstance().apply { timeInMillis = event.createdAt }
                    cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH) + 1
                }
                .distinct()
                .sortedByDescending { (y, m) -> y * 100 + m }
                .map { (y, m) ->
                    val cal = Calendar.getInstance().apply {
                        set(y, m - 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    MonthOption(
                        year = y,
                        month = m,
                        label = "${y}年${m}月",
                        startMs = start,
                        endMs = cal.timeInMillis
                    )
                }

            (availableMonths as MutableStateFlow).value = months

            // 默认选中当前月份，若当前月份无数据则选最新月份
            val now = Calendar.getInstance()
            val currentYear = now.get(Calendar.YEAR)
            val currentMonth = now.get(Calendar.MONTH) + 1
            val default = months.find { it.year == currentYear && it.month == currentMonth }
                ?: months.firstOrNull()

            if (default != null) {
                (selectedMonth as MutableStateFlow).value = default
                loadMonth(default)
            }
        }
    }

    fun selectMonth(option: MonthOption) {
        (selectedMonth as MutableStateFlow).value = option
        loadMonth(option)
    }

    fun setSortBy(sort: MemberSortBy) {
        (sortBy as MutableStateFlow).value = sort
        applySort()
    }

    fun setTypeFilter(filter: TypeFilter) {
        (typeFilter as MutableStateFlow).value = filter
        recomputeForFilter()
    }

    fun loadRecentMissed(n: Int) {
        viewModelScope.launch {
            val selected = currentEvents.sortedByDescending { it.createdAt }
            val filtered = filterEventsByType(selected)
            val events = if (n <= 0) filtered else filtered.take(n)
            val eventIds = events.map { it.eventId }
            val members = if (eventIds.isNotEmpty()) repo.getMembersByEventIds(eventIds) else emptyList()

            (recentMissed as MutableStateFlow).value =
                StatsCalculator.computeRecentMissed(events, members, n)
        }
    }

    private fun loadMonth(option: MonthOption) {
        viewModelScope.launch {
            (loading as MutableStateFlow).value = true
            val events = repo.getEventsInRange(option.startMs, option.endMs)
            currentEvents = events
            val eventIds = events.map { it.eventId }
            val members = if (eventIds.isNotEmpty()) repo.getMembersByEventIds(eventIds) else emptyList()
            currentMembers = members

            // 总览
            (overview as MutableStateFlow).value =
                StatsCalculator.computeOverview(events, members)

            // 成员统计
            val rawMembers = StatsCalculator.computeMonthly(events, members)
            (memberStats as MutableStateFlow).value = rawMembers
            applySort()

            // 战报摘要
            (eventSummaries as MutableStateFlow).value =
                StatsCalculator.computeEventSummaries(events, members)

            // 未进攻排行（默认当月全部）
            (recentMissed as MutableStateFlow).value =
                StatsCalculator.computeRecentMissed(
                    events.sortedByDescending { it.createdAt }, members, 0
                )

            (loading as MutableStateFlow).value = false
        }
    }

    private fun applySort() {
        val raw = (memberStats as MutableStateFlow).value
        val sorted = when (sortBy.value) {
            MemberSortBy.EFFECTIVE_RATE -> raw.sortedByDescending { it.effectiveRate }
            MemberSortBy.TOTAL_STARS -> raw.sortedByDescending { it.totalStars }
            MemberSortBy.PARTICIPATED -> raw.sortedByDescending { it.participated }
            MemberSortBy.ATTACKED -> raw.sortedByDescending { it.attacked }
        }
        (memberStats as MutableStateFlow).value = sorted
    }

    private fun filterEventsByType(events: List<WarEventEntity>): List<WarEventEntity> {
        return when (typeFilter.value) {
            TypeFilter.ALL -> events
            TypeFilter.WAR -> events.filter { it.eventType != "league" }
            TypeFilter.LEAGUE -> events.filter { it.eventType == "league" }
        }
    }

    private fun recomputeForFilter() {
        val events = filterEventsByType(currentEvents)
        val eventIds = events.map { it.eventId }.toSet()
        val members = currentMembers.filter { it.eventId in eventIds }

        val rawMembers = StatsCalculator.computeMonthly(events, members)
        (memberStats as MutableStateFlow).value = rawMembers
        applySort()

        (recentMissed as MutableStateFlow).value =
            StatsCalculator.computeRecentMissed(
                events.sortedByDescending { it.createdAt }, members, 0
            )
    }
}
