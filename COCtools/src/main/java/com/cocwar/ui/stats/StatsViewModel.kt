package com.cocwar.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.repository.WarRepository
import com.cocwar.domain.EventStatSummary
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.RecentMissedRank
import com.cocwar.domain.StatsCalculator
import com.cocwar.domain.StatsOverview
import com.cocwar.domain.TopMemberScore
import com.cocwar.ui.util.parseLeagueMatchFromName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Calendar

/** 类型筛选 —— 部落战和联赛完全独立，无「全部」选项 */
enum class TypeFilter(val label: String) {
    WAR("部落战"),
    LEAGUE("联赛")
}

/** 统计视图（二级筛选，随类型变化：部落战四项、联赛三项） */
enum class StatsView(val label: String) {
    OVERVIEW("总览"),
    RANKING("成员数据"),
    WARNING("预警"),
    TOP("积分排行");

    companion object {
        /** 指定类型可用的视图集合 */
        fun forType(type: TypeFilter): List<StatsView> = when (type) {
            TypeFilter.WAR -> entries
            TypeFilter.LEAGUE -> listOf(OVERVIEW, RANKING, WARNING)
        }
    }
}

/** 联赛场次归属（月初场/月中场），联赛成员数据按「月份 + 场次归属」分组 */
enum class LeagueMatch(val label: String) {
    EARLY("月初场"),
    MID("月中场")
}

/** 可选月份 */
data class MonthOption(
    val year: Int,
    val month: Int,        // 1..12
    val label: String,     // "2026年7月"
    val startMs: Long,
    val endMs: Long
)

