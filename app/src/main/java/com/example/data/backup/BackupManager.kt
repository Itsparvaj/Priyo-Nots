package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity
import com.example.data.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RestoreMode {
    MERGE,
    REPLACE
}

data class BackupResult(
    val fileName: String,
    val fileUri: String,
    val fileSizeBytes: Long,
    val noteCount: Int,
    val categoryCount: Int,
    val reminderCount: Int,
    val locationLabel: String
)

data class RestoreSummary(
    val notesAdded: Int,
    val notesUpdated: Int,
    val notesSkippedDuplicate: Int,
    val categoriesImported: Int,
    val remindersImported: Int
)

class BackupManager(
    private val context: Context,
    private val database: PriyoNotsDatabase
) {

    suspend fun createBackupPayload(password: String?): Pair<String, Triple<Int, Int, Int>> =
        withContext(Dispatchers.IO) {
            val notes = database.notesDao().getAllNotesRaw()
            val categories = database.categoriesDao().getAllCategoriesRaw()
            val reminders = database.remindersDao().getAllRemindersRaw()
            val settings = database.appSettingsDao().getSettings()

            val json = BackupJsonCodec.encodeFullBackup(notes, categories, reminders, settings)
            val finalContent = if (!password.isNullOrBlank()) {
                BackupCrypto.encryptPayload(json, password)
            } else {
                json
            }
            Pair(finalContent, Triple(notes.size, categories.size, reminders.size))
        }

    suspend fun performManualBackupToAppStorage(password: String?): BackupResult =
        withContext(Dispatchers.IO) {
            val (content, counts) = createBackupPayload(password)
            val isEncrypted = !password.isNullOrBlank()
            val ext = if (isEncrypted) "pnbak" else "json"
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "PriyoNots_Backup_$stamp.$ext"
            val dir = AtomicFileHelper.getBackupsDir(context)
            val targetFile = File(dir, fileName)
            AtomicFileHelper.writeTextAtomically(targetFile, content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            ).toString()

            val sizeBytes = targetFile.length()
            val locationLabel = "App Private Storage (PriyoNots/backups)"
            recordBackupSuccess(
                fileName = fileName,
                fileUri = uri,
                sizeBytes = sizeBytes,
                counts = counts,
                isEncrypted = isEncrypted,
                backupType = BackupHistoryEntity.TYPE_MANUAL,
                locationLabel = locationLabel
            )

            BackupResult(
                fileName = fileName,
                fileUri = uri,
                fileSizeBytes = sizeBytes,
                noteCount = counts.first,
                categoryCount = counts.second,
                reminderCount = counts.third,
                locationLabel = locationLabel
            )
        }

    suspend fun performManualBackupToUri(
        destinationUri: Uri,
        displayName: String,
        password: String?
    ): BackupResult = withContext(Dispatchers.IO) {
        val (content, counts) = createBackupPayload(password)
        val isEncrypted = !password.isNullOrBlank()
        val sizeBytes = AtomicFileHelper.writeTextToUri(context, destinationUri, content)
        val locationLabel = "User-Selected Folder"

        recordBackupSuccess(
            fileName = displayName,
            fileUri = destinationUri.toString(),
            sizeBytes = sizeBytes,
            counts = counts,
            isEncrypted = isEncrypted,
            backupType = BackupHistoryEntity.TYPE_MANUAL,
            locationLabel = locationLabel
        )

        BackupResult(
            fileName = displayName,
            fileUri = destinationUri.toString(),
            fileSizeBytes = sizeBytes,
            noteCount = counts.first,
            categoryCount = counts.second,
            reminderCount = counts.third,
            locationLabel = locationLabel
        )
    }

    suspend fun performAutomaticBackup(): BackupResult = withContext(Dispatchers.IO) {
        val settings = database.appSettingsDao().getSettings() ?: AppSettingsEntity()
        val (content, counts) = createBackupPayload(null)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PriyoNots_AutoBackup_$stamp.json"

        val folderUriStr = settings.backupFolderUri
        if (!folderUriStr.isNullOrBlank()) {
            val treeResult = runCatching {
                val treeUri = Uri.parse(folderUriStr)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    "application/json",
                    fileName
                ) ?: throw IllegalStateException("Could not create document in selected folder")
                val sizeBytes = AtomicFileHelper.writeTextToUri(context, createdUri, content)
                recordBackupSuccess(
                    fileName = fileName,
                    fileUri = createdUri.toString(),
                    sizeBytes = sizeBytes,
                    counts = counts,
                    isEncrypted = false,
                    backupType = BackupHistoryEntity.TYPE_AUTO,
                    locationLabel = settings.backupFolderLabel
                )
                BackupResult(
                    fileName = fileName,
                    fileUri = createdUri.toString(),
                    fileSizeBytes = sizeBytes,
                    noteCount = counts.first,
                    categoryCount = counts.second,
                    reminderCount = counts.third,
                    locationLabel = settings.backupFolderLabel
                )
            }
            if (treeResult.isSuccess) {
                return@withContext treeResult.getOrThrow()
            }
        }

        // Fallback to app-private PriyoNots/backups if no persistent folder is chosen
        val dir = AtomicFileHelper.getBackupsDir(context)
        val targetFile = File(dir, fileName)
        AtomicFileHelper.writeTextAtomically(targetFile, content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        ).toString()
        val sizeBytes = targetFile.length()
        val locationLabel = "App Private Storage (PriyoNots/backups)"
        recordBackupSuccess(
            fileName = fileName,
            fileUri = uri,
            sizeBytes = sizeBytes,
            counts = counts,
            isEncrypted = false,
            backupType = BackupHistoryEntity.TYPE_AUTO,
            locationLabel = locationLabel
        )
        BackupResult(
            fileName = fileName,
            fileUri = uri,
            fileSizeBytes = sizeBytes,
            noteCount = counts.first,
            categoryCount = counts.second,
            reminderCount = counts.third,
            locationLabel = locationLabel
        )
    }

    suspend fun inspectBackupRequiresPassword(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val raw = AtomicFileHelper.readTextFromUri(context, uri)
        BackupCrypto.isEncryptedEnvelope(raw)
    }

    suspend fun restoreBackupFromUri(
        uri: Uri,
        mode: RestoreMode,
        password: String?,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): RestoreSummary = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Reading backup file...")
        val rawText = AtomicFileHelper.readTextFromUri(context, uri)
        restoreFromRawText(rawText, mode, password, onProgress)
    }

    suspend fun restoreFromRawText(
        rawText: String,
        mode: RestoreMode,
        password: String?,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): RestoreSummary = withContext(Dispatchers.IO) {
        onProgress(0.30f, "Validating format...")
        val plainJson = if (BackupCrypto.isEncryptedEnvelope(rawText)) {
            if (password.isNullOrBlank()) {
                throw IllegalArgumentException("This backup is encrypted. Please enter the backup password.")
            }
            try {
                BackupCrypto.decryptPayload(rawText, password)
            } catch (e: Exception) {
                throw IllegalArgumentException("Incorrect password or corrupted encrypted backup.")
            }
        } else {
            rawText
        }

        val parsed = BackupJsonCodec.decodeAndValidate(plainJson)
        onProgress(0.55f, "Applying backup to Room database...")

        var notesAdded = 0
        var notesUpdated = 0
        var notesSkipped = 0
        var categoriesImported = 0
        var remindersImported = 0

        val scheduledAfterRestore = mutableListOf<RemindersEntity>()

        database.withTransaction {
            val notesDao = database.notesDao()
            val categoriesDao = database.categoriesDao()
            val remindersDao = database.remindersDao()
            val settingsDao = database.appSettingsDao()

            if (mode == RestoreMode.REPLACE) {
                val existingReminders = remindersDao.getAllActiveReminders()
                existingReminders.forEach { ReminderScheduler.cancelReminder(context, it) }
                remindersDao.deleteAllReminders()
                notesDao.deleteAllNotes()
                categoriesDao.deleteAllCategories()
            }

            // 1. Restore categories and map oldCategoryId -> newCategoryId
            val oldCatIdToNewId = mutableMapOf<Long, Long>()
            for (cat in parsed.categories) {
                val existingBySync = categoriesDao.getBySyncKey(cat.syncKey)
                val existingByName = categoriesDao.getByName(cat.name)
                val resolvedId = when {
                    existingBySync != null -> {
                        categoriesDao.updateCategory(
                            existingBySync.copy(
                                name = cat.name,
                                colorHex = cat.colorHex,
                                iconName = cat.iconName
                            )
                        )
                        existingBySync.id
                    }
                    existingByName != null -> existingByName.id
                    else -> {
                        categoriesImported++
                        categoriesDao.insertCategory(cat.copy(id = 0L))
                    }
                }
                if (cat.id > 0L) {
                    oldCatIdToNewId[cat.id] = resolvedId
                }
            }

            onProgress(0.75f, "Restoring notes...")
            val existingNotes = notesDao.getAllNotesRaw()
            val noteSyncToNewId = mutableMapOf<String, Long>()

            for (note in parsed.notes) {
                val newCategoryId = note.categoryId?.let { oldCatIdToNewId[it] }
                val matchBySync = notesDao.getBySyncKey(note.syncKey)
                val matchByContent = existingNotes.firstOrNull {
                    it.title == note.title && it.plainText == note.plainText && it.createdAt == note.createdAt
                }

                when {
                    mode == RestoreMode.MERGE && matchBySync != null -> {
                        if (note.updatedAt > matchBySync.updatedAt) {
                            notesDao.updateNote(
                                note.copy(
                                    id = matchBySync.id,
                                    categoryId = newCategoryId
                                )
                            )
                            notesUpdated++
                        } else {
                            notesSkipped++
                        }
                        noteSyncToNewId[note.syncKey] = matchBySync.id
                    }
                    mode == RestoreMode.MERGE && matchByContent != null -> {
                        notesSkipped++
                        noteSyncToNewId[note.syncKey] = matchByContent.id
                    }
                    else -> {
                        val insertedId = notesDao.insertNote(
                            note.copy(
                                id = 0L,
                                categoryId = newCategoryId
                            )
                        )
                        notesAdded++
                        noteSyncToNewId[note.syncKey] = insertedId
                    }
                }
            }

            onProgress(0.90f, "Restoring reminders...")
            for (rem in parsed.reminders) {
                val noteSyncKey = parsed.reminderNoteSyncKeys[rem.syncKey] ?: continue
                val targetNoteId = noteSyncToNewId[noteSyncKey] ?: continue
                val insertedRemId = remindersDao.insertReminder(
                    rem.copy(
                        id = 0L,
                        noteId = targetNoteId
                    )
                )
                val savedRem = rem.copy(id = insertedRemId, noteId = targetNoteId)
                if (savedRem.isActive && savedRem.triggerAtMillis > System.currentTimeMillis()) {
                    scheduledAfterRestore.add(savedRem)
                }
                remindersImported++
            }

            if (mode == RestoreMode.REPLACE && parsed.settings != null) {
                val current = settingsDao.getSettings() ?: AppSettingsEntity()
                settingsDao.upsertSettings(
                    parsed.settings.copy(
                        id = 1,
                        backupFolderUri = current.backupFolderUri,
                        backupFolderLabel = current.backupFolderLabel,
                        lastBackupAt = current.lastBackupAt,
                        lastBackupStatus = current.lastBackupStatus
                    )
                )
            }
        }

        scheduledAfterRestore.forEach { ReminderScheduler.scheduleReminder(context, it) }
        onProgress(1.0f, "Restore complete")

        RestoreSummary(
            notesAdded = notesAdded,
            notesUpdated = notesUpdated,
            notesSkippedDuplicate = notesSkipped,
            categoriesImported = categoriesImported,
            remindersImported = remindersImported
        )
    }

    suspend fun exportSingleNoteToAppExports(note: NotesEntity): File = withContext(Dispatchers.IO) {
        val safeTitle = note.title.ifBlank { "Untitled_Note" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(40)
        val fileName = "${safeTitle}_${note.id}.json"
        val json = BackupJsonCodec.encodeFullBackup(
            notes = listOf(note),
            categories = emptyList(),
            reminders = emptyList(),
            settings = null
        )
        val file = File(AtomicFileHelper.getExportsDir(context), fileName)
        AtomicFileHelper.writeTextAtomically(file, json)
        file
    }

    suspend fun recordExternalBackupSuccess(
        fileName: String,
        fileUri: String,
        sizeBytes: Long,
        counts: Triple<Int, Int, Int>,
        isEncrypted: Boolean,
        backupType: String,
        locationLabel: String
    ) {
        recordBackupSuccess(
            fileName = fileName,
            fileUri = fileUri,
            sizeBytes = sizeBytes,
            counts = counts,
            isEncrypted = isEncrypted,
            backupType = backupType,
            locationLabel = locationLabel
        )
    }

    private suspend fun recordBackupSuccess(
        fileName: String,
        fileUri: String,
        sizeBytes: Long,
        counts: Triple<Int, Int, Int>,
        isEncrypted: Boolean,
        backupType: String,
        locationLabel: String
    ) {
        val now = System.currentTimeMillis()
        database.backupHistoryDao().insertBackupRecord(
            BackupHistoryEntity(
                fileName = fileName,
                fileUri = fileUri,
                fileSizeBytes = sizeBytes,
                noteCount = counts.first,
                categoryCount = counts.second,
                reminderCount = counts.third,
                isEncrypted = isEncrypted,
                backupType = backupType,
                locationLabel = locationLabel,
                statusMessage = "Completed (${counts.first} notes)",
                createdAt = now
            )
        )
        val currentSettings = database.appSettingsDao().getSettings() ?: AppSettingsEntity()
        database.appSettingsDao().upsertSettings(
            currentSettings.copy(
                lastBackupAt = now,
                lastBackupStatus = "Success (${counts.first} notes)",
                updatedAt = now
            )
        )
    }
}
