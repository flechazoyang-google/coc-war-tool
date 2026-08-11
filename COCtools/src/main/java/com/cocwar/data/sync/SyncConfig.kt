package com.cocwar.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV 同步配置存储。
 *
 * URL / 用户名存放在普通 SharedPreferences（非敏感）；密码经 [SecurePrefs]
 * （AndroidKeyStore + AES/GCM）加密后落盘，避免明文被 Android Auto Backup 上传云端。
 */
class SyncConfig(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_webdav_prefs", Context.MODE_PRIVATE)

    /** 密码密文存储（普通 prefs，值为 [SecurePrefs] 的 AES/GCM 密文）。 */
    private val securePrefs: SharedPreferences =
        appContext.getSharedPreferences("coc_webdav_secure_prefs", Context.MODE_PRIVATE)

    /** 加密存储是否可用（false = 密码无法安全保存，UI 应提示用户）。 */
    val isSecureStorageAvailable: Boolean
        get() = SecurePrefs.isKeystoreAvailable()

    init {
        // 一次性迁移：老版本密码明文存在普通 prefs，读到后写入加密存储并删除明文键
        migrateLegacyPassword()
    }

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var password: String
        get() {
            val cipher = securePrefs.getString(KEY_PASS, null) ?: return ""
            // 旧版 security-crypto 密文或损坏数据无法解密 → 返回空，用户重输后覆盖为新格式
            return SecurePrefs.decrypt(cipher) ?: ""
        }
        set(value) {
            if (value.isEmpty()) {
                securePrefs.edit().remove(KEY_PASS).apply()
                return
            }
            // AndroidKeyStore 不可用或加密失败时拒绝保存，避免明文写入普通 prefs
            val encrypted = SecurePrefs.encrypt(value) ?: return
            securePrefs.edit().putString(KEY_PASS, encrypted).apply()
        }

    /** 是否已配置（URL 和用户名均非空）。 */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()

    // === 同步指纹与归档记录（B3，非敏感，普通 prefs） ===

    /** 上次同步完成时的本地指纹（null = 尚未同步过）。 */
    var lastLocalFingerprint: String?
        get() = prefs.getString(KEY_LAST_LOCAL_FP, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_LAST_LOCAL_FP).apply()
            else prefs.edit().putString(KEY_LAST_LOCAL_FP, value).apply()
        }

    /** 上次同步完成时看到的远端指纹（null = 尚未同步过）。 */
    var lastRemoteFingerprint: String?
        get() = prefs.getString(KEY_LAST_REMOTE_FP, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_LAST_REMOTE_FP).apply()
            else prefs.edit().putString(KEY_LAST_REMOTE_FP, value).apply()
        }

    /** 已归档文件名（按时间顺序）。 */
    fun archivedNames(): List<String> =
        (prefs.getString(KEY_ARCHIVES, "") ?: "").split(",").filter { it.isNotBlank() }

    /**
     * 记录一个归档名并裁剪到上限（RULES §6：最多保留 [MAX_ARCHIVES] 份）。
     * @return 被裁剪掉的归档名（调用方负责从云端删除）
     */
    fun recordArchive(name: String): List<String> {
        val all = archivedNames() + name
        val kept = all.takeLast(MAX_ARCHIVES)
        prefs.edit().putString(KEY_ARCHIVES, kept.joinToString(",")).apply()
        return all.drop(kept.size)
    }

    private fun migrateLegacyPassword() {
        // AndroidKeyStore 不可用时不做迁移（迁移会把明文留在普通 prefs 中）
        if (!SecurePrefs.isKeystoreAvailable()) return
        // 加密存储已有值则不迁移
        if (securePrefs.contains(KEY_PASS)) return
        val legacy = prefs.getString(KEY_PASS, null) ?: return
        if (legacy.isNotEmpty()) {
            val encrypted = SecurePrefs.encrypt(legacy) ?: return
            securePrefs.edit().putString(KEY_PASS, encrypted).apply()
        }
        prefs.edit().remove(KEY_PASS).apply()
    }

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"
        private const val KEY_LAST_LOCAL_FP = "last_local_fingerprint"
        private const val KEY_LAST_REMOTE_FP = "last_remote_fingerprint"
        private const val KEY_ARCHIVES = "archived_names"

        /** 云端归档保留上限（RULES §6）。 */
        const val MAX_ARCHIVES = 10
    }
}
