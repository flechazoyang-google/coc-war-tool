package com.cocwar.ui.members

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Checkbox
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
import com.cocwar.data.ocr.RosterOcrPrompts
import com.cocwar.domain.ROSTER_LIMIT
import com.cocwar.domain.RosterDiff
import com.cocwar.domain.RosterEntry
import com.cocwar.domain.checkRosterLimit
import com.cocwar.domain.RosterTextParser
import com.cocwar.domain.computeRosterDiff
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.CocShape
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.theme.cocColors
import com.cocwar.ui.theme.roleColor
import com.cocwar.ui.util.roleLabel

/**
 * 成员更新（硬替换）全屏弹窗：
 * - 阶段一：使用引导（复制识别提示词 / 粘贴结果）+ 花名册文本输入；
 * - 阶段二：差异预览——**退出**与**新增**逐人勾选，确认后一次性替换花名册。
 *
 * 与「导入新成员」的区别：这里是**替换**，不在新名单的成员会被真正删除（旧版是标记离队）。
 * 替换不影响任何战报数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RosterUpdateDialog(
    roster: List<MemberRosterEntity>,
    onReplace: (entries: List<RosterEntry>, summary: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    // 提示词注入当前花名册（含易混名提醒），名单变化时自动重建
    val prompt by remember(roster) {
        mutableStateOf(RosterOcrPrompts.build(roster.map { it.name }))
    }
    var input by remember { mutableStateOf("") }
    var previewEntries by remember { mutableStateOf<List<RosterEntry>?>(null) }
    var previewWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var diff by remember { mutableStateOf<RosterDiff?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }

    fun parseAndPreview() {
        val parsed = RosterTextParser.parse(input)
        if (parsed.entries.isEmpty()) {
            val ignored = if (parsed.errors.isNotEmpty()) "（已自动忽略 ${parsed.errors.size} 行格式异常）" else ""
            errorMsg = "未解析到有效成员，请检查文本格式（每行一个成员：名字,职位）$ignored"
            return
        }
        errorMsg = null
        previewEntries = parsed.entries
        previewWarnings = parsed.warnings
        previewErrors = parsed.errors
        diff = computeRosterDiff(roster, parsed.entries)
    }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (previewEntries == null) "成员更新" else "更新预览") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (previewEntries != null) {
                                previewEntries = null
                                diff = null
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                if (previewEntries == null) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (previewEntries == null) "关闭" else "返回修改"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            val currentEntries = previewEntries
            val currentDiff = diff
            if (currentEntries == null || currentDiff == null) {
                RosterUpdateInputStage(
                    paddingValues = padding,
                    input = input,
                    onInputChange = { input = it; errorMsg = null },
                    errorMsg = errorMsg,
                    onCopyPrompt = {
                        copyToClipboard(context, prompt, "提示词已复制，请连同截图发给豆包")
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
                RosterUpdatePreviewStage(
                    paddingValues = padding,
                    entries = currentEntries,
                    warnings = previewWarnings,
                    errors = previewErrors,
                    diff = currentDiff,
                    onBack = {
                        previewEntries = null
                        diff = null
                    },
                    onConfirm = { finalEntries, summary -> onReplace(finalEntries, summary) }
                )
            }
        }
    }

    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("成员识别提示词") },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, CocShape.field)
                        .padding(12.dp)
                ) {
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { copyToClipboard(context, prompt, "提示词已复制") }) {
                        Icon(Icons.Filled.ContentCopy, "复制提示词", tint = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showPromptDialog = false }) { Text("关闭") }
                }
            }
        )
    }
}

private fun copyToClipboard(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("成员识别提示词", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

@Composable
private fun RosterUpdateInputStage(
    paddingValues: PaddingValues,
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
                    "1. 用悬浮球截取游戏内「部落 → 成员」页（长名单可分多屏滚动截取）\n" +
                        "2. 打开豆包等 AI 软件，把截图和提示词一起发送，让它提取名单\n" +
                        "3. 复制输出的名单，回到这里粘贴",
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
                        Text("粘贴结果")
                    }
                }
                TextButton(onClick = onShowPrompt, contentPadding = PaddingValues(0.dp)) {
                    Text("查看完整提示词", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        SectionTitle("识别结果")
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
                    "每行一个成员：名字,职位。职位为 首领/副首领/长老/成员，缺省按成员；多段粘贴会自动合并去重。表头行（名字,职位）自动忽略；若某行被混入解说文字导致格式异常，会在预览中以「已忽略」标出，可手动修正后重新解析。",
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

/**
 * 差异预览与确认：
 * - 退出成员默认勾选（勾选 = 确认删除），取消勾选即保留；
 * - 新增成员默认勾选（勾选 = 确认加入），取消勾选即不加入；
 * - 职位变化只展示，一律以新名单为准；
 * - 更新后总人数超过 [ROSTER_LIMIT] 时禁止确认。
 */
