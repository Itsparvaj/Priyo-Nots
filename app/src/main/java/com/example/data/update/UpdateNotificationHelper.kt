package com.example.data.update

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.reminder.NotificationChannels

object UpdateNotificationHelper {

    private const val UPDATE_NOTIFICATION_ID = 9001
    private const val PREFS_NAME = "priyo_update_prefs"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_version_tag"

    fun showUpdateAvailableNotification(
        context: Context,
        metadata: UpdateMetadata,
        forceNotify: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val versionToken = "${metadata.versionName}:${metadata.versionCode}"
        if (!forceNotify && prefs.getString(KEY_LAST_NOTIFIED_TAG, null) == versionToken) {
            return
        }

        NotificationChannels.ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
        }
        val openPending = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = "Version ${metadata.versionName} is ready. Tap to download & install."
        val expanded = buildString {
            append(summary)
            if (metadata.releaseNotes.isNotBlank()) {
                append("\n\n")
                append(metadata.releaseNotes.trim().take(260))
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Priyo Nots v${metadata.versionName} Available")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "Update Now", openPending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, versionToken).apply()
    }

    fun cancelUpdateNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(UPDATE_NOTIFICATION_ID)
    }
}
