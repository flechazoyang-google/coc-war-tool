package com.cocwar.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cocwar.data.repository.WarRepository
import com.cocwar.data.sync.SyncConfig
import com.cocwar.data.sync.SyncDecider
import com.cocwar.data.sync.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 同步冲突：两端都有修改，待用户决策（RULES §6）。 */
data class SyncConflict(
    val localJson: String,
    val remoteJson: String
)

data class SyncUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isConfigured: Boolean = false,
    val statusMessage: String? = null,
    val isWorking: Boolean = false,
    val conflict: SyncConflict? = null
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

    // === B3 双向同步 ===

    /**
     * 双向同步（RULES §6）：指纹判定 + 决策表。
     * 冲突时进入待决策状态（不修改任何一端数据），由用户三选一。
     */
    fun syncNow() {
        saveConfig()
        _state.value = _state.value.copy(isWorking = true, statusMessage = "正在同步…")
        viewModelScope.launch {
            try {
                val client = buildClient() ?: run {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 请先完成配置")
                    return@launch
                }

                // 本地侧：指纹 + 是否有数据
                val localHasData = withContext(Dispatchers.IO) { repo.hasAnyData() }
                val localFp = if (localHasData) withContext(Dispatchers.IO) { repo.dataFingerprint() } else null

                // 远端侧：三态探测（UNKNOWN 时保守中止，防止误覆盖有数据的远端）
                val remoteState = withContext(Dispatchers.IO) { client.probe() }
                val remoteJson = when (remoteState) {
                    WebDavClient.RemoteState.EXISTS ->
                        withContext(Dispatchers.IO) { client.download().getOrThrow() }
                    WebDavClient.RemoteState.MISSING -> null
                    WebDavClient.RemoteState.UNKNOWN -> {
                        setDone("✗ 无法确认云端状态（连接或认证异常），已中止同步，未做任何修改")
                        return@launch
                    }
                }
                val remoteFp = remoteJson?.let { sha256(it) }

                val action = SyncDecider.decide(
                    localFp = localFp,
                    remoteFp = remoteFp,
                    lastLocalFp = config.lastLocalFingerprint,
                    lastRemoteFp = config.lastRemoteFingerprint
                )

                when (action) {
                    SyncDecider.SyncAction.UP_TO_DATE -> {
                        updateFingerprints(localFp, remoteFp)
                        setDone("✓ 两端已是最新，无需同步")
                    }
                    SyncDecider.SyncAction.PUSH_LOCAL -> {
                        // 覆盖远端前先归档远端旧版（RULES §6：被覆盖方自动归档，数据不丢）
                        if (remoteJson != null) archiveRemoteJson(client, remoteJson)
                        val json = withContext(Dispatchers.IO) { repo.exportAllDataJson() }
                        withContext(Dispatchers.IO) { client.upload(json) }.getOrThrow()
                        updateFingerprints(localFp, localFp)
                        setDone("✓ 已上传本地数据到云端")
                    }
                    SyncDecider.SyncAction.PULL_REMOTE -> {
                        val json = remoteJson!!
                        val valid = withContext(Dispatchers.IO) { repo.validateBackupJson(json) }
                        if (!valid) {
                            setDone("✗ 远程内容不是有效备份，未做任何修改")
                            return@launch
                        }
                        // 覆盖本地前先归档本地旧版（RULES §6：被覆盖方自动归档）
                        val localJson = withContext(Dispatchers.IO) { repo.exportAllDataJson() }
                        val localPath = withContext(Dispatchers.IO) { repo.saveLocalSyncBackup(localJson) }
                        withContext(Dispatchers.IO) { repo.restoreFromBackupJson(json) }
                        updateFingerprints(remoteFp, remoteFp)
                        setDone("✓ 已下载云端数据恢复本地（本地旧版已归档到 $localPath）")
                    }
                    SyncDecider.SyncAction.CONFLICT -> {
                        // 不修改任何一端，等待用户决策
                        _state.value = _state.value.copy(
                            isWorking = false,
                            conflict = SyncConflict(
                                localJson = withContext(Dispatchers.IO) { repo.exportAllDataJson() },
                                remoteJson = remoteJson!!
                            ),
                            statusMessage = "⚠ 检测到冲突：本地与云端都有修改"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 同步失败：${e.message}")
            }
        }
    }

    /** 冲突：保留本地数据，覆盖云端（远端旧版先归档）。 */
    fun resolveConflictKeepLocal() {
        val conflict = _state.value.conflict ?: return
        _state.value = _state.value.copy(conflict = null, isWorking = true, statusMessage = "正在保留本地并覆盖云端…")
        viewModelScope.launch {
            try {
                val client = buildClient() ?: run {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 请先完成配置")
                    return@launch
                }
                archiveRemoteJson(client, conflict.remoteJson)
                withContext(Dispatchers.IO) { client.upload(conflict.localJson) }.getOrThrow()
                val fp = withContext(Dispatchers.IO) { repo.dataFingerprint() }
                updateFingerprints(fp, fp)
                setDone("✓ 已保留本地数据（云端旧版已归档）")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 操作失败：${e.message}")
            }
        }
    }

    /** 冲突：采用云端数据，覆盖本地（本地旧版先归档到应用私有目录）。 */
    fun resolveConflictKeepRemote() {
        val conflict = _state.value.conflict ?: return
        _state.value = _state.value.copy(conflict = null, isWorking = true, statusMessage = "正在采用云端数据…")
        viewModelScope.launch {
            try {
                val localPath = withContext(Dispatchers.IO) {
                    repo.saveLocalSyncBackup(conflict.localJson)
                }
                val valid = withContext(Dispatchers.IO) {
                    repo.validateBackupJson(conflict.remoteJson)
                }
                if (!valid) {
                    setDone("✗ 云端数据无效，未做任何修改（本地已归档到 $localPath）")
                    return@launch
                }
                withContext(Dispatchers.IO) { repo.restoreFromBackupJson(conflict.remoteJson) }
                val fp = withContext(Dispatchers.IO) { repo.dataFingerprint() }
                updateFingerprints(fp, fp)
                setDone("✓ 已采用云端数据（本地旧版已归档到 $localPath）")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 操作失败：${e.message}")
            }
        }
    }

    /** 冲突：取消本次同步（不改动任何一端，下次同步会再次提示）。 */
    fun dismissConflict() {
        _state.value = _state.value.copy(conflict = null)
        setStatus("已取消同步（数据未改动）")
    }

    /** 上传备份到 WebDAV（强制覆盖，完成后更新同步指纹）。 */
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
                    val fp = withContext(Dispatchers.IO) { repo.dataFingerprint() }
                    updateFingerprints(fp, fp)
                    _state.value = _state.value.copy(statusMessage = "✓ 上传成功！")
                }.onFailure { e ->
                    _state.value = _state.value.copy(statusMessage = "✗ 上传失败：${e.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 导出失败：${e.message}")
            }
        }
    }

    /** 从 WebDAV 下载备份并完整还原（先校验内容，再清空本地写入；完成后更新同步指纹）。 */
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
                val valid = withContext(Dispatchers.IO) {
                    repo.validateBackupJson(json)
                }
                if (!valid) {
                    _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 远程内容不是有效备份，未做任何修改")
                    return@launch
                }
                _state.value = _state.value.copy(statusMessage = "正在导入数据…")
                // 完整还原：先全部解析成功，再清空本地写入，避免半途失败导致数据清空却未还原
                withContext(Dispatchers.IO) {
                    repo.restoreFromBackupJson(json)
                }
                val fp = withContext(Dispatchers.IO) { repo.dataFingerprint() }
                updateFingerprints(fp, fp)
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✓ 下载并恢复成功！")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 恢复失败：${e.message}")
            }
        }
    }

    /** 归档远端旧版到 WebDAV archives/ 并裁剪超限归档（RULES §6）。 */
    private fun archiveRemoteJson(client: WebDavClient, json: String) {
        val name = "coc_war_backup_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
                .format(java.util.Date()) + ".json"
        client.upload(json, client.archiveUrl(name)).getOrThrow()
        val evicted = config.recordArchive(name)
        evicted.forEach { old ->
            runCatching { client.delete(client.archiveUrl(old)) }
        }
    }

    /** 同步指纹更新（RULES §6：失败不更新，成功才更新）。 */
    private fun updateFingerprints(localFp: String?, remoteFp: String?) {
        config.lastLocalFingerprint = localFp
        config.lastRemoteFingerprint = remoteFp
    }

    private fun sha256(s: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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

    /** 结束工作态并设置状态消息。 */
    private fun setDone(msg: String) {
        _state.value = _state.value.copy(isWorking = false, statusMessage = msg)
    }
}
