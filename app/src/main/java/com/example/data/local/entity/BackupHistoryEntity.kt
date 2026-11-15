package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_history",
    indices = [Index(value = ["createdAt"])]
)
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val fileUri: String,
    val fileSizeBytes: Long,
    val noteCount: Int,
    val categoryCount: Int,
    val reminderCount: Int,
    val isEncrypted: Boolean,
    val backupType: String,
    val locationLabel: String,
    val statusMessage: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_MANUAL = "MANUAL"
        const val TYPE_AUTO = "AUTOMATIC"
        const val TYPE_EXPORT = "EXPORT"
        const val TYPE_GITHUB_GIST = "GITHUB_GIST"
        const val TYPE_GITHUB_REPO = "GITHUB_REPO"
    }
}
