package com.cocwar.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocwar.di.warViewModel
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocIconButton
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.EmptyState
import com.cocwar.ui.components.ScreenHeader
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel
import kotlinx.coroutines.launch

@Composable
fun MemberManageScreen(onBack: () -> Unit) {
    val viewModel: MemberManageViewModel = warViewModel { MemberManageViewModel(it) }
    val roster by viewModel.roster.collectAsStateWithLifecycle()
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var editingRoleName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 删除成员：立即落库删除 → Snackbar 提供撤销（重新加回名单），防误触
    fun removeNameWithUndo(name: String) {
        scope.launch {
            viewModel.removeName(name)
            val result = snackbarHostState.showSnackbar(
                message = "已删除成员「$name」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.addNames(listOf(name))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "成员",
                overline = "花名册",
                subtitle = if (roster.isEmpty()) "尚无成员" else "共 ${roster.size} 人",
                actions = {
                    CocIconButton(
                        icon = if (showImport) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "批量导入",
                        onClick = { showImport = !showImport },
                        filled = showImport
                    )
                }
            )
    
            if (showImport) {
                CocCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "批量导入",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "一行一个名字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it },
                            placeholder = { Text("陈平安\n张三\n李四\n...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            singleLine = false,
                            shape = CocShape.field,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                                cursorColor = MaterialTheme.cocColors.accent
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val names = importText.lines().map { it.trim() }.filter { it.isNotBlank() }
                                if (names.isNotEmpty()) {
                                    viewModel.addNames(names)
                                    importText = ""
                                    showImport = false
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = CocShape.field,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("导入", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
    
            if (roster.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "名单为空",
                        body = "点击右上角 + 批量导入成员\n导入战报时也会自动收录新成员"
                    )
                }
            } else {
                // 无卡片名册：序号 + 名字 + 职位（点击设置） + 删除，发丝线分隔
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(roster, key = { _, entry -> entry.name }) { index, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "%02d".format(index + 1),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            // 职位徽标：非默认职位用「色点 + 文字」，默认成员弱化为纯文字；点击弹出职位选择
                            if (entry.role.equals("member", ignoreCase = true)) {
                                Text(
                                    roleLabel(entry.role),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clickable { editingRoleName = entry.name }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .clickable { editingRoleName = entry.name }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(roleColor(entry.role), CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        roleLabel(entry.role),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = roleColor(entry.role)
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { removeNameWithUndo(entry.name) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (index < roster.lastIndex) {
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 职位选择弹窗（首领/副首领/长老/成员，默认成员）
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
}

/** 职位选择对话框：单选首领/副首领/长老/成员。 */
@Composable
private fun RoleSelectDialog(
    currentRole: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val roles = listOf(
        "leader" to "首领",
        "coLeader" to "副首领",
        "elder" to "长老",
        "member" to "成员"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置职位") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                roles.forEach { (role, label) ->
                    val selected = role == currentRole
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(role) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(roleColor(role), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.cocColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
