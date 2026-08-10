package com.cocwar.ui.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 战报 / 统计页筛选条件的跨进程持久化。
 *
 * 页面内的 rememberSaveable 依赖 Activity 的 SavedState：用户从最近任务划掉应用
 * （删除后台）后系统会清除 SavedState，筛选条件随之回到默认值。这里用
 * SharedPreferences 兜底持久化，重开应用后仍能恢复上次的筛选选择。
 * （轻量非敏感配置，与 ThemePrefs / UpdateConfig 同一存储策略。）
 */
object FilterPrefs {

    private const val PREFS_NAME = "coc_war_filter_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ================= 战报页 =================
    private const val KEY_EVENT_TYPE = "eventlist_type"    // "0" 部落战 / "1" 联赛
    private const val KEY_EVENT_YEAR = "eventlist_year"    // Int，缺省 = 「全部」
    private const val KEY_EVENT_MONTH = "eventlist_month"  // Int，缺省 = 「全部」

    fun eventType(context: Context): String =
        prefs(context).getString(KEY_EVENT_TYPE, "0") ?: "0"

    fun saveEventType(context: Context, type: String) {
        prefs(context).edit().putString(KEY_EVENT_TYPE, type).apply()
    }

    fun eventYear(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_EVENT_YEAR)) p.getInt(KEY_EVENT_YEAR, -1).takeIf { it >= 0 } else null
    }

    fun saveEventYear(context: Context, year: Int?) {
        prefs(context).edit().apply {
            if (year == null) remove(KEY_EVENT_YEAR) else putInt(KEY_EVENT_YEAR, year)
        }.apply()
    }

    fun eventMonth(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_EVENT_MONTH)) p.getInt(KEY_EVENT_MONTH, -1).takeIf { it >= 0 } else null
    }

    fun saveEventMonth(context: Context, month: Int?) {
        prefs(context).edit().apply {
            if (month == null) remove(KEY_EVENT_MONTH) else putInt(KEY_EVENT_MONTH, month)
        }.apply()
    }

    // ================= 统计页 =================
    private const val KEY_STATS_TYPE = "stats_type"          // TypeFilter.ordinal
    private const val KEY_STATS_VIEW = "stats_view"          // StatsView.ordinal
    private const val KEY_STATS_MONTH = "stats_month"        // MonthOption.label，如 "2026年7月"
    private const val KEY_STATS_MATCH = "stats_league_match" // LeagueMatch.label
    private const val KEY_STATS_RECENT_N = "stats_recent_n"  // 预警时间段 0/3/7

    fun statsType(context: Context): Int = prefs(context).getInt(KEY_STATS_TYPE, 0)

    fun saveStatsType(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_STATS_TYPE, index).apply()
    }

    fun statsView(context: Context): Int = prefs(context).getInt(KEY_STATS_VIEW, 0)

    fun saveStatsView(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_STATS_VIEW, index).apply()
    }

    fun statsMonth(context: Context): String =
        prefs(context).getString(KEY_STATS_MONTH, "") ?: ""

    fun saveStatsMonth(context: Context, label: String) {
        prefs(context).edit().putString(KEY_STATS_MONTH, label).apply()
    }

    fun statsLeagueMatch(context: Context): String =
        prefs(context).getString(KEY_STATS_MATCH, "") ?: ""

    fun saveStatsLeagueMatch(context: Context, label: String) {
        prefs(context).edit().putString(KEY_STATS_MATCH, label).apply()
    }

    fun statsRecentN(context: Context): Int {
        val n = prefs(context).getInt(KEY_STATS_RECENT_N, 0)
        // 防御越界：预警时间段仅 0/3/7 合法，非法值（负值/超大）回落默认「当月全部」
        return if (n in setOf(0, 3, 7)) n else 0
    }

    fun saveStatsRecentN(context: Context, n: Int) {
        prefs(context).edit().putInt(KEY_STATS_RECENT_N, n).apply()
    }
}
