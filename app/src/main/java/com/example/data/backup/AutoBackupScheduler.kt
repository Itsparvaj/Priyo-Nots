package com.example.data.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AutoBackupScheduler {

    private const val AUTO_BACKUP_JOB_ID = 4092

    fun schedulePeriodicBackup(context: Context, frequencyHours: Int) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val intervalMillis = frequencyHours.coerceIn(6, 168) * 3600_000L
        val component = ComponentName(context, AutoBackupJobService::class.java)
        val jobInfo = JobInfo.Builder(AUTO_BACKUP_JOB_ID, component)
            .setPeriodic(intervalMillis)
            .setPersisted(true)
            .setRequiresBatteryNotLow(true)
            .build()
        scheduler.schedule(jobInfo)
    }

    fun cancelPeriodicBackup(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(AUTO_BACKUP_JOB_ID)
    }
}

class AutoBackupJobService : JobService() {

    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            var needsReschedule = false
            try {
                val db = PriyoNotsDatabase.getInstance(applicationContext)
                val settings = db.appSettingsDao().getSettings()
                if (settings?.autoBackupEnabled == true) {
                    BackupManager(applicationContext, db).performAutomaticBackup()
                }
            } catch (e: Exception) {
                val db = PriyoNotsDatabase.getInstance(applicationContext)
                val current = db.appSettingsDao().getSettings() ?: AppSettingsEntity()
                db.appSettingsDao().upsertSettings(
                    current.copy(
                        lastBackupStatus = "Failed: ${e.message ?: "Unknown error"}",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                needsReschedule = true
            } finally {
                jobFinished(params, needsReschedule)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
