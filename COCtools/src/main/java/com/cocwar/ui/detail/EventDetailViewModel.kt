package com.cocwar.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.model.Attack
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventDetailViewModel(private val repo: WarRepository, private val eventId: String) : ViewModel() {
    val event: StateFlow<WarEventEntity?> = repo.getEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val members: StateFlow<List<MemberEntity>> = repo.getMembers(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // === 名称编辑状态 ===
    private val _isEditingName = MutableStateFlow(false)
    val isEditingName: StateFlow<Boolean> = _isEditingName
    private val _editingName = MutableStateFlow("")
    val editingName: StateFlow<String> = _editingName

    fun startEditName() {
        _editingName.value = event.value?.eventName ?: ""
        _isEditingName.value = true
    }

    fun updateEditingName(name: String) {
        _editingName.value = name
    }

    fun cancelEditName() {
        _isEditingName.value = false
    }

    fun saveEventName(newName: String) {
        viewModelScope.launch {
            repo.updateEventName(eventId, newName)
            _isEditingName.value = false
        }
    }

    // === 成员编辑 ===

    /** 切换某个成员的某次进攻状态 used ↔ unused */
    fun toggleAttackStatus(member: MemberEntity, attackOrder: Int) {
        viewModelScope.launch {
            val newAttacks = member.attacks.map { attack ->
                if (attack.attackOrder == attackOrder) {
                    attack.copy(
                        status = if (attack.status == "used") "unused" else "used",
                        destructionPercentage = if (attack.status == "used") 0 else attack.destructionPercentage
                    )
                } else attack
            }
            val newTotalStars = computeTotalStars(newAttacks)
            repo.updateMember(member.copy(attacks = newAttacks, totalStars = newTotalStars))
        }
    }

    /** 修改某次进攻的摧毁率 */
    fun updateAttackDestruction(member: MemberEntity, attackOrder: Int, destruction: Int) {
        viewModelScope.launch {
            val newAttacks = member.attacks.map { attack ->
                if (attack.attackOrder == attackOrder) {
                    attack.copy(
                        destructionPercentage = destruction.coerceIn(0, 100),
                        status = "used"
                    )
                } else attack
            }
            val newTotalStars = computeTotalStars(newAttacks)
            repo.updateMember(member.copy(attacks = newAttacks, totalStars = newTotalStars))
        }
    }

    /** 根据攻击列表计算总星数（每 50% 摧毁 = 1 星，100% = 3 星） */
    private fun computeTotalStars(attacks: List<Attack>): Int {
        return attacks.filter { it.status == "used" }.fold(0) { acc, attack ->
            acc + when {
                attack.destructionPercentage >= 100 -> 3
                attack.destructionPercentage >= 50 -> 2
                attack.destructionPercentage > 0 -> 1
                else -> 0
            }
        }
    }
}
