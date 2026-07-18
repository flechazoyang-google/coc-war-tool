package com.cocwar.ui.eventlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.cocwar.data.db.WarEventEntity

class EventListViewModel(private val repo: WarRepository) : ViewModel() {

    val events: StateFlow<List<WarEventEntity>> = repo.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteEvent(id: String) {
        viewModelScope.launch { repo.deleteEvent(id) }
    }
}
