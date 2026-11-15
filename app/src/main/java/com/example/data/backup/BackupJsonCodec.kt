package com.example.data.backup

import com.example.data.local.entity.AppSettingsEntity
import com.example.data.local.entity.CategoriesEntity
import com.example.data.local.entity.NotesEntity
import com.example.data.local.entity.RemindersEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ParsedBackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val categories: List<CategoriesEntity>,
    val notes: List<NotesEntity>,
    val reminders: List<RemindersEntity>,
    val settings: AppSettingsEntity?,
    val noteSyncKeysByOldId: Map<Long, String>,
    val categorySyncKeysByOldId: Map<Long, String>,
    val reminderNoteSyncKeys: Map<String, String>
)

object BackupJsonCodec {

    const val CURRENT_SCHEMA_VERSION = 1
    private const val APP_IDENTIFIER = "Priyo Nots"

    fun encodeFullBackup(
        notes: List<NotesEntity>,
        categories: List<CategoriesEntity>,
        reminders: List<RemindersEntity>,
        settings: AppSettingsEntity?
    ): String {
        val categorySyncById = categories.associate { it.id to it.syncKey }
        val noteSyncById = notes.associate { it.id to it.syncKey }

        val root = JSONObject().apply {
            put("app", APP_IDENTIFIER)
            put("owner", "Mohammad Parvaj")
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("encrypted", false)

            put("categories", JSONArray().apply {
                categories.forEach { cat ->
                    put(JSONObject().apply {
                        put("id", cat.id)
                        put("syncKey", cat.syncKey)
                        put("name", cat.name)
                        put("colorHex", cat.colorHex)
                        put("iconName", cat.iconName)
                        put("createdAt", cat.createdAt)
                    })
                }
            })

            put("notes", JSONArray().apply {
                notes.forEach { note ->
                    put(JSONObject().apply {
                        put("id", note.id)
                        put("syncKey", note.syncKey)
                        put("title", note.title)
                        put("contentHtml", note.contentHtml)
                        put("plainText", note.plainText)
                        put("colorKey", note.colorKey)
                        put("categoryId", note.categoryId ?: JSONObject.NULL)
                        put("categorySyncKey", note.categoryId?.let { categorySyncById[it] } ?: JSONObject.NULL)
                        put("isPinned", note.isPinned)
                        put("isArchived", note.isArchived)
                        put("archivedAt", note.archivedAt ?: JSONObject.NULL)
                        put("isTrashed", note.isTrashed)
                        put("trashedAt", note.trashedAt ?: JSONObject.NULL)
                        put("wordCount", note.wordCount)
                        put("createdAt", note.createdAt)
                        put("updatedAt", note.updatedAt)
                    })
                }
            })

            put("reminders", JSONArray().apply {
                reminders.forEach { rem ->
                    put(JSONObject().apply {
                        put("id", rem.id)
                        put("syncKey", rem.syncKey)
                        put("noteId", rem.noteId)
                        put("noteSyncKey", noteSyncById[rem.noteId] ?: JSONObject.NULL)
                        put("triggerAtMillis", rem.triggerAtMillis)
                        put("repeatType", rem.repeatType)
                        put("isActive", rem.isActive)
                        put("requestCode", rem.requestCode)
                        put("createdAt", rem.createdAt)
                        put("updatedAt", rem.updatedAt)
                    })
                }
            })

            if (settings != null) {
                put("settings", JSONObject().apply {
                    put("themeMode", settings.themeMode)
                    put("defaultSortOrder", settings.defaultSortOrder)
                    put("isGridView", settings.isGridView)
                    put("autoBackupEnabled", settings.autoBackupEnabled)
                    put("backupFrequencyHours", settings.backupFrequencyHours)
                    put("notificationsEnabled", settings.notificationsEnabled)
                    put("reminderSoundEnabled", settings.reminderSoundEnabled)
                    put("reminderVibrateEnabled", settings.reminderVibrateEnabled)
                    put("updateCheckEnabled", settings.updateCheckEnabled)
                    put("updateEndpointUrl", settings.updateEndpointUrl)
                    put("contactEmail", settings.contactEmail)
                    put("contactWebsite", settings.contactWebsite)
                    put("privacyModeHidePreviews", settings.privacyModeHidePreviews)
                })
            }
        }
        return root.toString(2)
    }

