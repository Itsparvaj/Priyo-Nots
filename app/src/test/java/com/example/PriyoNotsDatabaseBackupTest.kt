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
import com.example.data.drive.GoogleDriveService
import com.example.data.github.GitHubService
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

    @Test
    fun `github repo slug normalization and release parsing`() {
        assertEquals("mohammadparvaj/priyo-nots", GitHubService.normalizeRepoSlug("https://github.com/mohammadparvaj/priyo-nots.git"))
        assertEquals("mohammadparvaj/priyo-nots", GitHubService.normalizeRepoSlug("mohammadparvaj/priyo-nots"))
        assertEquals(null, GitHubService.normalizeRepoSlug("invalid-slug"))

        assertTrue(GitHubService.compareSemanticVersions("2.1.0", "1.0") > 0)
        assertEquals(0, GitHubService.compareSemanticVersions("v1.0.0", "1.0"))

        val releaseJson = """
            {
              "tag_name": "v2.3.1",
              "name": "Priyo Nots v2.3.1",
              "body": "Added cloud sync [force-update]",
              "html_url": "https://github.com/mohammadparvaj/priyo-nots/releases/tag/v2.3.1",
              "assets": [
                {
                  "name": "PriyoNots-v2.3.1-release.apk",
                  "browser_download_url": "https://github.com/mohammadparvaj/priyo-nots/releases/download/v2.3.1/PriyoNots-v2.3.1-release.apk",
                  "digest": "sha256:abcdef123456"
                }
              ]
            }
        """.trimIndent()

        val meta = GitHubService.parseReleaseJson(releaseJson)
        assertEquals("2.3.1", meta.versionName)
        assertTrue(meta.isNewerThanInstalled)
        assertTrue(meta.hasDirectApkAsset)
        assertTrue(meta.forceUpdate)
        assertEquals("abcdef123456", meta.sha256)
    }

    @Test
    fun `cloud raw text restore works with encrypted payload`(): Unit = runBlocking {
        db.notesDao().insertNote(
            NotesEntity(title = "Cloud Note", contentHtml = "<p>Synced</p>", plainText = "Synced", wordCount = 1)
        )
        val manager = BackupManager(context, db)
        val (encrypted, _) = manager.createBackupPayload("CloudKey99")

        val summary = manager.restoreFromRawText(encrypted, RestoreMode.REPLACE, "CloudKey99")
        assertEquals(1, summary.notesAdded)
        assertEquals("Cloud Note", db.notesDao().getAllNotesRaw().first().title)
    }

    @Test
    fun `google drive service parses files multipart and userinfo`() {
        val email = GoogleDriveService.parseUserInfoEmail("""{"email":"bloggerparvaj30@gmail.com","name":"Mohammad Parvaj"}""")
        assertEquals("bloggerparvaj30@gmail.com", email)

        val multipart = GoogleDriveService.buildMultipartBody(
            metadataJson = """{"name":"PriyoNots_Backup.json"}""",
            contentText = """{"notes":[]}""",
            boundary = "test_bound"
        )
        assertTrue(multipart.contains("--test_bound\r\n"))
        assertTrue(multipart.contains("PriyoNots_Backup.json"))
        assertTrue(multipart.endsWith("--test_bound--"))

        val uploaded = GoogleDriveService.parseUploadedFileJson(
            rawJson = """{"id":"file_99","name":"PriyoNots_Backup.pnbak","size":"2048","webViewLink":"https://drive.google.com/file/d/file_99/view"}""",
            folderId = "folder_1",
            fallbackName = "fallback.json",
            fallbackSizeBytes = 100L
        )
        assertEquals("file_99", uploaded.fileId)
        assertEquals(2048L, uploaded.sizeBytes)

        val listJson = """
            {
              "files": [
                {
                  "id": "drv_1",
                  "name": "PriyoNots_Backup_20261115.json",
                  "size": "4096",
                  "modifiedTime": "2026-11-15T08:00:00Z",
                  "description": "Priyo Nots backup (12 notes)"
                }
              ]
            }
        """.trimIndent()
        val parsedList = GoogleDriveService.parseFilesListJson(listJson)
        assertEquals(1, parsedList.size)
        assertEquals("drv_1", parsedList.first().id)
        assertEquals(4096L, parsedList.first().sizeBytes)
    }

    @Test
    fun `default app settings preconfigure official update repository`() {
        val defaults = com.example.data.local.entity.AppSettingsEntity()
        assertEquals("Itsparvaj/Priyo-Nots", defaults.githubReleaseRepo)
        assertEquals(
            com.example.data.local.entity.AppSettingsEntity.UPDATE_SOURCE_GITHUB,
            defaults.updateSourceType
        )
    }
}
