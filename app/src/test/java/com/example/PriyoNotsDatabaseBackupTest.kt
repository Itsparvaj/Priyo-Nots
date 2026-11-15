package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.AtomicFileHelper
import com.example.data.backup.BackupCrypto
import com.example.data.backup.BackupJsonCodec
import com.example.data.backup.BackupManager
import com.example.data.backup.RestoreMode
import com.example.data.local.PriyoNotsDatabase
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PriyoNotsDatabaseBackupTest {

    private lateinit var context: Context
    private lateinit var db: PriyoNotsDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PriyoNotsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `room crud pin archive trash restore and search`(): Unit = runBlocking {
        val catId = db.categoriesDao().insertCategory(
            CategoriesEntity(name = "Work", colorHex = "#0EA5E9", iconName = "work")
        )
        val noteId = db.notesDao().insertNote(
            NotesEntity(
                title = "Sprint Roadmap",
                contentHtml = "<p>Ship Priyo Nots</p>",
                plainText = "Ship Priyo Nots",
                colorKey = "lavender",
                categoryId = catId,
                wordCount = 3
            )
        )

        // Pin
        db.notesDao().setPinned(noteId, true)
        val pinned = db.notesDao().observePinnedNotes().first()
        assertEquals(1, pinned.size)
        assertEquals("Sprint Roadmap", pinned.first().note.title)

        // Search
        val searchHits = db.notesDao().searchNotes("Roadmap", null, includeArchived = false).first()
        assertEquals(1, searchHits.size)

        // Archive & restore
        db.notesDao().setArchived(noteId, true, System.currentTimeMillis())
        assertEquals(1, db.notesDao().observeArchivedNotes().first().size)
        assertEquals(0, db.notesDao().observeActiveNotes().first().size)

        db.notesDao().setArchived(noteId, false, null)
        assertEquals(1, db.notesDao().observeActiveNotes().first().size)

        // Trash & recovery & permanent delete
        db.notesDao().moveToTrash(noteId)
        assertEquals(1, db.notesDao().observeTrashedNotes().first().size)

        db.notesDao().restoreFromTrash(noteId)
        assertEquals(1, db.notesDao().observeActiveNotes().first().size)

        db.notesDao().moveToTrash(noteId)
        db.notesDao().deletePermanently(noteId)
        assertEquals(0, db.notesDao().getAllNotesRaw().size)
    }

    @Test
    fun `backup json encryption merge and replace restore`(): Unit = runBlocking {
        val catId = db.categoriesDao().insertCategory(
            CategoriesEntity(name = "Personal", colorHex = "#4F1ED8", iconName = "person")
        )
        val noteId = db.notesDao().insertNote(
            NotesEntity(
                title = "Encrypted Journal",
                contentHtml = "<p>Private thought</p>",
                plainText = "Private thought",
                colorKey = "amber",
                categoryId = catId,
                isPinned = true,
                wordCount = 2
            )
        )
        db.remindersDao().insertReminder(
            RemindersEntity(
                noteId = noteId,
                triggerAtMillis = System.currentTimeMillis() + 600_000L
            )
        )

        val backupManager = BackupManager(context, db)
        val (encryptedText, counts) = backupManager.createBackupPayload("SecretPass123")
        assertEquals(1, counts.first)
        assertTrue(BackupCrypto.isEncryptedEnvelope(encryptedText))

        val tempFile = File(context.cacheDir, "test_backup.pnbak")
        AtomicFileHelper.writeTextAtomically(tempFile, encryptedText)
        val uri = Uri.fromFile(tempFile)

        assertTrue(backupManager.inspectBackupRequiresPassword(uri))

        // Merge should skip duplicate note
        val mergeSummary = backupManager.restoreBackupFromUri(uri, RestoreMode.MERGE, "SecretPass123")
        assertEquals(0, mergeSummary.notesAdded)
        assertEquals(1, mergeSummary.notesSkippedDuplicate)

        // Replace should restore exact dataset cleanly
        val replaceSummary = backupManager.restoreBackupFromUri(uri, RestoreMode.REPLACE, "SecretPass123")
        assertEquals(1, replaceSummary.notesAdded)
        assertEquals(1, db.notesDao().getAllNotesRaw().size)
        assertEquals(1, db.categoriesDao().getAllCategoriesRaw().size)
        assertEquals(1, db.remindersDao().getAllRemindersRaw().size)

        tempFile.delete()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed backup json is rejected safely`() {
        BackupJsonCodec.decodeAndValidate("not json at all")
    }

    @Test
    fun `xss script tags are stripped from backup html`() {
        val sanitized = BackupJsonCodec.sanitizeHtmlBasic("<p>Hello</p><script>alert(1)</script><b onclick=\"evil()\">Bold</b>")
        assertFalse(sanitized.contains("<script>"))
        assertFalse(sanitized.contains("onclick"))
        assertNotNull(sanitized)
    }
}
