package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val themeMode: String = THEME_SYSTEM,
    val defaultSortOrder: String = SORT_MODIFIED_DESC,
    val isGridView: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val backupFrequencyHours: Int = 24,
    val backupFolderUri: String? = null,
    val backupFolderLabel: String = "App Private Storage",
    val lastBackupAt: Long? = null,
    val lastBackupStatus: String = "Not backed up yet",
    val notificationsEnabled: Boolean = true,
    val reminderSoundEnabled: Boolean = true,
    val reminderVibrateEnabled: Boolean = true,
    val updateCheckEnabled: Boolean = true,
    val updateEndpointUrl: String = "",
    val dismissedUpdateVersionCode: Int = 0,
    val lastUpdateCheckAt: Long? = null,
    val contactEmail: String = "",
    val contactWebsite: String = "",
    val privacyModeHidePreviews: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    // GitHub integration fields (added in DB v2)
    val updateSourceType: String = UPDATE_SOURCE_GITHUB,
    val githubReleaseRepo: String = "",
    val githubUsername: String = "",
    val githubBackupMode: String = GITHUB_MODE_GIST,
    val githubGistId: String = "",
    val githubRepoSlug: String = "",
    val githubRepoBranch: String = "main",
    val githubRepoFilePath: String = "backups/priyo_nots_backup.json",
    val githubLastBackupAt: Long? = null,
    val githubLastStatus: String = "Not connected"
) {
    companion object {
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"

        const val SORT_MODIFIED_DESC = "MODIFIED_DESC"
        const val SORT_CREATED_DESC = "CREATED_DESC"
        const val SORT_TITLE_ASC = "TITLE_ASC"
        const val SORT_TITLE_DESC = "TITLE_DESC"

        const val UPDATE_SOURCE_GITHUB = "GITHUB_RELEASES"
        const val UPDATE_SOURCE_CUSTOM_JSON = "CUSTOM_JSON"

        const val GITHUB_MODE_GIST = "GIST"
        const val GITHUB_MODE_REPO = "REPO"
    }
}
