package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupResult
import com.example.data.backup.RestoreMode
import com.example.data.backup.RestoreSummary
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.dao.ReminderWithNoteTitle
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity
import com.example.data.local.model.CategoryWithCount
import com.example.data.local.model.NoteWithRelations
import com.example.data.reminder.ReminderScheduler
import com.example.data.update.DownloadState
import com.example.data.update.UpdateChecker
import com.example.data.update.UpdateMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class PriyoNotsRepository(
    private val context: Context,
    private val database: PriyoNotsDatabase
) {
    private val notesDao = database.notesDao()
    private val categoriesDao = database.categoriesDao()
    private val remindersDao = database.remindersDao()
    private val settingsDao = database.appSettingsDao()
    private val backupHistoryDao = database.backupHistoryDao()

    private val backupManager = BackupManager(context, database)
    private val updateChecker = UpdateChecker(context)

    val activeNotes: Flow<List<NoteWithRelations>> = notesDao.observeActiveNotes()
    val pinnedNotes: Flow<List<NoteWithRelations>> = notesDao.observePinnedNotes()
    val archivedNotes: Flow<List<NoteWithRelations>> = notesDao.observeArchivedNotes()
    val trashedNotes: Flow<List<NoteWithRelations>> = notesDao.observeTrashedNotes()
    val categoriesWithCount: Flow<List<CategoryWithCount>> = categoriesDao.observeCategoriesWithCount()
    val activeReminders: Flow<List<ReminderWithNoteTitle>> = remindersDao.observeActiveRemindersWithNotes()
    val backupHistory: Flow<List<BackupHistoryEntity>> = backupHistoryDao.observeBackupHistory()
    val activeCount: Flow<Int> = notesDao.observeActiveCount()

    val settings: Flow<AppSettingsEntity> = settingsDao.observeSettings().map { it ?: AppSettingsEntity() }

    fun searchNotes(query: String, categoryId: Long?, includeArchived: Boolean): Flow<List<NoteWithRelations>> {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return notesDao.searchNotes(escaped, categoryId, includeArchived)
    }

    fun observeNoteById(noteId: Long): Flow<NoteWithRelations?> = notesDao.observeNoteById(noteId)

    suspend fun getNoteById(noteId: Long): NoteWithRelations? = notesDao.getNoteById(noteId)

    suspend fun saveNote(
        existingId: Long?,
        title: String,
        contentHtml: String,
        plainText: String,
        colorKey: String,
        categoryId: Long?,
        isPinned: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        val words = plainText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return if (existingId != null && existingId > 0L) {
            val current = notesDao.getNoteById(existingId)?.note
            if (current != null) {
                val updated = current.copy(
                    title = title,
                    contentHtml = contentHtml,
                    plainText = plainText,
                    colorKey = colorKey,
                    categoryId = categoryId,
                    isPinned = isPinned,
                    wordCount = words,
                    updatedAt = now
                )
                notesDao.updateNote(updated)
                existingId
            } else {
                notesDao.insertNote(
                    NotesEntity(
                        title = title,
                        contentHtml = contentHtml,
                        plainText = plainText,
                        colorKey = colorKey,
                        categoryId = categoryId,
                        isPinned = isPinned,
                        wordCount = words,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        } else {
            notesDao.insertNote(
                NotesEntity(
                    title = title,
                    contentHtml = contentHtml,
                    plainText = plainText,
                    colorKey = colorKey,
                    categoryId = categoryId,
                    isPinned = isPinned,
                    wordCount = words,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) {
        notesDao.setPinned(noteId, pinned)
    }

    suspend fun setArchived(noteId: Long, archived: Boolean) {
        notesDao.setArchived(noteId, archived, if (archived) System.currentTimeMillis() else null)
    }

    suspend fun moveToTrash(noteId: Long) {
        database.withTransaction {
            notesDao.moveToTrash(noteId)
            val rem = remindersDao.getReminderByNoteId(noteId)
            if (rem != null && rem.isActive) {
                remindersDao.deactivateReminder(rem.id)
                ReminderScheduler.cancelReminder(context, rem)
            }
        }
    }

    suspend fun restoreFromTrash(noteId: Long) {
        notesDao.restoreFromTrash(noteId)
    }

    suspend fun deletePermanently(noteId: Long) {
        val rem = remindersDao.getReminderByNoteId(noteId)
        if (rem != null) {
            ReminderScheduler.cancelReminder(context, rem)
        }
        notesDao.deletePermanently(noteId)
    }

    suspend fun emptyTrash() {
        notesDao.emptyTrash()
    }

    suspend fun createCategory(name: String, colorHex: String, iconName: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Category name cannot be empty"))
        val existing = categoriesDao.getByName(trimmed)
        if (existing != null) return Result.failure(IllegalArgumentException("Category \"$trimmed\" already exists"))
        val id = categoriesDao.insertCategory(
            CategoriesEntity(name = trimmed, colorHex = colorHex, iconName = iconName)
        )
        return Result.success(id)
    }

    suspend fun updateCategory(category: CategoriesEntity): Result<Unit> {
        val trimmed = category.name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Category name cannot be empty"))
        val duplicate = categoriesDao.getByName(trimmed)
        if (duplicate != null && duplicate.id != category.id) {
            return Result.failure(IllegalArgumentException("Another category already uses \"$trimmed\""))
        }
        categoriesDao.updateCategory(category.copy(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoriesDao.deleteCategoryById(categoryId)
    }

    suspend fun setNoteReminder(noteId: Long, triggerAtMillis: Long, repeatType: String): RemindersEntity {
        val existing = remindersDao.getReminderByNoteId(noteId)
        val now = System.currentTimeMillis()
        val saved = if (existing != null) {
            val updated = existing.copy(
                triggerAtMillis = triggerAtMillis,
                repeatType = repeatType,
                isActive = true,
                updatedAt = now
            )
            remindersDao.updateReminder(updated)
            updated
        } else {
            val newReminder = RemindersEntity(
                noteId = noteId,
                triggerAtMillis = triggerAtMillis,
                repeatType = repeatType,
                isActive = true,
                requestCode = ((noteId * 31 + (now and 0xFFFF)) and 0x7FFFFFFF).toInt(),
                createdAt = now,
                updatedAt = now
            )
            val id = remindersDao.insertReminder(newReminder)
            newReminder.copy(id = id)
        }
        ReminderScheduler.scheduleReminder(context, saved)
        return saved
    }

    suspend fun cancelReminder(reminderId: Long) {
        val rem = remindersDao.getReminderById(reminderId) ?: return
        remindersDao.deactivateReminder(reminderId)
        ReminderScheduler.cancelReminder(context, rem)
    }

    suspend fun cancelReminderForNote(noteId: Long) {
        val rem = remindersDao.getReminderByNoteId(noteId) ?: return
        remindersDao.deactivateReminder(rem.id)
        ReminderScheduler.cancelReminder(context, rem)
    }

    suspend fun getSettings(): AppSettingsEntity {
        return settingsDao.getSettings() ?: AppSettingsEntity().also {
            settingsDao.upsertSettings(it)
        }
    }

    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity): AppSettingsEntity {
        val current = getSettings()
        val updated = transform(current).copy(id = 1, updatedAt = System.currentTimeMillis())
        settingsDao.upsertSettings(updated)
        return updated
    }

    suspend fun backupToAppStorage(password: String?): BackupResult =
        backupManager.performManualBackupToAppStorage(password)

    suspend fun backupToUri(uri: Uri, displayName: String, password: String?): BackupResult =
        backupManager.performManualBackupToUri(uri, displayName, password)

    suspend fun runAutoBackupNow(): BackupResult =
        backupManager.performAutomaticBackup()

    suspend fun inspectRequiresPassword(uri: Uri): Boolean =
        backupManager.inspectBackupRequiresPassword(uri)

    suspend fun restoreFromUri(
        uri: Uri,
        mode: RestoreMode,
        password: String?,
        onProgress: (Float, String) -> Unit
    ): RestoreSummary = backupManager.restoreBackupFromUri(uri, mode, password, onProgress)

    suspend fun exportNoteAsJsonFile(note: NotesEntity): File =
        backupManager.exportSingleNoteToAppExports(note)

    suspend fun deleteBackupHistoryRecord(id: Long) {
        backupHistoryDao.deleteRecordById(id)
    }

    suspend fun checkForUpdate(endpointUrl: String): Result<UpdateMetadata?> =
        updateChecker.checkForUpdate(endpointUrl)

    fun downloadUpdateApk(metadata: UpdateMetadata): Flow<DownloadState> =
        updateChecker.downloadAndVerifyApk(metadata)

    fun canInstallPackages(): Boolean = updateChecker.canInstallPackages()

    fun buildInstallSettingsIntent() = updateChecker.buildInstallSettingsIntent()

    fun buildInstallApkIntent(file: File) = updateChecker.launchSystemPackageInstaller(file)
}
