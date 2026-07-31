package com.cocwar.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.repository.WarRepository
import com.cocwar.data.sync.SyncConfig
import com.cocwar.data.sync.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConfigured: Boolean = false,
    val statusMessage: String? = null,
    val isWorking: Boolean = false
)

class SyncViewModel(
    private val repo: WarRepository,
    private val config: SyncConfig
) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    init {
        loadConfig()
    }

    private fun loadConfig() {
        _state.value = _state.value.copy(
            serverUrl = config.serverUrl,
            username = config.username,
            password = config.password,
            isConfigured = config.isConfigured
        )
    }

    fun onUrlChange(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun onUsernameChange(user: String) {
        _state.value = _state.value.copy(username = user)
    }

    fun onPasswordChange(pass: String) {
        _state.value = _state.value.copy(password = pass)
    }

    /** 保存配置 */
    fun saveConfig() {
        val s = _state.value
        config.serverUrl = s.serverUrl.trimEnd('/')
        config.username = s.username.trim()
        config.password = s.password
        loadConfig()
        setStatus("配置已保存")
    }

    /** 测试 WebDAV 连接 */
    fun testConnection() {
        saveConfig()
        _state.value = _state.value.copy(isWorking = true, statusMessage = "正在测试连接…")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                buildClient()?.testConnection()
            }
            _state.value = _state.value.copy(isWorking = false)
            result?.onSuccess {
                _state.value = _state.value.copy(statusMessage = "✓ 连接成功！")
            }?.onFailure { e ->
                _state.value = _state.value.copy(statusMessage = "✗ 连接失败：${e.message}")
            }
            if (result == null) {
                _state.value = _state.value.copy(statusMessage = "✗ 请先完成配置")
            }
        }
    }

    /** 上传备份到 WebDAV */
    fun uploadBackup() {
        saveConfig()
        _state.value = _state.value.copy(isWorking = true, statusMessage = "正在导出并上传…")
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { repo.exportAllDataJson() }
                val client = buildClient() ?: run {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 请先完成配置")
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    client.upload(json)
                }
                _state.value = _state.value.copy(isWorking = false)
                result.onSuccess {
                    _state.value = _state.value.copy(statusMessage = "✓ 上传成功！")
                }.onFailure { e ->
                    _state.value = _state.value.copy(statusMessage = "✗ 上传失败：${e.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 导出失败：${e.message}")
            }
        }
    }

    /** 从 WebDAV 下载备份并完整还原（先校验内容，再清空本地写入）。 */
    fun downloadAndRestore() {
        saveConfig()
        _state.value = _state.value.copy(isWorking = true, statusMessage = "正在下载…")
        viewModelScope.launch {
            try {
                val client = buildClient() ?: run {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 请先完成配置")
                    return@launch
                }
                val json = withContext(Dispatchers.IO) {
                    client.download().getOrThrow()
                }
                // 先校验备份内容，非法内容绝不执行清空/写入，避免“假成功”
                val validated = withContext(Dispatchers.IO) {
                    validateBackup(json)
                }
                if (validated == null) {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 远程内容不是有效备份，未做任何修改")
                    return@launch
                }
                _state.value = _state.value.copy(statusMessage = "正在导入数据…")
                // 完整还原：先全部解析成功，再清空本地写入，避免半途失败导致数据清空却未还原
                withContext(Dispatchers.IO) {
                    restoreFromJson(json)
                }
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✓ 下载并恢复成功！")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 恢复失败：${e.message}")
            }
        }
    }

    /** 校验备份 JSON 是否为合法的备份结构；非法返回 null。 */
    private fun validateBackup(json: String): Boolean? {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return null
        return try {
            val root = com.google.gson.Gson().fromJson(trimmed, BackupData::class.java)
            if (root == null || (root.events == null && root.roster == null)) null
            else true
        } catch (e: Exception) {
            null
        }
    }

    /** 解析备份 JSON 并完整还原（先全部解析成功，再清空本地事件/成员后写入，含花名册）。 */
    private suspend fun restoreFromJson(json: String) {
        val gson = com.google.gson.Gson()
        val root = gson.fromJson(json, BackupData::class.java) ?: return

        // 第一步：解析全部事件并保留原始名称，任一事件解析失败则整体放弃（不碰本地数据）
        val parsedEvents = mutableListOf<com.cocwar.data.parser.WarJsonParser.ParsedEvent>()
        root.events?.forEach { eventDto ->
            val parsed = com.cocwar.data.parser.WarJsonParser.parse(
                eventDto.toWarJson(),
                createdAt = eventDto.created_at?.takeIf { it > 0 } ?: System.currentTimeMillis(),
                eventType = eventDto.event_type ?: com.cocwar.data.model.EVENT_TYPE_WAR,
                eventRound = eventDto.event_round ?: 0
            )
            if (parsed is com.cocwar.data.parser.WarJsonParser.ParseResult.Success) {
                // 用备份里的原始名称覆盖，避免被重置为空
                val src = parsed.data
                parsedEvents += src.copy(
                    event = src.event.copy(eventName = eventDto.event_name ?: src.event.eventName)
                )
            } else {
                throw Exception("备份中某场战报数据损坏，已中止还原（本地数据未改动）")
            }
        }

        // 第二步：全部解析成功后才清空本地并写入
        repo.clearAllEvents()

        // 恢复花名册（addToRoster 内部去重/忽略已存在）
        root.roster?.takeIf { it.isNotEmpty() }?.let { repo.addToRoster(it) }

        parsedEvents.forEach { restored ->
            repo.importEvent(restored)
        }
    }

    private fun buildClient(): WebDavClient? {
        val s = _state.value
        val url = s.serverUrl.trimEnd('/')
        if (url.isBlank() || s.username.isBlank()) return null
        return WebDavClient(url, s.username, s.password)
    }

    private fun setStatus(msg: String) {
        _state.value = _state.value.copy(statusMessage = msg)
    }
}

