package com.example.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.R

object NotificationChannels {
    const val CHANNEL_REMINDERS = "priyo_reminders_channel"
    const val CHANNEL_UPDATES = "priyo_updates_channel"
    const val CHANNEL_BACKUPS = "priyo_backups_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
            enableVibration(true)
        }

        val updateChannel = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.update_channel_desc)
            enableVibration(true)
        }

        val backupChannel = NotificationChannel(
            CHANNEL_BACKUPS,
            context.getString(R.string.backup_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.backup_channel_desc)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(updateChannel)
        manager.createNotificationChannel(backupChannel)
    }
}