    fun decodeAndValidate(rawJson: String): ParsedBackupPayload {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("Invalid backup file: root must be a JSON object")
        }
        val root = JSONObject(trimmed)
        val appName = root.optString("app", "")
        if (appName.isNotEmpty() && !appName.equals(APP_IDENTIFIER, ignoreCase = true)) {
            throw IllegalArgumentException("Backup file does not belong to Priyo Nots")
        }
        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion <= 0 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }
        if (!root.has("notes") || root.optJSONArray("notes") == null) {
            throw IllegalArgumentException("Backup file is missing the notes array")
        }

        val categoriesList = mutableListOf<CategoriesEntity>()
        val categorySyncByOldId = mutableMapOf<Long, String>()
        val catArray = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArray.length()) {
            val obj = catArray.optJSONObject(i) ?: continue
            val oldId = obj.optLong("id", 0L)
            val syncKey = obj.optString("syncKey").ifBlank { UUID.randomUUID().toString() }
            val name = obj.optString("name").trim().take(60)
            if (name.isEmpty()) continue
            val colorHex = sanitizeHexColor(obj.optString("colorHex", "#4F1ED8"))
            val iconName = obj.optString("iconName", "folder").take(32)
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            categoriesList.add(
                CategoriesEntity(
                    id = oldId,
                    syncKey = syncKey,
                    name = name,
                    colorHex = colorHex,
                    iconName = iconName,
                    createdAt = createdAt
                )
            )
            if (oldId > 0L) {
                categorySyncByOldId[oldId] = syncKey
            }
        }

        val notesList = mutableListOf<NotesEntity>()
        val noteSyncByOldId = mutableMapOf<Long, String>()
        val notesArray = root.getJSONArray("notes")
        for (i in 0 until notesArray.length()) {
            val obj = notesArray.optJSONObject(i) ?: continue
            val oldId = obj.optLong("id", 0L)
            val syncKey = obj.optString("syncKey").ifBlank { UUID.randomUUID().toString() }
            val title = obj.optString("title", "").take(300)
            val contentHtml = sanitizeHtmlBasic(obj.optString("contentHtml", ""))
            val plainText = obj.optString("plainText", "")
            val colorKey = obj.optString("colorKey", "default").take(24)
            val oldCatId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId", 0L).takeIf { it > 0L }
            val isPinned = obj.optBoolean("isPinned", false)
            val isArchived = obj.optBoolean("isArchived", false)
            val archivedAt = if (obj.isNull("archivedAt")) null else obj.optLong("archivedAt", 0L).takeIf { it > 0L }
            val isTrashed = obj.optBoolean("isTrashed", false)
            val trashedAt = if (obj.isNull("trashedAt")) null else obj.optLong("trashedAt", 0L).takeIf { it > 0L }
            val wordCount = obj.optInt("wordCount", plainText.trim().split(Regex("\\s+")).count { it.isNotEmpty() })
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = obj.optLong("updatedAt", createdAt)

            notesList.add(
                NotesEntity(
                    id = oldId,
                    syncKey = syncKey,
                    title = title,
                    contentHtml = contentHtml,
                    plainText = plainText,
                    colorKey = colorKey,
                    categoryId = oldCatId,
                    isPinned = isPinned,
                    isArchived = isArchived,
                    archivedAt = archivedAt,
                    isTrashed = isTrashed,
                    trashedAt = trashedAt,
                    wordCount = wordCount,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            )
            if (oldId > 0L) {
                noteSyncByOldId[oldId] = syncKey
            }
        }

        val remindersList = mutableListOf<RemindersEntity>()
        val reminderNoteSyncKeys = mutableMapOf<String, String>()
        val remArray = root.optJSONArray("reminders") ?: JSONArray()
        for (i in 0 until remArray.length()) {
            val obj = remArray.optJSONObject(i) ?: continue
            val syncKey = obj.optString("syncKey").ifBlank { UUID.randomUUID().toString() }
            val noteId = obj.optLong("noteId", 0L)
            val noteSyncKey = obj.optString("noteSyncKey", "")
                .ifBlank { noteSyncByOldId[noteId].orEmpty() }
            if (noteSyncKey.isBlank()) continue

            val triggerAt = obj.optLong("triggerAtMillis", 0L)
            if (triggerAt <= 0L) continue
            val repeatType = obj.optString("repeatType", RemindersEntity.REPEAT_NONE)
            val isActive = obj.optBoolean("isActive", true)
            val requestCode = obj.optInt("requestCode", (System.nanoTime() and 0x7FFFFFFF).toInt())
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = obj.optLong("updatedAt", createdAt)

            remindersList.add(
                RemindersEntity(
                    id = 0L,
                    syncKey = syncKey,
                    noteId = noteId,
                    triggerAtMillis = triggerAt,
                    repeatType = repeatType,
                    isActive = isActive,
                    requestCode = requestCode,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            )
            reminderNoteSyncKeys[syncKey] = noteSyncKey
        }

        val settingsObj = root.optJSONObject("settings")?.let { s ->
            AppSettingsEntity(
                id = 1,
                themeMode = s.optString("themeMode", AppSettingsEntity.THEME_SYSTEM),
                defaultSortOrder = s.optString("defaultSortOrder", AppSettingsEntity.SORT_MODIFIED_DESC),
                isGridView = s.optBoolean("isGridView", true),
                autoBackupEnabled = s.optBoolean("autoBackupEnabled", false),
                backupFrequencyHours = s.optInt("backupFrequencyHours", 24).coerceIn(6, 168),
                notificationsEnabled = s.optBoolean("notificationsEnabled", true),
                reminderSoundEnabled = s.optBoolean("reminderSoundEnabled", true),
                reminderVibrateEnabled = s.optBoolean("reminderVibrateEnabled", true),
                updateCheckEnabled = s.optBoolean("updateCheckEnabled", true),
                updateEndpointUrl = sanitizeHttpsUrl(s.optString("updateEndpointUrl", "")),
                contactEmail = s.optString("contactEmail", "").take(120),
                contactWebsite = s.optString("contactWebsite", "").take(200),
                privacyModeHidePreviews = s.optBoolean("privacyModeHidePreviews", false)
            )
        }

        return ParsedBackupPayload(
            schemaVersion = schemaVersion,
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            categories = categoriesList,
            notes = notesList,
            reminders = remindersList,
            settings = settingsObj,
            noteSyncKeysByOldId = noteSyncByOldId,
            categorySyncKeysByOldId = categorySyncByOldId,
            reminderNoteSyncKeys = reminderNoteSyncKeys
        )
    }

    private fun sanitizeHexColor(hex: String): String {
        val cleaned = hex.trim()
        return if (Regex("^#[0-9A-Fa-f]{6}$").matches(cleaned)) cleaned else "#4F1ED8"
    }

    private fun sanitizeHttpsUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("https://", ignoreCase = true)) trimmed.take(300) else ""
    }

    fun sanitizeHtmlBasic(raw: String): String {
        return raw
            .replace(Regex("(?is)<script.*?>.*?</script>"), "")
            .replace(Regex("(?is)<iframe.*?>.*?</iframe>"), "")
            .replace(Regex("(?is)<object.*?>.*?</object>"), "")
            .replace(Regex("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\""), "")
            .replace(Regex("(?i)\\son\\w+\\s*=\\s*'[^']*'"), "")
            .replace(Regex("(?i)javascript:"), "")
    }
}
