package com.cocwar.ui.members

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cocwar.data.db.MemberRosterEntity
import com.cocwar.domain.ParsedRoster
import com.cocwar.domain.RosterDiff
import com.cocwar.domain.RosterEntry
import com.cocwar.domain.RosterTextParser
import com.cocwar.domain.computeRosterDiff
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.importflow.CopyPrompts
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel

/**
 * 更新花名册（软替换）全屏弹窗：
 * - 阶段一：使用引导（复制豆包提示词 / 粘贴剪贴板）+ 花名册文本输入；
 * - 阶段二：解析结果与差异预览（新增 / 恢复在册 / 职位变化 / 将标记离队），确认后落库。
 * 替换不影响战报数据；旧成员标记离队（可在「已离队成员」页恢复），职位以新名单为准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateRosterDialog(
    roster: List<MemberRosterEntity>,
    onReplace: (entries: List<RosterEntry>, summary: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ParsedRoster?>(null) }
    var diff by remember { mutableStateOf<RosterDiff?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }

    fun parseAndPreview() {
        val parsed = RosterTextParser.parse(input)
        if (parsed.entries.isEmpty()) {
            errorMsg = "未解析到有效成员，请检查文本格式（每行一个成员：昵称,职位）"
            return
        }
        errorMsg = null
        preview = parsed
        diff = computeRosterDiff(roster, parsed.entries)
    }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (preview == null) "更新花名册" else "更新预览") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (preview != null) {
                                preview = null
                                diff = null
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                if (preview == null) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (preview == null) "关闭" else "返回修改"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            // 委托属性无法智能转换，先取局部 val 再判空
            val currentPreview = preview
            val currentDiff = diff
            if (currentPreview == null || currentDiff == null) {
                UpdateRosterInputStage(
                    paddingValues = padding,
                    input = input,
                    onInputChange = { input = it; errorMsg = null },
                    errorMsg = errorMsg,
                    onCopyPrompt = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("花名册识别提示词", CopyPrompts.ROSTER_PROMPT))
                        Toast.makeText(context, "提示词已复制，请连同截图发给豆包", Toast.LENGTH_SHORT).show()
                    },
                    onPasteClipboard = {
                        val text = clipboardManager.getText()?.text
                        if (text.isNullOrBlank()) {
                            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                        } else {
                            input = if (input.isBlank()) text else input + "\n" + text
                            errorMsg = null
                        }
                    },
                    onShowPrompt = { showPromptDialog = true },
                    onParse = ::parseAndPreview
                )
            } else {
                UpdateRosterPreviewStage(
                    paddingValues = padding,
                    preview = currentPreview,
                    diff = currentDiff,
                    onBack = {
                        preview = null
                        diff = null
                    },
                    onConfirm = {
                        val summary = buildUpdateSummary(currentDiff)
                        onReplace(currentPreview.entries, summary)
                    }
                )
            }
        }
    }

    if (showPromptDialog) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("豆包识别提示词") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, CocShape.field)
                        .padding(12.dp)
                ) {
                    Text(
                        CopyPrompts.ROSTER_PROMPT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("花名册识别提示词", CopyPrompts.ROSTER_PROMPT))
                        Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, "复制提示词", tint = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showPromptDialog = false }) { Text("关闭") }
                }
            }
        )
    }
}

@Composable
private fun UpdateRosterInputStage(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    input: String,
    onInputChange: (String) -> Unit,
    errorMsg: String?,
    onCopyPrompt: () -> Unit,
    onPasteClipboard: () -> Unit,
    onShowPrompt: () -> Unit,
    onParse: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SectionTitle("使用引导")
        CocCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1. 用悬浮球截取游戏内「部落」页的成员列表（长名单可分多屏）\n" +
                        "2. 打开豆包 App，把截图和提示词一起发送，让它提取名单\n" +
                        "3. 复制豆包输出的名单，回到这里粘贴",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCopyPrompt,
                        modifier = Modifier.weight(1f),
                        shape = CocShape.field
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制提示词")
                    }
                    OutlinedButton(
                        onClick = onPasteClipboard,
                        modifier = Modifier.weight(1f),
                        shape = CocShape.field
                    ) {
                        Icon(Icons.Filled.ContentPaste, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("粘贴剪贴板")
                    }
                }
                TextButton(onClick = onShowPrompt, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("查看完整提示词", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        SectionTitle("花名册文本")
        CocCard {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("陈平安,首领\n宁姚,副首领\n裴钱,长老\n...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    singleLine = false,
                    shape = CocShape.field,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.cocColors.hairline,
                        cursorColor = MaterialTheme.cocColors.accent
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "每行一个成员：昵称,职位。职位为 首领/副首领/长老/成员，缺省按成员；多段粘贴会自动合并去重。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        errorMsg?.let {
            CocCard(Modifier.fillMaxWidth()) {
                Text(
                    it,
                    color = MaterialTheme.cocColors.danger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Button(
            onClick = onParse,
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CocShape.field,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("解析并预览", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun UpdateRosterPreviewStage(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    preview: ParsedRoster,
    diff: RosterDiff,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SectionTitle("更新摘要")
        CocCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "新名单共 ${preview.entries.size} 人",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "新增 ${diff.added.size} · 恢复在册 ${diff.restored.size} · 职位变化 ${diff.roleChanged.size} · 标记离队 ${diff.departing.size} · 不变 ${diff.unchangedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "不在新名单的在册成员将标记为已离队（可在「已离队成员」页恢复），职位以新名单为准；战报数据不受影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (preview.warnings.isNotEmpty()) {
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    preview.warnings.forEach {
                        Text(
                            it,
                            color = MaterialTheme.cocColors.danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (diff.added.isNotEmpty()) {
            SectionTitle("新增成员（${diff.added.size}）")
            CocCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    diff.added.forEach { entry ->
                        RosterRow(name = entry.name, role = entry.role)
                    }
                }
            }
        }

        if (diff.restored.isNotEmpty()) {
            SectionTitle("恢复在册（${diff.restored.size}）")
            CocCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    diff.restored.forEach { entry ->
                        RosterRow(name = entry.name, role = entry.role)
                    }
                }
            }
        }

        if (diff.roleChanged.isNotEmpty()) {
            SectionTitle("职位变化（${diff.roleChanged.size}）")
            CocCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    diff.roleChanged.forEach { change ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(change.name, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            RoleText(change.oldRole)
                            Text(
                                " → ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RoleText(change.newRole)
                        }
                    }
                }
            }
        }

        if (diff.departing.isNotEmpty()) {
            SectionTitle("将标记离队（${diff.departing.size}）")
            CocCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    diff.departing.forEach { member ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                member.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.cocColors.danger
                            )
                            Spacer(Modifier.weight(1f))
                            RoleText(member.role)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = CocShape.field
            ) {
                Text("返回修改")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp),
                shape = CocShape.field,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("确认更新", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RosterRow(name: String, role: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        RoleText(role)
    }
}

@Composable
private fun RoleText(role: String) {
    Text(
        roleLabel(role),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = roleColor(role)
    )
}

/** Snackbar 摘要：省略零值项；全部无变化时提示无变化。 */
private fun buildUpdateSummary(diff: RosterDiff): String {
    val parts = mutableListOf<String>()
    if (diff.added.isNotEmpty()) parts.add("新增 ${diff.added.size}")
    if (diff.restored.isNotEmpty()) parts.add("恢复在册 ${diff.restored.size}")
    if (diff.roleChanged.isNotEmpty()) parts.add("职位变化 ${diff.roleChanged.size}")
    if (diff.departing.isNotEmpty()) parts.add("标记离队 ${diff.departing.size}")
    return if (parts.isEmpty()) "花名册无变化" else "已更新花名册：" + parts.joinToString(" · ")
}
