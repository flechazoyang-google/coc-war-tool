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

    fun addToRoster(names: List<String>) {
        viewModelScope.launch { repo.addToRoster(names) }
    }

    fun save(parsed: WarJsonParser.ParsedEvent, onSaved: () -> Unit) {
        viewModelScope.launch { repo.importEvent(parsed); onSaved() }
    }
}
