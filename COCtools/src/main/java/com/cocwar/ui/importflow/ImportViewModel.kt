package com.cocwar.ui.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.parser.WarJsonParser
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.launch

class ImportViewModel(private val repo: WarRepository) : ViewModel() {

    fun parse(json: String): WarJsonParser.ParseResult = WarJsonParser.parse(json)

    suspend fun generateName(eventType: String, eventRound: Int): String =
        repo.generateEventName(eventType, eventRound)

    suspend fun loadRoster(): List<String> = repo.getRoster()

    /**
     * 保存事件：自动把名单外的成员加入花名册，再导入事件，全部串行在
     * 同一个 viewModelScope 协程中完成。避免 onSaved→popBackStack 后
     * 名单写入协程被取消导致新成员丢失。
     */
    fun save(parsed: WarJsonParser.ParsedEvent, onSaved: () -> Unit) {
        viewModelScope.launch {
            val roster = repo.getRoster()
            val newNames = parsed.members.map { it.playerName }.filter { it !in roster }.distinct()
            if (newNames.isNotEmpty()) repo.addToRoster(newNames)
            repo.importEvent(parsed)
            onSaved()
        }
    }
}
