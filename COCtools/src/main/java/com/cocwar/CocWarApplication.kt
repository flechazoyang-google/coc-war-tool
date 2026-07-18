package com.cocwar

import android.app.Application
import com.cocwar.data.db.WarDatabase
import com.cocwar.data.repository.WarRepository

class CocWarApplication : Application() {
    val database by lazy { WarDatabase.build(this) }
    val repository by lazy { WarRepository(database, this) }
}
