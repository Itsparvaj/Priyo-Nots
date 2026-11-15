package com.example.data.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.entity.RemindersEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (reminderId <= 0L || noteId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PriyoNotsDatabase.getInstance(context)
                val remindersDao = db.remindersDao()
                val notesDao = db.notesDao()
                val settings = db.appSettingsDao().getSettings()

                when (action) {
                    ACTION_DISMISS_REMINDER -> {
                        remindersDao.deactivateReminder(reminderId)
                        cancelNotification(context, reminderId.toInt())
                    }

                    ACTION_SNOOZE_15M -> {
                        val existing = remindersDao.getReminderById(reminderId)
                        if (existing != null) {
                            val snoozed = existing.copy(
                                triggerAtMillis = System.currentTimeMillis() + 15L * 60_000L,
                                isActive = true,
                                updatedAt = System.currentTimeMillis()
                            )
                            remindersDao.updateReminder(snoozed)
                            ReminderScheduler.scheduleReminder(context, snoozed)
                        }
                        cancelNotification(context, reminderId.toInt())
                    }

                    ACTION_FIRE_REMINDER -> {
                        val reminder = remindersDao.getReminderById(reminderId) ?: return@launch
                        val note = notesDao.getNoteById(noteId)?.note
                        if (!reminder.isActive || note == null || note.isTrashed) {
                            remindersDao.deactivateReminder(reminderId)
                            return@launch
                        }

                        if (settings?.notificationsEnabled != false) {
                            postReminderNotification(
                                context = context,
                                reminderId = reminderId,
                                noteId = noteId,
                                title = note.title.ifBlank { "Priyo Nots Reminder" },
                                preview = note.plainText.ifBlank { "Tap to open your note" },
                                enableSound = settings?.reminderSoundEnabled ?: true,
                                enableVibrate = settings?.reminderVibrateEnabled ?: true
                            )
                        }

                        // Handle repeating reminders or mark one-time reminders completed
                        val nextTrigger = when (reminder.repeatType) {
                            RemindersEntity.REPEAT_DAILY -> reminder.triggerAtMillis + 24L * 3600_000L
                            RemindersEntity.REPEAT_WEEKLY -> reminder.triggerAtMillis + 7L * 24L * 3600_000L
                            else -> null
                        }
                        if (nextTrigger != null) {
                            val updated = reminder.copy(
                                triggerAtMillis = nextTrigger,
                                updatedAt = System.currentTimeMillis()
                            )
                            remindersDao.updateReminder(updated)
                            ReminderScheduler.scheduleReminder(context, updated)
                        } else {
                            remindersDao.deactivateReminder(reminderId)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postReminderNotification(
        context: Context,
        reminderId: Long,
        noteId: Long,
        title: String,
        preview: String,
        enableSound: Boolean,
        enableVibrate: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationChannels.ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            (noteId * 100 + 1).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_15M
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            (reminderId * 100 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DISMISS_REMINDER
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            (reminderId * 100 + 3).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(preview.take(140))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(300)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "Snooze 15m", snoozePending)
            .addAction(0, "Mark Done", donePending)

        if (!enableSound) {
            builder.setSilent(true)
        } else if (enableVibrate) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(reminderId.toInt(), builder.build())
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.example.priyonots.ACTION_FIRE_REMINDER"
        const val ACTION_SNOOZE_15M = "com.example.priyonots.ACTION_SNOOZE_15M"
        const val ACTION_DISMISS_REMINDER = "com.example.priyonots.ACTION_DISMISS_REMINDER"
    }
}
