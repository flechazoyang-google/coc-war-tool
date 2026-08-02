package com.cocwar.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemberManageViewModel(private val repo: WarRepository) : ViewModel() {

    private val _roster = MutableStateFlow<List<MemberRosterEntity>>(emptyList())
    val roster: StateFlow<List<MemberRosterEntity>> = _roster

    init {
        viewModelScope.launch {
            repo.observeRoster().collect { list -> _roster.value = list }
        }
    }

    fun addNames(names: List<String>) {
        viewModelScope.launch { repo.addToRoster(names) }
    }

    fun removeName(name: String) {
        viewModelScope.launch { repo.removeFromRoster(name) }
    }

    /** 设置名单成员职位（leader/coLeader/elder/member）。 */
    fun updateRole(name: String, role: String) {
        viewModelScope.launch { repo.updateRosterRole(name, role) }
    }
}
