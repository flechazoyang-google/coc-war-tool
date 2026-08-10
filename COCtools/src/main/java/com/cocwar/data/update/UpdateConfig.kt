package com.cocwar.data.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 更新相关设置存储（轻量非敏感配置，普通 SharedPreferences）。
 */
object UpdatePrefs {
    private const val PREFS_NAME = "cocwar_update_prefs"
    private const val KEY_INCLUDE_PRERELEASE = "include_prerelease"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否加入测试计划：检查更新时预览版（prerelease）也纳入「最新」判定。默认关闭。 */
    fun isPrereleaseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_PRERELEASE, false)

    fun setPrereleaseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_PRERELEASE, enabled).apply()
    }
}