/** 成员单场战报明细（排名页详情弹窗表格用） */
data class MemberEventDetail(
    val eventName: String,
    val eventType: String,
    val stars: Int,
    val attacks: List<Attack>
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

    // 下拉刷新进度（与 loading 分离：loading 语义为「首次加载」，
    // refreshing 仅在用户手动刷新时置位，保证下拉指示器可见直到加载完成）
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    // --- 联赛场次归属（仅联赛成员数据视图生效） ---
    val leagueMatch: StateFlow<LeagueMatch> = MutableStateFlow(LeagueMatch.EARLY)

    // --- 类型筛选 ---
    val typeFilter: StateFlow<TypeFilter> = MutableStateFlow(TypeFilter.WAR)

    // --- 未进攻窗口 ---
    private val _recentMissedWindow = MutableStateFlow(0)
    val recentMissedWindow: StateFlow<Int> = _recentMissedWindow

    // 缓存当月原始数据
    private var currentEvents: List<WarEventEntity> = emptyList()
    private var currentMembers: List<MemberEntity> = emptyList()
    private var currentRoster: List<String> = emptyList()
    private var currentRosterRoles: Map<String, String> = emptyMap()

    // 成员数据视图事件列表（联赛按场次归属过滤；成员详情弹窗用）
    private var currentMemberViewEvents: List<WarEventEntity> = emptyList()

    // 月份加载任务（切换月份时取消旧任务，防止乱序覆盖）
    private var monthLoadJob: Job? = null

    // 缓存所有事件用于近N次计算
    private var allEventsDesc: List<WarEventEntity> = emptyList()

    init {
        // 响应式刷新：战报表或花名册任一变化（导入/删除/迁移/改职位/云端还原）都会自动
        // 重新构建月份列表并重载当前选中月份数据，统计页始终与数据库保持一致。
        // Room Flow 订阅后立即发射当前值，因此首次加载也由此驱动，无需额外初始化。
        viewModelScope.launch {
            combine(repo.events, repo.observeRoster()) { events, roster -> events to roster }
                .collect {
                    loadAvailableMonths()
                    selectedMonth.value?.let { m -> loadMonth(m) }
                }
        }
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

            // 仅当尚未选中月份，或原选中月份已不在新列表（该月数据被删除）时才重置为默认月；
            // 否则保留当前选中月份——否则 refresh() 先刷月份列表会把历史月份跳回最近月
            val stillValid = selectedMonth.value?.let { cur ->
                months.any { it.year == cur.year && it.month == cur.month }
            } ?: false
            if (!stillValid) {
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
    }

    fun selectMonth(option: MonthOption) {
        (selectedMonth as MutableStateFlow).value = option
        loadMonth(option)
    }

    /** 手动刷新当前月份数据（其他页面修改数据后同步到统计页）。同时刷新可选月份列表。 */
    fun refresh() {
        val current = selectedMonth.value ?: return
        viewModelScope.launch {
            _refreshing.value = true
            // 确保 UI 能收到 true→false 两次发射（join 可能因 job 已完成而不挂起，
            // 导致刷新状态被 StateFlow 合并，下拉指示器无法正常收起）
            yield()
            loadAvailableMonths()
            loadMonth(current)
            // 等待加载协程完成后再收起下拉指示器（loadMonth 内部异步，需 join）
            monthLoadJob?.join()
            _refreshing.value = false
        }
    }

    fun setLeagueMatch(match: LeagueMatch) {
        (leagueMatch as MutableStateFlow).value = match
        // 用户手动选择不触发回落：对话框选项已按目标月份（allEventsDesc）校验过存在性，
        // 此时 currentEvents 可能仍是旧月份数据，回落判断会误伤（回落仅在 loadMonth/类型切换时执行）
        recomputeForFilter(allowFallback = false)
    }

    fun setTypeFilter(filter: TypeFilter) {
        (typeFilter as MutableStateFlow).value = filter
        recomputeForFilter(allowFallback = true)
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
            // 职位以花名册为准：统计前用花名册角色覆盖成员快照，花名册改职位后历史统计同步更新
            currentRosterRoles = repo.rosterRoleMap()
            currentMembers = if (currentRosterRoles.isEmpty()) members else members.map {
                it.copy(role = currentRosterRoles[it.playerName] ?: it.role)
            }
            currentRoster = repo.getRoster()

            // 应用当前类型筛选（loadMonth 完成后 currentEvents 与所选月份一致，允许场次回落）
            recomputeForFilter(allowFallback = true)

            (loading as MutableStateFlow).value = false
        }
    }

    /**
     * 成员数据视图专用事件集合：部落战 = 类型过滤后整月；
     * 联赛 = 类型过滤后按场次归属（月初场/月中场）再过滤。
     * 归属解析：C1=0 → 月初场，C1=1 → 月中场，无法解析（非标准名）→ 月初场。
     */
    private fun memberViewEvents(events: List<WarEventEntity>): List<WarEventEntity> {
        if (typeFilter.value != TypeFilter.LEAGUE) return events
        val mid = leagueMatch.value == LeagueMatch.MID
        return events.filter { ev ->
            val m = parseLeagueMatchFromName(ev.eventName)
            if (mid) m == 2 else m != 2
        }
    }

    /**
     * 指定月份实际存在的联赛场次归属（供联赛成员数据页的场次选择）。
     * 月初场恒存在（无法解析归属的事件也计入月初场）；月中场仅当月存在 C1=1 事件时返回。
     */
    fun leagueMatchOptions(monthLabel: String): List<LeagueMatch> {
        val option = availableMonths.value.find { it.label == monthLabel } ?: return emptyList()
        val hasMid = allEventsDesc.any { ev ->
            ev.eventType == "league" &&
                ev.createdAt in option.startMs until option.endMs &&
                parseLeagueMatchFromName(ev.eventName) == 2
        }
        return if (hasMid) listOf(LeagueMatch.EARLY, LeagueMatch.MID)
        else listOf(LeagueMatch.EARLY)
    }

    private fun filterEventsByType(events: List<WarEventEntity>): List<WarEventEntity> {
        return when (typeFilter.value) {
            TypeFilter.WAR -> events.filter { it.eventType != "league" }
            TypeFilter.LEAGUE -> events.filter { it.eventType == "league" }
        }
    }

    private fun recomputeForFilter(allowFallback: Boolean = false) {
        val events = filterEventsByType(currentEvents)
        val eventIds = events.map { it.eventId }.toSet()
        val members = currentMembers.filter { it.eventId in eventIds }

        // 场次回落：联赛选中的月中场在本月不存在（无 C1=1 事件）时回落到月初场。
        // 仅在 loadMonth 完成 / 类型切换时执行（此时 currentEvents 与所选月份一致）；
        // events 为空（数据尚未加载完成，如进程重建后恢复场次选择）时不回落，
        // 避免空列表把刚恢复的「月中场」误判吞掉——loadMonth 完成后会再次执行本判断。
        if (allowFallback &&
            typeFilter.value == TypeFilter.LEAGUE &&
            leagueMatch.value == LeagueMatch.MID &&
            events.isNotEmpty() &&
            events.none { parseLeagueMatchFromName(it.eventName) == 2 }
        ) {
            (leagueMatch as MutableStateFlow).value = LeagueMatch.EARLY
        }

        // 总览按当前类型筛选，展示对应类型的数据（整月，不受场次归属影响）
        (overview as MutableStateFlow).value =
            StatsCalculator.computeOverview(events, members)

        // 积分排行独立视图：固定使用全量数据（积分制仅统计部落战），不受类型筛选影响
        (topMembers as MutableStateFlow).value =
            StatsCalculator.computeTopMembers(currentEvents, currentMembers, currentRoster, currentRosterRoles)

        // 战报情况表格：整月数据，不受场次归属影响
        (eventSummaries as MutableStateFlow).value =
            StatsCalculator.computeEventSummaries(events, members)

        recomputeRecentMissed(events, members)

        // 成员数据视图：联赛按场次归属过滤；固定按参战次数降序（次按三星率、场均摧毁）
        val memberEvents = memberViewEvents(events)
        currentMemberViewEvents = memberEvents
        val memberEventIds = memberEvents.map { it.eventId }.toSet()
        val memberMembers = currentMembers.filter { it.eventId in memberEventIds }
        val rawMembers = StatsCalculator.computeMonthly(memberEvents, memberMembers)
        (memberStats as MutableStateFlow).value = rawMembers.sortedWith(
            compareByDescending<MemberMonthlyStat> { it.attacked }
                .thenByDescending { it.threeStarRate }
                .thenByDescending { it.avgDestruction }
        )
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

    /**
     * 该成员本月（当前筛选类型内）逐场参战明细，按时间升序，仅实际参战场次。
     * 供排名页点击成员后弹出表格展示。
     */
    fun memberEventDetails(playerName: String): List<MemberEventDetail> {
        val eventTime = currentMemberViewEvents.associate { it.eventId to it.createdAt }
        val nameToEvent = currentMemberViewEvents.associate { it.eventId to it }
        return currentMembers
            .filter { it.playerName == playerName && it.eventId in eventTime.keys }
            .sortedBy { eventTime[it.eventId] ?: 0L }
            .map { m ->
                val ev = nameToEvent[m.eventId]
                MemberEventDetail(
                    eventName = ev?.eventName.orEmpty(),
                    eventType = ev?.eventType ?: "war",
                    stars = m.totalStars,
                    attacks = m.attacks
                )
            }
    }
}
