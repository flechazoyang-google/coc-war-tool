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
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✓ 下载并恢复成功！")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isWorking = false, statusMessage = "✗ 恢复失败：${e.message}")
            }
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