@Composable
private fun RosterUpdatePreviewStage(
    paddingValues: PaddingValues,
    entries: List<RosterEntry>,
    warnings: List<String>,
    errors: List<String>,
    diff: RosterDiff,
    onBack: () -> Unit,
    onConfirm: (entries: List<RosterEntry>, summary: String) -> Unit
) {
    // 取消勾选的新增成员（不加入）；其余一律按新名单写入
    var excludedAdded by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 取消勾选的退出成员（保留在花名册）；默认全部勾选 = 确认删除
    var keptDeparting by remember { mutableStateOf<Set<String>>(emptySet()) }

    val finalEntries = remember(entries, diff, excludedAdded, keptDeparting) {
        val kept = diff.departing
            .filter { it.name in keptDeparting }
            .map { RosterEntry(it.name, it.role) }
        entries.filter { it.name !in excludedAdded } + kept
    }
    val limitError = checkRosterLimit(finalEntries.size)
    val summary = buildUpdateSummary(
        diff = diff,
        deletedCount = diff.departing.count { it.name !in keptDeparting },
        addedCount = diff.added.count { it.name !in excludedAdded }
    )

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
                    "识别到 ${entries.size} 人 · 更新后 ${finalEntries.size} / $ROSTER_LIMIT 人",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "新增 ${diff.added.size} · 退出 ${diff.departing.size} · 职位变化 ${diff.roleChanged.size} · 不变 ${diff.unchangedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "退出成员确认后将从花名册删除（战报数据不受影响）；取消勾选可保留，职位以新名单为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (limitError != null) {
            CocCard(Modifier.fillMaxWidth()) {
                Text(
                    limitError,
                    color = MaterialTheme.cocColors.danger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        if (warnings.isNotEmpty()) {
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    warnings.forEach {
                        Text(
                            it,
                            color = MaterialTheme.cocColors.danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            CocCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "以下 ${errors.size} 行无法解析，已忽略（不会入库）：",
                        color = MaterialTheme.cocColors.danger,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    errors.forEach {
                        Text(
                            it,
                            color = MaterialTheme.cocColors.danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "请手动修正粘贴文本中这些行后，返回重新解析。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (diff.departing.isNotEmpty()) {
            SectionTitle("退出成员（${diff.departing.size}）· 勾选表示删除")
            CocCard {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    diff.departing.forEach { member ->
                        CheckableRosterRow(
                            name = member.name,
                            role = member.role,
                            checked = member.name !in keptDeparting,
                            onCheckedChange = { checked ->
                                keptDeparting = if (checked) {
                                    keptDeparting - member.name
                                } else {
                                    keptDeparting + member.name
                                }
                            }
                        )
                    }
                }
            }
        }

        if (diff.added.isNotEmpty()) {
            SectionTitle("新增成员（${diff.added.size}）· 勾选表示加入")
            CocCard {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    diff.added.forEach { entry ->
                        CheckableRosterRow(
                            name = entry.name,
                            role = entry.role,
                            checked = entry.name !in excludedAdded,
                            onCheckedChange = { checked ->
                                excludedAdded = if (checked) {
                                    excludedAdded - entry.name
                                } else {
                                    excludedAdded + entry.name
                                }
                            }
                        )
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
                                .padding(vertical = 8.dp),
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
                onClick = { onConfirm(finalEntries, summary) },
                enabled = limitError == null && finalEntries.isNotEmpty(),
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
private fun CheckableRosterRow(
    name: String,
    role: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
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

/** Snackbar 摘要：按本次勾选后的真实增删数汇报，省略零值项。 */
private fun buildUpdateSummary(
    diff: RosterDiff,
    deletedCount: Int,
    addedCount: Int
): String {
    val parts = mutableListOf<String>()
    if (addedCount > 0) parts.add("新增 $addedCount")
    if (deletedCount > 0) parts.add("移出 $deletedCount")
    if (diff.roleChanged.isNotEmpty()) parts.add("职位变化 ${diff.roleChanged.size}")
    return if (parts.isEmpty()) "花名册无变化" else "已更新花名册：" + parts.joinToString(" · ")
}
