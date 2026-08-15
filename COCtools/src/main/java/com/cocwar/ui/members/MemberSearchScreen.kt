package com.cocwar.ui.members

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.theme.cocColors
import kotlinx.coroutines.launch

/**
 * 花名册-搜索页：独立全屏搜索。
 * 顶部输入框自动聚焦，输入时实时子串包含过滤（忽略大小写）；
 * 结果行与花名册一致：点击设置职位、长按删除（可撤销）。
 */
@Composable
fun MemberSearchScreen(onBack: () -> Unit) {
    val viewModel: MemberManageViewModel = warViewModel { MemberManageViewModel(it) }
    val roster by viewModel.roster.collectAsStateWithLifecycle()
    val absentCounts by viewModel.absentCounts.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingDeleteName by remember { mutableStateOf<String?>(null) }
    var editingRoleName by remember { mutableStateOf<String?>(null) }
    var detailName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    // 子串包含模糊过滤：输入为空显示全部名单（与花名册排序一致：职位 → 连续缺席场次从少到多）
    val filtered = remember(roster, absentCounts, query) {
        val q = query.trim()
        if (q.isEmpty()) sortRoster(roster, absentCounts)
        else roster.filter { it.name.contains(q, ignoreCase = true) }
    }

    // 删除成员：立即落库删除 → Snackbar 提供撤销（含角色恢复），防误触
    fun removeNameWithUndo(name: String) {
        scope.launch {
            val savedRole = roster.find { it.name == name }?.role ?: "member"
            viewModel.removeName(name)
            val result = snackbarHostState.showSnackbar(
                message = "已删除成员「$name」",
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
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 顶部搜索栏：返回 + 自动聚焦输入框 + 清除
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索成员名字") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close, "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CocShape.field,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                        cursorColor = MaterialTheme.cocColors.accent
                    )
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Box(Modifier.fillMaxSize()) {
                if (roster.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "名单为空",
                            body = "先到花名册导入成员"
                        )
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "没有匹配的成员",
                            body = "换个名字试试"
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(filtered, key = { _, entry -> entry.name }) { index, entry ->
                            MemberRow(
                                entry = entry,
                                index = index,
                                onClick = { detailName = entry.name },
                                onRoleClick = { editingRoleName = entry.name },
                                onDeleteRequest = { pendingDeleteName = entry.name }
                            )
                            if (index < filtered.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(start = 50.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.cocColors.hairline)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    // 删除成员确认框
    pendingDeleteName?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteName = null },
            title = { Text("删除成员") },
            text = { Text("确定将「$name」移出花名册？删除后可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteName = null
                    removeNameWithUndo(name)
                }) { Text("删除", color = MaterialTheme.cocColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteName = null }) { Text("取消") }
            }
        )
    }

    // 职位选择弹窗
    editingRoleName?.let { name ->
        val currentRole = roster.find { it.name == name }?.role ?: "member"
        RoleSelectDialog(
            currentRole = currentRole,
            onSelect = { role ->
                viewModel.updateRole(name, role)
                editingRoleName = null
            },
            onDismiss = { editingRoleName = null }
        )
    }

    // 成员详情弹窗：展示距离上次参战已连续缺席的部落战场次
    detailName?.let { name ->
        MemberDetailDialog(
            name = name,
            loadAbsentCount = viewModel::getWarAbsentCount,
            onDismiss = { detailName = null }
        )
    }
}
