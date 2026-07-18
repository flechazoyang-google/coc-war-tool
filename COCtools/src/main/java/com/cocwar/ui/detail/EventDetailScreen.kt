package com.cocwar.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.model.Attack
import com.cocwar.di.warViewModel
import com.cocwar.domain.StatsCalculator
import com.cocwar.domain.WarStats
import com.cocwar.ui.components.AttackStatusChip
import com.cocwar.ui.components.InfoRow
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.StatTile
import com.cocwar.ui.components.TypeBadge
import com.cocwar.ui.util.eventTypeLabel
import com.cocwar.ui.util.parseEventRoundFromName
import com.cocwar.ui.util.roleColor
import kotlinx.coroutines.launch

private data class EditAttackInfo(val member: MemberEntity, val attackOrder: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(eventId: String, onBack: () -> Unit) {
    val viewModel: EventDetailViewModel = warViewModel { EventDetailViewModel(it, eventId) }
    val event by viewModel.event.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val isEditingName by viewModel.isEditingName.collectAsStateWithLifecycle()
    val editingName by viewModel.editingName.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 攻击编辑弹窗状态
    var editingAttack by remember { mutableStateOf<EditAttackInfo?>(null) }
    var editDestructionText by remember { mutableStateOf("") }
    var editStatusUsed by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = { viewModel.updateEditingName(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("战报名称") }
                        )
                    } else {
                        Text(
                            text = event?.eventName?.ifBlank { "战报详情" } ?: "战报详情",
                            modifier = Modifier.clickable { viewModel.startEditName() }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditingName) viewModel.cancelEditName()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEditingName) {
                        IconButton(onClick = {
                            val newName = editingName.trim()
                            if (newName.isNotBlank()) viewModel.saveEventName(newName)
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "确认", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.cancelEditName() }) {
                            Icon(Icons.Filled.Close, contentDescription = "取消")
                        }
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                val app = context.applicationContext as CocWarApplication
                                val json = app.repository.exportEventJson(eventId)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    putExtra(Intent.EXTRA_SUBJECT, event?.eventName ?: "战报")
                                }
                                context.startActivity(Intent.createChooser(intent, "导出战报"))
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "导出")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (event == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val ev = event!!
        val stats = remember(ev, members) { StatsCalculator.compute(ev, members) }

        Column(Modifier.fillMaxSize().padding(padding)) {
            HeaderCard(ev)
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("概览") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("统计") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("成员") })
            }
            when (tab) {
                0 -> OverviewTab(ev, stats, Modifier.weight(1f))
                1 -> StatsTab(stats, Modifier.weight(1f))
                2 -> MembersTab(members, Modifier.weight(1f)) { member, attackOrder ->
                    val attack = member.attacks.find { it.attackOrder == attackOrder }
                    editingAttack = EditAttackInfo(member, attackOrder)
                    editStatusUsed = attack?.status == "used"
                    editDestructionText = attack?.destructionPercentage?.toString() ?: "0"
                }
            }
        }
    }

    // 攻击编辑弹窗
    editingAttack?.let { info ->
        AttackEditDialog(
            attackOrder = info.attackOrder,
            statusUsed = editStatusUsed,
            destructionText = editDestructionText,
            onStatusChange = { editStatusUsed = it },
            onDestructionChange = { editDestructionText = it },
            onDismiss = { editingAttack = null },
            onConfirm = {
                val destruction = editDestructionText.toIntOrNull()?.coerceIn(0, 100) ?: 0
                val member = info.member
                viewModel.updateAttackDestruction(member, info.attackOrder, destruction)
                if (!editStatusUsed) {
                    // 标记为未使用：切换状态
                    viewModel.toggleAttackStatus(member, info.attackOrder)
                }
                editingAttack = null
            }
        )
    }
}

@Composable
private fun AttackEditDialog(
    attackOrder: Int,
    statusUsed: Boolean,
    destructionText: String,
    onStatusChange: (Boolean) -> Unit,
    onDestructionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第${attackOrder}次进攻") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("状态：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = statusUsed,
                        onClick = { onStatusChange(true) },
                        label = { Text("已使用") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = !statusUsed,
                        onClick = { onStatusChange(false) },
                        label = { Text("未使用") }
                    )
                }
                if (statusUsed) {
                    OutlinedTextField(
                        value = destructionText,
                        onValueChange = { text ->
                            // 只允许数字输入
                            if (text.isEmpty() || text.all { it.isDigit() } && text.length <= 3) {
                                onDestructionChange(text)
                            }
                        },
                        label = { Text("摧毁率 (0-100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun HeaderCard(ev: com.cocwar.data.db.WarEventEntity) {
    val gradient = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(gradient).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TypeBadge(ev.eventType, parseEventRoundFromName(ev.eventName))
                    if (ev.isSample) {
                        Text("示例", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${ev.clanTotalStars}",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun OverviewTab(ev: com.cocwar.data.db.WarEventEntity, stats: WarStats, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("战报信息")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow("类型", eventTypeLabel(ev.eventType) + if (ev.eventType == "league") " · 第${parseEventRoundFromName(ev.eventName)}轮" else "")
                InfoRow("总星数", "${ev.clanTotalStars}")
                InfoRow("成员人数", "${stats.totalMembers}")
            }
        }
    }
}

@Composable
private fun StatsTab(stats: WarStats, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("进攻概况")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("进攻人数", "${stats.attackerCount}/${stats.totalMembers}", Modifier.weight(1f))
                StatTile(
                    "未进攻人数", "${stats.nonAttackerCount}", Modifier.weight(1f),
                    highlight = stats.nonAttackerCount > 0,
                    valueColor = if (stats.nonAttackerCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                StatTile("使用攻击数", "${stats.totalUsedAttacks}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("累计星数", "${stats.totalStarsObtained}", Modifier.weight(1f))
                StatTile("三星次数", "${stats.threeStarCount}", Modifier.weight(1f))
                StatTile("三星率", "%.0f%%".format(stats.threeStarRate * 100), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("未进攻人员公示")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (stats.nonAttackerCount > 0) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                if (stats.nonAttackerNames.isEmpty()) {
                    Text("全部成员均已出手，无未进攻人员！", color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(
                        "以下 ${stats.nonAttackerCount} 人本轮未出手：",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    stats.nonAttackerNames.forEachIndexed { i, name ->
                        Text("${i + 1}. $name", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MembersTab(
    members: List<MemberEntity>,
    modifier: Modifier = Modifier,
    onEditAttack: (MemberEntity, Int) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        items(members, key = { it.id }) { member -> MemberCard(member, onEditAttack) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MemberCard(
    member: MemberEntity,
    onEditAttack: (MemberEntity, Int) -> Unit = { _, _ -> }
) {
    val hasAttack = member.attacks.any { it.status == "used" }
    val nameColor = roleColor(member.role)
    val initial = member.playerName.firstOrNull()?.toString() ?: "?"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(nameColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = nameColor
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(member.playerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = nameColor)
                        Text("#${member.rank}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AttackStatusChip(hasAttack)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${member.totalStars} 星", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(6.dp))
            member.attacks.forEach { attack ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditAttack(member, attack.attackOrder) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (attack.status == "used") "第${attack.attackOrder}次"
                        else "第${attack.attackOrder}次（未使用）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (attack.status == "used") MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (attack.status == "used") {
                            Text(
                                "${attack.destructionPercentage}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (attack.destructionPercentage == 100) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
