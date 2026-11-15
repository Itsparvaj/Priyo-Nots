package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BackupHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupHistoryDao {

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    fun observeBackupHistory(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecord(record: BackupHistoryEntity): Long

    @Query("DELETE FROM backup_history WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM backup_history")
    suspend fun clearAllHistory()
}
