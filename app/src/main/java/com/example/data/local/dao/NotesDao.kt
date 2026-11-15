package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.NotesEntity
import com.example.data.local.model.NoteWithRelations
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {

    @Transaction
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActiveNotes(): Flow<List<NoteWithRelations>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isPinned = 1 AND isArchived = 0 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observePinnedNotes(): Flow<List<NoteWithRelations>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY archivedAt DESC, updatedAt DESC")
    fun observeArchivedNotes(): Flow<List<NoteWithRelations>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC, updatedAt DESC")
    fun observeTrashedNotes(): Flow<List<NoteWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE isTrashed = 0
          AND (:includeArchived = 1 OR isArchived = 0)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (
            :query = '' OR
            title LIKE '%' || :query || '%' ESCAPE '\' OR
            plainText LIKE '%' || :query || '%' ESCAPE '\'
          )
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun searchNotes(
        query: String,
        categoryId: Long?,
        includeArchived: Boolean
    ): Flow<List<NoteWithRelations>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    fun observeNoteById(noteId: Long): Flow<NoteWithRelations?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): NoteWithRelations?

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun getAllNotesRaw(): List<NotesEntity>

    @Query("SELECT * FROM notes WHERE syncKey = :syncKey LIMIT 1")
    suspend fun getBySyncKey(syncKey: String): NotesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NotesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NotesEntity>): List<Long>

    @Update
    suspend fun updateNote(note: NotesEntity)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun setPinned(noteId: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = :archived, isPinned = CASE WHEN :archived = 1 THEN 0 ELSE isPinned END, archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun setArchived(
        noteId: Long,
        archived: Boolean,
        archivedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notes SET isTrashed = 1, isPinned = 0, trashedAt = :trashedAt, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun moveToTrash(
        noteId: Long,
        trashedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE notes SET isTrashed = 0, isArchived = 0, trashedAt = NULL, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deletePermanently(noteId: Long)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0 AND isArchived = 0")
    fun observeActiveCount(): Flow<Int>
}
