package com.cocwar

import android.app.Application
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.repository.WarRepository

class CocWarApplication : Application() {
    val database by lazy { WarDatabase.build(this) }
    val repository by lazy { WarRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        // 清理更新下载残留的 APK：安装流程结束后不再需要，若不清除会累积占用大量缓存
        // （每个 APK 约几十 MB）。下载新版本前 UpdateChecker 也会清理，这里是兜底。
        Thread {
            runCatching {
                cacheDir.listFiles()
                    ?.filter { it.name.startsWith("update_") && it.name.endsWith(".apk") }
                    ?.forEach { it.delete() }
            }
        }.start()
    }
}
