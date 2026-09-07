package com.cocwar.ui.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.model.ParseResult
import com.cocwar.data.model.ParsedEvent
import com.cocwar.data.ocr.OcrPrompts
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.launch

class ImportViewModel(
    private val repo: WarRepository
) : ViewModel() {

    /** 解析 CSV 战报（B2，RULES §4.15）：按类型填充槽位。 */
    suspend fun parseCsv(
        text: String,
        eventType: String,
        slotCount: Int
    ): ParseResult {
        val roleMap = repo.rosterRoleMap()
        return com.cocwar.data.csv.CsvImporter.parse(
            text = text,
            slotCount = slotCount,
            eventType = eventType,
            rosterRoles = roleMap
        )
    }

    suspend fun generateName(eventType: String, eventRound: Int): String =
        repo.generateEventName(eventType, eventRound)

    suspend fun generateNameForDate(eventType: String, eventRound: Int, dateMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        return repo.generateEventName(eventType, eventRound, cal)
    }

    /**
     * 生成「给外部 AI 用的识别提示词」：注入在册成员名单 + 形近名提醒，
     * [eventType] 决定部落战/联赛的列规则。App 本身不调用任何 AI 接口，
     * 用户复制提示词后到豆包等外部软件识别，再把结果 CSV 粘贴回来。
     */
    suspend fun buildPromptText(eventType: String?): String =
        OcrPrompts.buildPrompt(repo.getRoster(), eventType)

    suspend fun loadRoster(): List<String> = repo.getRoster()

    /**
     * 入册确认闸：检测即将自动加入花名册的新名字中与在册成员疑似同名的项。
     * 返回空列表表示无冲突，可直接保存。
     */
    suspend fun findRosterConflicts(names: List<String>): List<RosterConflict> =
        detectRosterConflicts(names, repo.getRoster())

    /**
     * 保存事件：自动把名单外的成员加入花名册，再导入事件，全部串行在
     * 同一个 viewModelScope 协程中完成。避免 onSaved→popBackStack 后
     * 名单写入协程被取消导致新成员丢失。
     */
    fun save(parsed: ParsedEvent, onSaved: () -> Unit) {
        viewModelScope.launch {
            val roster = repo.getRoster()
            val newNames = parsed.members.map { it.playerName }.filter { it !in roster }.distinct()
            if (newNames.isNotEmpty()) repo.addToRoster(newNames)
            repo.importEvent(parsed)
            onSaved()
        }
    }
}
