package com.cocwar.data.csv

import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.domain.MemberMonthlyStat
import com.cocwar.domain.StatsOverview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出（RULES §4.14 / §4.16）。
 * 全部 UTF-8 + BOM；数字格式化一律 Locale.US。
 */
object CsvExporter {

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * 全量导出：单文件多事件宽表。
     * 表头 `事件名称,类型,时间,成员名,排名,职位,总星数,进攻1摧毁率,进攻2摧毁率`；
     * 部落战 2 列进攻、联赛第 2 列留空；无成员事件输出单行汇总。
     */
    fun exportEventsCsv(
        events: List<WarEventEntity>,
        membersByEvent: Map<String, List<MemberEntity>>
    ): String {
        val sb = StringBuilder(CsvCodec.BOM)
        sb.append(CsvCodec.row(listOf(
            "事件名称", "类型", "时间", "成员名", "排名", "职位", "总星数", "进攻1摧毁率", "进攻2摧毁率"
        ))).append("\n")

        events.sortedBy { it.createdAt }.forEach { ev ->
            val type = if (ev.eventType == "league") "联赛" else "部落战"
            val time = timeFmt.format(Date(ev.createdAt))
            val members = membersByEvent[ev.eventId].orEmpty().sortedBy { it.rank }
            if (members.isEmpty()) {
                sb.append(CsvCodec.row(listOf(
                    ev.eventName, type, time, "", "", "", "${ev.clanTotalStars}", "", ""
                ))).append("\n")
            } else {
                members.forEach { m ->
                    val a1 = m.attacks.firstOrNull { it.attackOrder == 1 }
                        ?.destructionPercentage?.let { "$it%" } ?: ""
                    val a2 = m.attacks.firstOrNull { it.attackOrder == 2 }
                        ?.destructionPercentage?.let { "$it%" } ?: ""
                    sb.append(CsvCodec.row(listOf(
                        ev.eventName, type, time, m.playerName, "${m.rank}",
                        m.role, "${m.totalStars}", a1, a2
                    ))).append("\n")
                }
            }
        }
        return sb.toString()
    }

    /**
     * 月度报告：标题行 + 汇总行 + 成员明细行。
     * 口径全部复用 `StatsCalculator`（computeMonthly / computeOverview），不引入新统计（RULES §4.16）。
     */
    fun exportMonthlyReportCsv(
        title: String,
        stats: List<MemberMonthlyStat>,
        overview: StatsOverview?
    ): String {
        val sb = StringBuilder(CsvCodec.BOM)
        sb.append("# ").append(title).append("\n")

        overview?.let { o ->
            sb.append(CsvCodec.row(listOf(
                "汇总", "总场次 ${o.totalEvents}", "总星数 ${o.totalStars}",
                "进攻率 ${formatRate(o.overallAttackRate)}", "三星率 ${formatRate(o.threeStarRate)}",
                "满星率 ${formatRate(o.fullStarRate)}", "均摧毁 ${formatPct(o.avgDestruction)}"
            ))).append("\n")
        }

        sb.append(CsvCodec.row(listOf(
            "成员", "职位", "参战场次", "有效参战", "参战率", "进攻率", "未进攻场次",
            "总星数", "场均星数", "三星次数", "三星率", "场均摧毁率"
        ))).append("\n")

        stats.forEach { s ->
            sb.append(CsvCodec.row(listOf(
                s.playerName, s.role, "${s.participated}", "${s.attacked}",
                formatRate(s.participationRate), formatRate(s.effectiveRate), "${s.missedCount}",
                "${s.totalStars}", "%.2f".format(Locale.US, s.avgStars), "${s.threeStarCount}",
                formatRate(s.threeStarRate), formatPct(s.avgDestruction)
            ))).append("\n")
        }
        return sb.toString()
    }

    private fun formatRate(v: Float): String = "%.1f%%".format(Locale.US, v * 100)

    private fun formatPct(v: Float): String = "%.1f%%".format(Locale.US, v)
}
