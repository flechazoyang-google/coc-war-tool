package com.cocwar.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * WebDAV 同步配置存储。
 *
 * URL / 用户名存放在普通 SharedPreferences（非敏感）；密码使用
 * EncryptedSharedPreferences（security-crypto）加密落盘，避免明文
 * 被 Android Auto Backup 上传云端。
 */
class SyncConfig(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("coc_webdav_prefs", Context.MODE_PRIVATE)

    /** 密码专用加密存储；初始化失败时降级为普通存储（不崩溃，仅弱化加密）。 */
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "coc_webdav_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        prefs
    }

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
        get() = securePrefs.getString(KEY_PASS, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_PASS, value).apply()

    /** 是否已配置（URL 和用户名均非空）。 */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()

    private fun migrateLegacyPassword() {
        // 加密存储已有值则不迁移
        if (securePrefs.contains(KEY_PASS)) return
        val legacy = prefs.getString(KEY_PASS, null) ?: return
        if (legacy.isNotEmpty()) {
            securePrefs.edit().putString(KEY_PASS, legacy).apply()
        }
        prefs.edit().remove(KEY_PASS).apply()
    }

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"
    }
}
