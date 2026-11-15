package com.example.data.drive

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GoogleDriveFileSummary(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTimeIso: String,
    val description: String
)

data class GoogleDriveUploadResult(
    val fileId: String,
    val folderId: String,
    val fileName: String,
    val sizeBytes: Long,
    val webViewLink: String
)

object GoogleDriveService {

    private const val DRIVE_FILES_API = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
    private const val USERINFO_API = "https://www.googleapis.com/oauth2/v3/userinfo"
    private const val BACKUP_FOLDER_NAME = "Priyo Nots Backups"
    private const val MULTIPART_BOUNDARY = "priyo_nots_drive_boundary_v1"

    suspend fun fetchUserInfoEmail(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        val cleaned = accessToken.trim()
        if (cleaned.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Missing Google access token"))
        }
        runCatching {
            val raw = executeRequest("GET", USERINFO_API, cleaned, null, "application/json")
            parseUserInfoEmail(raw)
        }
    }

    fun parseUserInfoEmail(rawJson: String): String {
        val obj = JSONObject(rawJson)
        return obj.optString("email", "").trim()
            .ifBlank { obj.optString("name", "").trim() }
            .ifBlank { "Google Account" }
    }

    suspend fun ensureBackupFolder(
        accessToken: String,
        existingFolderId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val candidate = existingFolderId?.trim().orEmpty()
            if (candidate.isNotEmpty()) {
                val existingOk = runCatching {
                    val encodedId = URLEncoder.encode(candidate, "UTF-8")
                    val checkRaw = executeRequest(
                        method = "GET",
                        urlStr = "$DRIVE_FILES_API/$encodedId?fields=id,trashed",
                        accessToken = accessToken,
                        body = null,
                        contentType = "application/json"
                    )
                    val obj = JSONObject(checkRaw)
                    !obj.optBoolean("trashed", false) && obj.optString("id").isNotBlank()
                }.getOrDefault(false)
                if (existingOk) return@runCatching candidate
            }

            val query = "mimeType='application/vnd.google-apps.folder' and name='$BACKUP_FOLDER_NAME' and trashed=false"
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val searchRaw = executeRequest(
                method = "GET",
                urlStr = "$DRIVE_FILES_API?q=$encodedQ&spaces=drive&fields=files(id,name)",
                accessToken = accessToken,
                body = null,
                contentType = "application/json"
            )
            val files = JSONObject(searchRaw).optJSONArray("files")
            if (files != null && files.length() > 0) {
                val id = files.optJSONObject(0)?.optString("id", "").orEmpty()
                if (id.isNotBlank()) return@runCatching id
            }

            val createPayload = JSONObject().apply {
                put("name", BACKUP_FOLDER_NAME)
                put("mimeType", "application/vnd.google-apps.folder")
                put("description", "Encrypted and JSON backups created by Priyo Nots")
                put("appProperties", JSONObject().apply {
                    put("priyoNotsApp", "true")
                })
            }
            val createdRaw = executeRequest(
                method = "POST",
                urlStr = "$DRIVE_FILES_API?fields=id,name",
                accessToken = accessToken,
                body = createPayload.toString(),
                contentType = "application/json; charset=UTF-8"
            )
            val createdId = JSONObject(createdRaw).optString("id", "").trim()
            if (createdId.isEmpty()) {
                error("Google Drive did not return a folder ID.")
            }
            createdId
        }
    }

    suspend fun uploadBackup(
        accessToken: String,
        existingFolderId: String?,
        fileName: String,
        content: String,
        noteCount: Int,
        isEncrypted: Boolean
    ): Result<GoogleDriveUploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureBackupFolder(accessToken, existingFolderId).getOrThrow()
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().apply { put(folderId) })
                put("mimeType", "application/json")
                put("description", "Priyo Nots backup ($noteCount notes${if (isEncrypted) ", AES-256 encrypted" else ""})")
                put("appProperties", JSONObject().apply {
                    put("priyoNotsApp", "true")
                    put("encrypted", isEncrypted.toString())
                })
            }

            val multipartBody = buildMultipartBody(
                metadataJson = metadata.toString(),
                contentText = content,
                boundary = MULTIPART_BOUNDARY
            )

            val responseRaw = executeRequest(
                method = "POST",
                urlStr = "$DRIVE_UPLOAD_API?uploadType=multipart&fields=id,name,size,modifiedTime,webViewLink",
                accessToken = accessToken,
                body = multipartBody,
                contentType = "multipart/related; boundary=$MULTIPART_BOUNDARY"
            )
            parseUploadedFileJson(responseRaw, folderId, fileName, content.toByteArray(Charsets.UTF_8).size.toLong())
        }
    }

    fun buildMultipartBody(
        metadataJson: String,
        contentText: String,
        boundary: String = MULTIPART_BOUNDARY
    ): String {
        return buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(contentText).append("\r\n")
            append("--").append(boundary).append("--")
        }
    }

    fun parseUploadedFileJson(
        rawJson: String,
        folderId: String,
        fallbackName: String,
        fallbackSizeBytes: Long
    ): GoogleDriveUploadResult {
        val obj = JSONObject(rawJson)
        val fileId = obj.optString("id", "").trim()
        if (fileId.isEmpty()) error("Upload failed: missing Drive file ID.")
        val name = obj.optString("name", fallbackName).ifBlank { fallbackName }
        val size = obj.optString("size", "").toLongOrNull() ?: fallbackSizeBytes
        val link = obj.optString("webViewLink", "https://drive.google.com/file/d/$fileId/view")
        return GoogleDriveUploadResult(
            fileId = fileId,
            folderId = folderId,
            fileName = name,
            sizeBytes = size,
            webViewLink = link
        )
    }

    suspend fun listBackups(accessToken: String): Result<List<GoogleDriveFileSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "trashed=false and mimeType!='application/vnd.google-apps.folder' and appProperties has { key='priyoNotsApp' and value='true' }"
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val encodedOrder = URLEncoder.encode("modifiedTime desc", "UTF-8")
            val encodedFields = URLEncoder.encode("files(id,name,size,modifiedTime,description)", "UTF-8")
            val raw = executeRequest(
                method = "GET",
                urlStr = "$DRIVE_FILES_API?q=$encodedQ&orderBy=$encodedOrder&pageSize=25&spaces=drive&fields=$encodedFields",
                accessToken = accessToken,
                body = null,
                contentType = "application/json"
            )
            parseFilesListJson(raw)
        }
    }

    fun parseFilesListJson(rawJson: String): List<GoogleDriveFileSummary> {
        val root = JSONObject(rawJson)
        val arr = root.optJSONArray("files") ?: JSONArray()
        val list = mutableListOf<GoogleDriveFileSummary>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty()) continue
            list += GoogleDriveFileSummary(
                id = id,
                name = item.optString("name", "PriyoNots_Backup.json"),
                sizeBytes = item.optString("size", "0").toLongOrNull() ?: 0L,
                modifiedTimeIso = item.optString("modifiedTime", ""),
                description = item.optString("description", "")
            )
        }
        return list
    }

    suspend fun downloadBackupContent(
        accessToken: String,
        fileId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedId = URLEncoder.encode(fileId.trim(), "UTF-8")
            val text = executeRequest(
                method = "GET",
                urlStr = "$DRIVE_FILES_API/$encodedId?alt=media",
                accessToken = accessToken,
                body = null,
                contentType = "application/json"
            )
            if (text.isBlank()) error("Downloaded Google Drive backup file is empty.")
            text
        }
    }

    fun formatDriveError(code: Int, rawErrorBody: String): String {
        val parsedMsg = runCatching {
            val obj = JSONObject(rawErrorBody)
            obj.optJSONObject("error")?.optString("message", "").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401 -> "Google Drive session expired ($code). Tap Connect Google Drive again."
            403 -> "Google Drive permission denied ($code): ${parsedMsg.ifBlank { "Ensure Drive API is enabled and permissions were granted." }}"
            404 -> "Google Drive file or folder not found ($code)."
            else -> parsedMsg.ifBlank { "Google Drive request failed (HTTP $code)" }
        }
    }

    private fun executeRequest(
        method: String,
        urlStr: String,
        accessToken: String,
        body: String?,
        contentType: String
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PriyoNots-Android/${BuildConfig.VERSION_NAME}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        return try {
            if (body != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException(formatDriveError(code, text))
            }
            text
        } finally {
            conn.disconnect()
        }
    }
}
