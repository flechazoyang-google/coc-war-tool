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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.CocWarApplication
import com.cocwar.data.db.MemberEntity
import com.cocwar.data.db.WarEventEntity
import com.cocwar.di.warViewModel
import com.cocwar.domain.StatsCalculator
import com.cocwar.domain.WarStats
import com.cocwar.ui.components.AttackStatusChip
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.FilterPill
import com.cocwar.ui.components.InfoRow
import com.cocwar.ui.components.InitialAvatar
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SegmentedTabs
import com.cocwar.ui.components.StatTile
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.eventTypeLabel
import com.cocwar.ui.util.parseEventRoundFromName
import com.cocwar.ui.util.roleLabel
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = { viewModel.updateEditingName(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("战报名称") },
                            shape = CocShape.field
                        )
                    } else {
                        Text(
                            text = event?.eventName?.ifBlank { "战报详情" } ?: "战报详情",
                            style = MaterialTheme.typography.titleMedium,
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
                            Icon(Icons.Filled.Check, contentDescription = "确认", tint = MaterialTheme.cocColors.accent)
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
                            Icon(Icons.Filled.Share, contentDescription = "导出", modifier = Modifier.size(20.dp))
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
            Masthead(ev, stats)

            SegmentedTabs(
                options = listOf("概览", "统计", "成员"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )

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
                // 一次原子写入状态+摧毁率，避免旧的两次并发写竞态
                viewModel.updateAttack(member, info.attackOrder, used = editStatusUsed, destruction = destruction)
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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("状态", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    FilterPill(label = "已使用", selected = statusUsed, onClick = { onStatusChange(true) })
                    Spacer(Modifier.width(8.dp))
                    FilterPill(label = "未使用", selected = !statusUsed, onClick = { onStatusChange(false) })
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
                        shape = CocShape.field,
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

/**
 * 墨色刊头：实色深块 + 纸色文字 + 黄铜星数，编辑式排版，零渐变。
 */
@Composable
private fun Masthead(ev: WarEventEntity, stats: WarStats) {
    val masthead = MaterialTheme.cocColors.masthead
    val onMast = MaterialTheme.cocColors.onMasthead
    val onMastSoft = MaterialTheme.cocColors.onMastheadSoft

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = CocShape.card,
        color = masthead,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            // 眉题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (ev.eventType == "league") "联赛 · 第${parseEventRoundFromName(ev.eventName)}轮" else "部落战",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ev.eventType == "league") MaterialTheme.cocColors.star else onMastSoft
                )
                if (ev.isSample) {
                    Text("示例数据", style = MaterialTheme.typography.labelSmall, color = onMastSoft)
                }
            }

            Spacer(Modifier.height(20.dp))

            // 星数大数字
            Row(verticalAlignment = Alignment.Bottom) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.cocColors.star,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(bottom = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${ev.clanTotalStars}",
                    style = MaterialTheme.typography.displayLarge,
                    color = onMast
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "总星数",
                    style = MaterialTheme.typography.labelMedium,
                    color = onMastSoft,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(onMastSoft.copy(alpha = 0.3f))
            )
            Spacer(Modifier.height(14.dp))

            // 元信息三栏
            Row(Modifier.fillMaxWidth()) {
                MastheadMeta("成员", "${stats.totalMembers}", Modifier.weight(1f))
                MastheadMeta("已出手", "${stats.attackerCount}", Modifier.weight(1f))
                MastheadMeta(
                    "未出手", "${stats.nonAttackerCount}", Modifier.weight(1f),
                    valueColor = if (stats.nonAttackerCount > 0) MaterialTheme.cocColors.danger else onMast
                )
            }
        }
    }
}

@Composable
private fun MastheadMeta(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.cocColors.onMasthead
) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cocColors.onMastheadSoft
        )
    }
}

@Composable
private fun OverviewTab(ev: WarEventEntity, stats: WarStats, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("战报信息")
        CocCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                InfoRow("名称", ev.eventName)
                InfoRow(
                    "类型",
                    eventTypeLabel(ev.eventType) + if (ev.eventType == "league") " · 第${parseEventRoundFromName(ev.eventName)}轮" else ""
                )
                InfoRow("总星数", "${ev.clanTotalStars}")
                InfoRow("成员人数", "${stats.totalMembers}")
                InfoRow(
                    "出手情况",
                    "${stats.attackerCount}/${stats.totalMembers} 人已出手",
                    valueColor = if (stats.nonAttackerCount > 0) MaterialTheme.cocColors.danger
                    else MaterialTheme.cocColors.accent,
                    showDivider = false
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatsTab(stats: WarStats, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("进攻概况")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("进攻人数", "${stats.attackerCount}/${stats.totalMembers}", Modifier.weight(1f))
                StatTile(
                    "未进攻人数", "${stats.nonAttackerCount}", Modifier.weight(1f),
                    highlight = stats.nonAttackerCount > 0,
                    valueColor = if (stats.nonAttackerCount > 0) MaterialTheme.cocColors.danger
                    else MaterialTheme.colorScheme.onSurface
                )
                StatTile("使用攻击数", "${stats.totalUsedAttacks}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("累计星数", "${stats.totalStarsObtained}", Modifier.weight(1f))
                StatTile("三星次数", "${stats.threeStarCount}", Modifier.weight(1f))
                StatTile("三星率", "%.0f%%".format(stats.threeStarRate * 100), Modifier.weight(1f))
            }
        }

        SectionTitle("未进攻人员公示")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CocShape.card,
            color = if (stats.nonAttackerCount > 0) MaterialTheme.cocColors.dangerSoft
            else MaterialTheme.cocColors.accentSoft,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (stats.nonAttackerCount > 0) MaterialTheme.cocColors.danger.copy(alpha = 0.3f)
                else MaterialTheme.cocColors.accent.copy(alpha = 0.3f)
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                if (stats.nonAttackerNames.isEmpty()) {
                    Text(
                        "全员已出手，无人缺席",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.accent
                    )
                } else {
                    Text(
                        "以下 ${stats.nonAttackerCount} 人本轮未出手",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.cocColors.danger
                    )
                    Spacer(Modifier.height(10.dp))
                    stats.nonAttackerNames.forEachIndexed { i, name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Text(
                                "%02d".format(i + 1),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.cocColors.danger.copy(alpha = 0.6f),
                                modifier = Modifier.width(26.dp)
                            )
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.cocColors.danger
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MembersTab(
    members: List<MemberEntity>,
    modifier: Modifier = Modifier,
    onEditAttack: (MemberEntity, Int) -> Unit = { _, _ -> }
) {
    LazyColumn(
        modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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

    CocCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(name = member.playerName, color = nameColor)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            member.playerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = nameColor
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "#${member.rank} · ${roleLabel(member.role)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AttackStatusChip(hasAttack)
            }

            Spacer(Modifier.height(11.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.cocColors.hairline)
            )
            Spacer(Modifier.height(9.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.cocColors.star,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${member.totalStars} 星",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.cocColors.star
                )
            }
            Spacer(Modifier.height(6.dp))

            member.attacks.forEachIndexed { index, attack ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditAttack(member, attack.attackOrder) }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (attack.status == "used") "第${attack.attackOrder}次进攻"
                        else "第${attack.attackOrder}次 · 未使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (attack.status == "used") MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (attack.status == "used") {
                            Text(
                                "${attack.destructionPercentage}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (attack.destructionPercentage == 100) MaterialTheme.cocColors.accent
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                if (index < member.attacks.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.cocColors.hairline.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
