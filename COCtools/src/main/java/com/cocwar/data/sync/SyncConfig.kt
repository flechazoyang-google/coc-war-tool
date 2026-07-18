package com.cocwar.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV 同步配置存储（SharedPreferences）。
 */
class SyncConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("coc_webdav_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    /** 是否已配置（URL 和用户名均非空）。 */
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"
    }
}
