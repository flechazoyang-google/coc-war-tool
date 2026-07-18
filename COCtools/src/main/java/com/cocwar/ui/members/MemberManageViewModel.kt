package com.cocwar.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemberManageViewModel(private val repo: WarRepository) : ViewModel() {

    private val _roster = MutableStateFlow<List<String>>(emptyList())
    val roster: StateFlow<List<String>> = _roster

    init {
        viewModelScope.launch {
            repo.observeRoster().collect { list -> _roster.value = list.map { it.name } }
        }
    }

    fun addNames(names: List<String>) {
        viewModelScope.launch { repo.addToRoster(names) }
    }

    fun removeName(name: String) {
        viewModelScope.launch { repo.removeFromRoster(name) }
    }
}
