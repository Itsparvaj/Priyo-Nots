package com.example.data.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.data.github.GitHubService
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object UpdateCheckScheduler {

    private const val UPDATE_CHECK_JOB_ID = 4093
    private const val TWELVE_HOURS_MILLIS = 12L * 3600_000L

    fun schedulePeriodicUpdateCheck(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val component = ComponentName(context, UpdateCheckJobService::class.java)
        val jobInfo = JobInfo.Builder(UPDATE_CHECK_JOB_ID, component)
            .setPeriodic(TWELVE_HOURS_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        scheduler.schedule(jobInfo)
    }

    fun cancelPeriodicUpdateCheck(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(UPDATE_CHECK_JOB_ID)
    }
}

class UpdateCheckJobService : JobService() {

    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            try {
                val db = PriyoNotsDatabase.getInstance(applicationContext)
                val settingsDao = db.appSettingsDao()
                val settings = settingsDao.getSettings() ?: AppSettingsEntity()
                if (!settings.updateCheckEnabled) return@launch

                val useGitHub = settings.updateSourceType == AppSettingsEntity.UPDATE_SOURCE_GITHUB
                val result = if (useGitHub) {
                    val repo = settings.githubReleaseRepo.ifBlank { AppSettingsEntity.DEFAULT_UPDATE_REPO }
                    GitHubService.fetchLatestRelease(repo, null)
                } else {
                    if (settings.updateEndpointUrl.isBlank()) return@launch
                    UpdateChecker(applicationContext).checkForUpdate(settings.updateEndpointUrl)
                }

                result.onSuccess { meta ->
                    val now = System.currentTimeMillis()
                    settingsDao.upsertSettings(settings.copy(lastUpdateCheckAt = now, updatedAt = now))
                    if (meta != null && meta.isNewerThanInstalled && settings.notificationsEnabled) {
                        if (meta.forceUpdate || meta.versionCode != settings.dismissedUpdateVersionCode) {
                            UpdateNotificationHelper.showUpdateAvailableNotification(applicationContext, meta)
                        }
                    }
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
