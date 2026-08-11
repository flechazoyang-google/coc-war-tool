package com.cocwar.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cocwar.CocWarApplication
import com.cocwar.data.migrate.DataMigrator
import com.cocwar.data.migrate.MigrationPlan
import com.cocwar.data.migrate.MigrationResult
import com.cocwar.ui.components.CocCard
import com.cocwar.ui.components.SectionTitle
import com.cocwar.ui.components.SettingsRow
import com.cocwar.ui.theme.cocColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置-数据管理页：云端同步 / 导出备份 / 导出 CSV / 从备份导入 / 数据迁移修复 / JSON 格式示例
 * （原工具页「数据管理」区块迁移）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 弹窗状态
    var showJsonFormatDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    // 待写入文件的导出 JSON / CSV（SAF 选择保存位置后写入）
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }
    // 数据迁移修复：预览计划 / 执行结果 / 执行中标记
    var migrationPlan by remember { mutableStateOf<MigrationPlan?>(null) }
    var migrationResult by remember { mutableStateOf<MigrationResult?>(null) }
    var migrationBusy by remember { mutableStateOf(false) }

    // 导出备份：SAF 选择保存位置后写入 JSON 文件
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && json != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法打开输出流")
                    }
                }.onSuccess {
                    Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导出 CSV：SAF 选择保存位置后写入 CSV 文件
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri != null && csv != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法打开输出流")
                    }
                }.onSuccess {
                    Toast.makeText(context, "CSV 已导出", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 从备份文件导入：选文件后先校验，再完整还原
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val json = context.contentResolver.openInputStream(it)
                            ?.bufferedReader()?.use { r -> r.readText() } ?: ""
                        val app = context.applicationContext as CocWarApplication
                        if (!app.repository.validateBackupJson(json)) {
                            throw IllegalStateException("所选文件不是有效的备份 JSON")
                        }
                        app.repository.restoreFromBackupJson(json)
                    }
                }.onSuccess {
                    Toast.makeText(context, "备份导入成功", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("数据管理", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))

            SectionTitle("备份与同步")
            CocCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Cloud,
                        iconColor = MaterialTheme.cocColors.roleElder,
                        title = "云端同步 (WebDAV)",
                        subtitle = "上传/下载备份到坚果云等",
                        onClick = onOpenSync
                    )
                    SettingsRow(
                        icon = Icons.Filled.SaveAlt,
                        iconColor = MaterialTheme.cocColors.accent,
                        title = "导出所有数据",
                        subtitle = "导出全量战报与名单为备份 JSON 文件",
                        onClick = {
                            scope.launch {
                                val app = context.applicationContext as CocWarApplication
                                // JSON 拼接在 IO 线程执行，避免大数据量时卡主线程
                                val json = withContext(Dispatchers.IO) {
                                    app.repository.exportAllDataJson()
                                }
                                pendingExportJson = json
                                val ts = java.text.SimpleDateFormat(
                                    "yyyyMMdd_HHmmss", java.util.Locale.US
                                ).format(java.util.Date())
                                exportLauncher.launch("coc_war_backup_$ts.json")
                            }
                        }
                    )
                    SettingsRow(
                        icon = Icons.Filled.GridOn,
                        iconColor = MaterialTheme.cocColors.star,
                        title = "导出 CSV 表格",
                        subtitle = "全部战报导出为 CSV（Excel/WPS 可直接打开）",
                        onClick = {
                            scope.launch {
                                val app = context.applicationContext as CocWarApplication
                                val csv = withContext(Dispatchers.IO) {
                                    app.repository.exportAllEventsCsv()
                                }
                                pendingExportCsv = csv
                                val ts = java.text.SimpleDateFormat(
                                    "yyyyMMdd_HHmmss", java.util.Locale.US
                                ).format(java.util.Date())
                                csvExportLauncher.launch("coc_war_events_$ts.csv")
                            }
                        }
                    )
                    SettingsRow(
                        icon = Icons.Filled.FileOpen,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "从备份导入",
                        subtitle = "选择备份 JSON 文件完整还原（会覆盖当前数据）",
                        onClick = { showRestoreConfirm = true },
                        showDivider = false
                    )
                }
            }

            SectionTitle("修复与参考")
            CocCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.SystemUpdateAlt,
                        iconColor = MaterialTheme.cocColors.danger,
                        title = "数据迁移修复",
                        subtitle = "将旧版联赛战报名称升级为新编码，迁移前自动备份",
                        onClick = {
                            if (migrationBusy) return@SettingsRow
                            scope.launch {
                                migrationBusy = true
                                try {
                                    val app = context.applicationContext as CocWarApplication
                                    val migrator = DataMigrator(app.database.warDao(), app.repository)
                                    val plan = withContext(Dispatchers.IO) {
                                        migrator.scan()
                                    }
                                    if (plan.items.isEmpty()) {
                                        Toast.makeText(context, "数据已是最新结构，无需迁移", Toast.LENGTH_SHORT).show()
                                    } else {
                                        migrationPlan = plan
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "迁移扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    migrationBusy = false
                                }
                            }
                        }
                    )
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "JSON 格式示例",
                        subtitle = "查看并复制标准战报 JSON 格式",
                        onClick = { showJsonFormatDialog = true },
                        showDivider = false
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // ── JSON 格式示例弹窗 ──
    if (showJsonFormatDialog) {
        val jsonSample = """{
  "members": [
    {
      "player_name": "陈平安",
      "total_stars": 6,
      "attacks": [
        { "attack_order": 1, "destruction_percentage": 100 },
        { "attack_order": 2, "destruction_percentage": 0 }
      ]
    }
  ]
}"""
        AlertDialog(
            onDismissRequest = { showJsonFormatDialog = false },
            title = { Text("JSON 数据格式") },
            text = {
                Column {
                    Text(jsonSample, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "说明：未进攻成员的攻击记录可省略，系统自动补占位；" +
                        "摧毁率为 0 视为未进攻；职位在「成员」页花名册中设置，无需填写 rank/role/status。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("json", jsonSample))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showJsonFormatDialog = false
                }) { Text("复制") }
            },
            confirmButton = {
                TextButton(onClick = { showJsonFormatDialog = false }) { Text("关闭") }
            }
        )
    }

    // ── 从备份导入确认 ──
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从备份导入") },
            text = {
                Text("将清空当前全部战报与名单，并用备份文件内容完整还原。\n\n确定继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    restorePicker.launch("application/json")
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            }
        )
    }

    // ── 数据迁移修复：确认 ──
    migrationPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { migrationPlan = null },
            title = { Text("数据迁移修复") },
            text = {
                Column {
                    Text(
                        "发现 ${plan.items.size} 条旧版联赛战报名称需要迁移" +
                            if (plan.overflowCount > 0) "（其中 ${plan.overflowCount} 条超出编码范围，将标记为无效名称）" else "" +
                            "。\n\n执行前将自动备份全部数据到应用备份目录，备份可用于「从备份导入」还原。\n\n预览："
                    )
                    plan.items.take(3).forEach { item ->
                        Text("${item.oldName}  →  ${item.newName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.items.size > 3) {
                        Text("…共 ${plan.items.size} 条", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    migrationPlan = null
                    scope.launch {
                        migrationBusy = true
                        try {
                            val app = context.applicationContext as CocWarApplication
                            val migrator = DataMigrator(app.database.warDao(), app.repository)
                            val backupDir = context.getExternalFilesDir("backups") ?: context.filesDir
                            val result = withContext(Dispatchers.IO) {
                                migrator.execute(backupDir)
                            }
                            migrationResult = result
                        } catch (e: Exception) {
                            Toast.makeText(context, "迁移失败：${e.message}（数据未改动）", Toast.LENGTH_LONG).show()
                        } finally {
                            migrationBusy = false
                        }
                    }
                }) { Text("确认迁移") }
            },
            dismissButton = {
                TextButton(onClick = { migrationPlan = null }) { Text("取消") }
            }
        )
    }

    // ── 数据迁移修复：结果 ──
    migrationResult?.let { result ->
        AlertDialog(
            onDismissRequest = { migrationResult = null },
            title = { Text("迁移完成") },
            text = {
                Column {
                    Text("成功迁移 ${result.migrated} 条，跳过 ${result.skipped} 条，溢出 ${result.overflow} 条。")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "迁移前备份已保存到：\n${result.backupPath}\n\n" +
                            "该备份为迁移前的完整数据，可通过「从备份导入」随时还原。"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { migrationResult = null }) { Text("知道了") }
            }
        )
    }
}
