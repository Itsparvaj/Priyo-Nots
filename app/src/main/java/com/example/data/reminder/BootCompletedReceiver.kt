package com.example.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.backup.AutoBackupScheduler
import com.example.data.local.PriyoNotsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PriyoNotsDatabase.getInstance(context)
                val activeReminders = db.remindersDao().getAllActiveReminders()
                activeReminders.forEach { reminder ->
                    ReminderScheduler.scheduleReminder(context, reminder)
                }
                val settings = db.appSettingsDao().getSettings()
                if (settings?.autoBackupEnabled == true) {
                    AutoBackupScheduler.schedulePeriodicBackup(context, settings.backupFrequencyHours)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