/** 备份 JSON 的顶层结构 */
private data class BackupData(
    val roster: List<String>? = null,
    val events: List<BackupEvent>? = null
)

private data class BackupEvent(
    val event_name: String? = null,
    val event_type: String? = null,
    val event_round: Int? = 0,
    val clan_total_stars: Int? = 0,
    val clan_total_destruction: String? = "0%",
    val created_at: Long? = 0,
    val members: List<BackupMember>? = null
) {
    /** 将备份格式转换为导入 JSON 格式 */
    fun toWarJson(): String {
        val sb = StringBuilder()
        sb.append("{\n  \"members\": [\n")
        members?.forEachIndexed { i, m ->
            sb.append("    {\n")
            sb.append("      \"rank\": ${m.rank},\n")
            sb.append("      \"player_name\": \"${escape(m.player_name ?: "")}\",\n")
            sb.append("      \"role\": \"${escape(m.role ?: "member")}\",\n")
            sb.append("      \"total_stars\": ${m.total_stars ?: 0},\n")
            sb.append("      \"attacks\": [\n")
            m.attacks?.forEachIndexed { j, a ->
                sb.append("        {\n")
                sb.append("          \"attack_order\": ${a.attack_order ?: 0},\n")
                sb.append("          \"status\": \"${escape(a.status ?: "unused")}\",\n")
                sb.append("          \"destruction_percentage\": ${a.destruction_percentage ?: 0}\n")
                sb.append("        }${if (j < (m.attacks?.lastIndex ?: 0)) "," else ""}\n")
            }
            sb.append("      ]\n")
            sb.append("    }${if (i < (members?.lastIndex ?: 0)) "," else ""}\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    private fun escape(s: String): String = buildString(s.length + 8) {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < '\u0020') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }
}

private data class BackupMember(
    val rank: Int? = 0,
    val player_name: String? = null,
    val role: String? = null,
    val total_stars: Int? = 0,
    val attacks: List<BackupAttack>? = null
)

private data class BackupAttack(
    val attack_order: Int? = 0,
    val status: String? = null,
    val destruction_percentage: Int? = 0
)
