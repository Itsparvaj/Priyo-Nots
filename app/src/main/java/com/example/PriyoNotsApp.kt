package com.example

import android.app.Application
import com.example.data.backup.AutoBackupScheduler
import com.example.data.local.PriyoNotsDatabase
import com.example.data.reminder.NotificationChannels
import com.example.data.repository.PriyoNotsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PriyoNotsApp : Application() {

    lateinit var database: PriyoNotsDatabase
        private set

    lateinit var repository: PriyoNotsRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
        database = PriyoNotsDatabase.getInstance(this)
        repository = PriyoNotsRepository(this, database)

        appScope.launch {
            val settings = repository.getSettings()
            if (settings.autoBackupEnabled) {
                AutoBackupScheduler.schedulePeriodicBackup(this@PriyoNotsApp, settings.backupFrequencyHours)
            }
        }
    }
}
