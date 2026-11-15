package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.RemindersEntity
import kotlinx.coroutines.flow.Flow

data class ReminderWithNoteTitle(
    val id: Long,
    val syncKey: String,
    val noteId: Long,
    val triggerAtMillis: Long,
    val repeatType: String,
    val isActive: Boolean,
    val requestCode: Int,
    val noteTitle: String,
    val notePlainText: String
)

@Dao
interface RemindersDao {

    @Query(
        """
        SELECT r.id, r.syncKey, r.noteId, r.triggerAtMillis, r.repeatType, r.isActive, r.requestCode,
               n.title AS noteTitle, n.plainText AS notePlainText
        FROM reminders r
        INNER JOIN notes n ON n.id = r.noteId
        WHERE n.isTrashed = 0 AND r.isActive = 1
        ORDER BY r.triggerAtMillis ASC
        """
    )
    fun observeActiveRemindersWithNotes(): Flow<List<ReminderWithNoteTitle>>

    @Query("SELECT * FROM reminders WHERE noteId = :noteId AND isActive = 1 LIMIT 1")
    fun observeActiveReminderForNote(noteId: Long): Flow<RemindersEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): RemindersEntity?

    @Query("SELECT * FROM reminders WHERE noteId = :noteId LIMIT 1")
    suspend fun getReminderByNoteId(noteId: Long): RemindersEntity?

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    suspend fun getAllActiveReminders(): List<RemindersEntity>

    @Query("SELECT * FROM reminders ORDER BY id ASC")
    suspend fun getAllRemindersRaw(): List<RemindersEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: RemindersEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<RemindersEntity>): List<Long>

    @Update
    suspend fun updateReminder(reminder: RemindersEntity)

    @Query("UPDATE reminders SET isActive = 0, updatedAt = :updatedAt WHERE id = :reminderId")
    suspend fun deactivateReminder(reminderId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteRemindersForNote(noteId: Long)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
}
