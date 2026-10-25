package com.lunhui.log

import android.app.Application
import androidx.room.Room
import com.lunhui.log.core.database.LogDatabase
import com.lunhui.log.core.backup.BackupRepository
import com.lunhui.log.core.time.AndroidRecordClock
import com.lunhui.log.feature.bowel.RecordRepository
import com.lunhui.log.feature.settings.AppPreferences

class LunhuiApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, LogDatabase::class.java, "lunhui.db").build() }
    val repository by lazy { RecordRepository(database, AndroidRecordClock(this)) }
    val backups by lazy { BackupRepository(database) }
    val preferences by lazy { AppPreferences(this) }
}
