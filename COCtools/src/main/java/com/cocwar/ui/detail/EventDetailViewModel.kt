package com.cocwar.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.data.repository.WarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventDetailViewModel(private val repo: WarRepository, private val eventId: String) : ViewModel() {
    val event: StateFlow<WarEventEntity?> = repo.getEvent(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** DB 原始成员列表 */
    private val rawMembers: StateFlow<List<MemberEntity>> = repo.getMembers(eventId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 角色已根据花名册实时映射后的成员列表——与统计页保持一致，避免同一成员在不同页面显示不同角色 */
    val members: StateFlow<List<MemberEntity>> = combine(
        rawMembers,
        repo.observeRoster()
    ) { memberList, roster ->
        val roleMap = roster.associate { it.name to it.role }
        if (roleMap.isEmpty()) memberList
        else memberList.map { m -> m.copy(role = roleMap[m.playerName] ?: m.role) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** 修改某次进攻的摧毁率（>0 视为已使用，0 视为未进攻），一次原子写入。
     *  先重新获取最新成员数据再写入，避免快速连续编辑时基于过期快照覆盖前一次修改。
     *  注意：星数来自游戏 API 原始数据，不根据摧毁率重新推导——摧毁率无法可靠映射到星数
     *  （50-99% 可能为 1 星或 2 星，取决于大本营是否被摧毁）。 */
    fun updateAttack(member: MemberEntity, attackOrder: Int, destruction: Int) {
        viewModelScope.launch {
            // 重新获取最新数据，防止快照过期覆盖前次编辑
            val latest = repo.getMembersByEventIds(listOf(eventId))
                .find { it.id == member.id } ?: member
            val newAttacks = latest.attacks.map { attack ->
                if (attack.attackOrder == attackOrder) {
                    attack.copy(destructionPercentage = destruction.coerceIn(0, 100))
                } else attack
            }
            // 保留原始 totalStars（不根据摧毁率重新推导），仅更新进攻数据
            repo.updateMember(latest.copy(attacks = newAttacks, totalStars = latest.totalStars))
        }
    }
}
