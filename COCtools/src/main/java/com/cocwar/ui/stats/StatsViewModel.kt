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
import com.cocwar.domain.TopMemberScore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** 成员排序方式 */
enum class MemberSortBy(val label: String) {
    ATTACKED_COUNT("有效参战次数"),
    THREE_STAR_RATE("三星率")
}

/** 类型筛选 —— 部落战和联赛完全独立，无「全部」选项 */
enum class TypeFilter(val label: String) {
    WAR("部落战"),
    LEAGUE("联赛")
}

/** 统计视图（二级筛选，随类型变化：部落战四项、联赛两项） */
enum class StatsView(val label: String) {
    OVERVIEW("总览"),
    RANKING("排名"),
    WARNING("预警"),
    TOP("本月最佳");

    companion object {
        /** 指定类型可用的视图集合 */
        fun forType(type: TypeFilter): List<StatsView> = when (type) {
            TypeFilter.WAR -> entries
            TypeFilter.LEAGUE -> listOf(OVERVIEW, WARNING)
        }
    }
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
    val topMembers: StateFlow<List<TopMemberScore>> = MutableStateFlow(emptyList())
    val loading: StateFlow<Boolean> = MutableStateFlow(false)

    // --- 排序 ---
    val sortBy: StateFlow<MemberSortBy> = MutableStateFlow(MemberSortBy.ATTACKED_COUNT)

    // --- 类型筛选 ---
    val typeFilter: StateFlow<TypeFilter> = MutableStateFlow(TypeFilter.WAR)

    // --- 未进攻窗口 ---
    private val _recentMissedWindow = MutableStateFlow(0)
    val recentMissedWindow: StateFlow<Int> = _recentMissedWindow

    // 缓存当月原始数据
    private var currentEvents: List<WarEventEntity> = emptyList()
    private var currentMembers: List<MemberEntity> = emptyList()

    // 月份加载任务（切换月份时取消旧任务，防止乱序覆盖）
    private var monthLoadJob: Job? = null

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

    fun setRecentMissedWindow(n: Int) {
        _recentMissedWindow.value = n
        recomputeRecentMissed()
    }

    fun loadRecentMissed(n: Int) {
        setRecentMissedWindow(n)
    }

    private fun loadMonth(option: MonthOption) {
        // 取消上一次未完成的月份加载，避免快速切换月份时旧请求后完成覆盖新数据
        monthLoadJob?.cancel()
        monthLoadJob = viewModelScope.launch {
            (loading as MutableStateFlow).value = true
            val events = repo.getEventsInRange(option.startMs, option.endMs)
            currentEvents = events
            val eventIds = events.map { it.eventId }
            val members = if (eventIds.isNotEmpty()) repo.getMembersByEventIds(eventIds) else emptyList()
            currentMembers = members

            // 应用当前类型筛选
            recomputeForFilter()

            (loading as MutableStateFlow).value = false
        }
    }

    private fun applySort() {
        val raw = (memberStats as MutableStateFlow).value
        val sorted = when (sortBy.value) {
            MemberSortBy.ATTACKED_COUNT -> raw.sortedWith(
                compareByDescending<MemberMonthlyStat> { it.attacked }
                    .thenByDescending { it.threeStarRate }
                    .thenByDescending { it.avgDestruction }
            )
            MemberSortBy.THREE_STAR_RATE -> raw.sortedWith(
                compareByDescending<MemberMonthlyStat> { it.threeStarRate }
                    .thenByDescending { it.attacked }
                    .thenByDescending { it.avgDestruction }
            )
        }
        (memberStats as MutableStateFlow).value = sorted
    }

    private fun filterEventsByType(events: List<WarEventEntity>): List<WarEventEntity> {
        return when (typeFilter.value) {
            TypeFilter.WAR -> events.filter { it.eventType != "league" }
            TypeFilter.LEAGUE -> events.filter { it.eventType == "league" }
        }
    }

    private fun recomputeForFilter() {
        val events = filterEventsByType(currentEvents)
        val eventIds = events.map { it.eventId }.toSet()
        val members = currentMembers.filter { it.eventId in eventIds }

        // 总览按当前类型筛选，展示对应类型的数据
        (overview as MutableStateFlow).value =
            StatsCalculator.computeOverview(events, members)

        // 本月最佳独立视图：固定使用全量数据（积分制仅统计部落战），不受类型筛选影响
        (topMembers as MutableStateFlow).value =
            StatsCalculator.computeTopMembers(currentEvents, currentMembers)

        val rawMembers = StatsCalculator.computeMonthly(events, members)
        (memberStats as MutableStateFlow).value = rawMembers
        applySort()

        (eventSummaries as MutableStateFlow).value =
            StatsCalculator.computeEventSummaries(events, members)

        recomputeRecentMissed(events, members)
    }

    private fun recomputeRecentMissed(
        events: List<WarEventEntity> = filterEventsByType(currentEvents),
        members: List<MemberEntity> = currentMembers.filter { it.eventId in events.map { e -> e.eventId }.toSet() }
    ) {
        val selected = events.sortedByDescending { it.createdAt }
        val window = _recentMissedWindow.value
        val windowed = if (window <= 0) selected else selected.take(window)
        val windowedIds = windowed.map { it.eventId }.toSet()
        val windowedMembers = currentMembers.filter { it.eventId in windowedIds }
        (recentMissed as MutableStateFlow).value =
            StatsCalculator.computeRecentMissed(windowed, windowedMembers, window)
    }
}
