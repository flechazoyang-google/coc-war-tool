package com.cocwar.ui.members

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel
import kotlinx.coroutines.launch

/**
 * 已离队成员页：列出已标记离队的成员（职位保留），
 * 每行「恢复」一键回到在册名单，右上角可全部恢复；长按可移出花名册（可撤销）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartedMembersScreen(onBack: () -> Unit) {
    val viewModel: MemberManageViewModel = warViewModel { MemberManageViewModel(it) }
    val departed by viewModel.departed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteName by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DepartedTopBar(
                hasDeparted = departed.isNotEmpty(),
                onBack = onBack,
                onRestoreAll = {
                    viewModel.restoreAllDeparted()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "已恢复全部已离队成员",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    ) { padding ->
        DepartedMemberList(
            departed = departed,
            onRestore = { name ->
                scope.launch { restoreWithUndo(name, viewModel, snackbarHostState) }
            },
            onDeleteRequest = { pendingDeleteName = it },
            modifier = Modifier.padding(padding)
        )
    }

    pendingDeleteName?.let { name ->
        DepartedDeleteDialog(
            name = name,
            onConfirm = {
                pendingDeleteName = null
                scope.launch { removeNameWithUndo(name, departed, viewModel, snackbarHostState) }
            },
            onDismiss = { pendingDeleteName = null }
        )
    }
}

/** 已离队成员页顶栏：返回 + 「全部恢复」（无已离队成员时隐藏）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartedTopBar(
    hasDeparted: Boolean,
    onBack: () -> Unit,
    onRestoreAll: () -> Unit
) {
    TopAppBar(
        title = { Text("已离队成员", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
        },
        actions = {
            if (hasDeparted) {
                TextButton(onClick = onRestoreAll) { Text("全部恢复") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

/** 移出花名册确认框：长按条目 → 菜单「移出花名册」→ 确认 → 执行删除（可撤销）。 */
@Composable
private fun DepartedDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移出花名册") },
        text = { Text("确定将「$name」从花名册中删除？删除后可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.cocColors.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 已离队成员列表：空态提示或名单（每行恢复按钮，长按弹删除菜单）。 */
@Composable
private fun DepartedMemberList(
    departed: List<MemberRosterEntity>,
    onRestore: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (departed.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "没有已离队成员",
                body = "在「成员」页的疑似离队确认中\n标记离队后会出现在这里"
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        itemsIndexed(departed, key = { _, entry -> entry.name }) { index, entry ->
            DepartedMemberRow(
                entry = entry,
                onRestore = { onRestore(entry.name) },
                onDeleteRequest = { onDeleteRequest(entry.name) }
            )
            if (index < departed.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 20.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.cocColors.hairline)
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 恢复 + Snackbar 撤销（撤销 = 重新标记离队）。 */
private suspend fun restoreWithUndo(
    name: String,
    viewModel: MemberManageViewModel,
    snackbarHostState: SnackbarHostState
) {
    viewModel.restoreDeparted(name)
    val result = snackbarHostState.showSnackbar(
        message = "已恢复「$name」",
        actionLabel = "撤销",
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
        viewModel.markDeparted(name)
    }
}

/** 移出花名册：立即删除 → Snackbar 撤销（含职位恢复），防误触。 */
private suspend fun removeNameWithUndo(
    name: String,
    departed: List<MemberRosterEntity>,
    viewModel: MemberManageViewModel,
    snackbarHostState: SnackbarHostState
) {
    val savedRole = departed.find { it.name == name }?.role ?: "member"
    viewModel.removeName(name)
    val result = snackbarHostState.showSnackbar(
        message = "已将「$name」移出花名册",
        actionLabel = "撤销",
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
        viewModel.addNames(listOf(name))
        if (savedRole != "member") {
            viewModel.updateRole(name, savedRole)
        }
    }
}

/** 已离队成员行：名字 + 职位 + 「恢复」按钮；长按弹出「移出花名册」菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DepartedMemberRow(
    entry: MemberRosterEntity,
    onRestore: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.role.equals("member", ignoreCase = true)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        roleLabel(entry.role),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColor(entry.role)
                    )
                }
            }
            TextButton(onClick = onRestore) {
                Text("恢复", color = MaterialTheme.cocColors.accent)
            }
        }

        // 长按操作菜单：移出花名册（确认框由页面层负责）
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("移出花名册", color = MaterialTheme.cocColors.danger) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.cocColors.danger,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuOpen = false
                    onDeleteRequest()
                }
            )
        }
    }
}
