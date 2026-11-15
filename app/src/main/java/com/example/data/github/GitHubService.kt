package com.example.data.github

import android.util.Base64
import com.example.BuildConfig
import com.example.data.update.UpdateMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubUser(
    val login: String,
    val displayName: String
)

data class GitHubGistSummary(
    val id: String,
    val description: String,
    val fileName: String,
    val sizeBytes: Long,
    val updatedAtIso: String
)

data class GitHubCloudBackupResult(
    val targetId: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long
)

object GitHubService {

    private const val API_BASE = "https://api.github.com"

    /**
     * Normalizes "owner/repo" or "https://github.com/owner/repo(.git)" into "owner/repo".
     */
    fun normalizeRepoSlug(input: String): String? {
        val trimmed = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = trimmed.split('/')
        if (parts.size != 2) return null
        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val validPart = Regex("^[A-Za-z0-9_.-]+$")
        return if (validPart.matches(owner) && validPart.matches(repo)) "$owner/$repo" else null
    }

    suspend fun verifyToken(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        val cleaned = token.trim()
        if (cleaned.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("GitHub token cannot be empty"))
        }
        runCatching {
            val response = requestJson("GET", "$API_BASE/user", cleaned, null)
            val obj = JSONObject(response)
            val login = obj.getString("login")
            val name = obj.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: login
            GitHubUser(login = login, displayName = name)
        }
    }

    suspend fun fetchLatestRelease(
        repoInput: String,
        optionalToken: String? = null
    ): Result<UpdateMetadata?> = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(repoInput)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid GitHub repository format. Use owner/repo (e.g., mohammadparvaj/priyo-nots).")
            )
        runCatching {
            val response = requestJson(
                method = "GET",
                urlStr = "$API_BASE/repos/$slug/releases/latest",
                token = optionalToken?.takeIf { it.isNotBlank() },
                bodyJson = null
            )
            parseReleaseJson(response)
        }
    }

    fun parseReleaseJson(rawJson: String): UpdateMetadata {
        val obj = JSONObject(rawJson)
        val tagName = obj.optString("tag_name", "").trim()
        val cleanVersion = tagName.removePrefix("v").removePrefix("V").trim()
        val releaseName = obj.optString("name", "").ifBlank { "Priyo Nots $tagName" }
        val body = obj.optString("body", "")
        val htmlUrl = obj.optString("html_url", "")

        var apkUrl = ""
        var sha256: String? = null
        val assets = obj.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val dl = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && dl.startsWith("https://", ignoreCase = true)) {
                apkUrl = dl
                val digest = asset.optString("digest", "")
                if (digest.startsWith("sha256:", ignoreCase = true)) {
                    sha256 = digest.substringAfter("sha256:").trim()
                }
                break
            }
        }

        val parsedCode = deriveVersionCode(cleanVersion)
        val forceUpdate = body.contains("[force-update]", ignoreCase = true)

        return UpdateMetadata(
            versionCode = parsedCode,
            versionName = cleanVersion.ifBlank { tagName },
            updateTitle = releaseName,
            updateMessage = if (apkUrl.isNotBlank()) {
                "New release $tagName is available on GitHub Releases."
            } else {
                "Release $tagName found on GitHub (no .apk asset attached; view release notes)."
            },
            downloadUrl = apkUrl.ifBlank { htmlUrl },
            releaseNotes = body,
            forceUpdate = forceUpdate,
            sha256 = sha256
        )
    }

    fun compareSemanticVersions(remote: String, local: String): Int {
        val rParts = remote.removePrefix("v").removePrefix("V").split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val lParts = local.removePrefix("v").removePrefix("V").split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    private fun deriveVersionCode(versionName: String): Int {
        val parts = versionName.split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return BuildConfig.VERSION_CODE
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return (major * 10_000) + (minor * 100) + patch
    }

    suspend fun uploadBackupToGist(
        token: String,
        existingGistId: String?,
        fileName: String,
        content: String,
        noteCount: Int
    ): Result<GitHubCloudBackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val filesObj = JSONObject().apply {
                put(fileName, JSONObject().put("content", content))
            }
            val payload = JSONObject().apply {
                put("description", "Priyo Nots Backup ($noteCount notes) — Owner: Mohammad Parvaj")
                put("public", false)
                put("files", filesObj)
            }

            val cleanGistId = existingGistId?.trim().orEmpty()
            val response = if (cleanGistId.isNotEmpty()) {
                runCatching {
                    requestJson("PATCH", "$API_BASE/gists/$cleanGistId", token, payload.toString())
                }.getOrElse {
                    // Fallback to creating a fresh secret Gist if previous Gist was deleted on GitHub
                    requestJson("POST", "$API_BASE/gists", token, payload.toString())
                }
            } else {
                requestJson("POST", "$API_BASE/gists", token, payload.toString())
            }

            val resObj = JSONObject(response)
            GitHubCloudBackupResult(
                targetId = resObj.getString("id"),
                url = resObj.optString("html_url", ""),
                fileName = fileName,
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
            )
        }
    }

    suspend fun listPriyoNotsGists(token: String): Result<List<GitHubGistSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = requestJson("GET", "$API_BASE/gists?per_page=40", token, null)
            val arr = JSONArray(raw)
            val list = mutableListOf<GitHubGistSummary>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val files = obj.optJSONObject("files") ?: continue
                val keys = files.keys()
                var matchedName: String? = null
                var matchedSize = 0L
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.contains("priyo", ignoreCase = true) ||
                        key.endsWith(".json", ignoreCase = true) ||
                        key.endsWith(".pnbak", ignoreCase = true)
                    ) {
                        matchedName = key
                        matchedSize = files.optJSONObject(key)?.optLong("size", 0L) ?: 0L
                        break
                    }
                }
                if (matchedName != null) {
                    list.add(
                        GitHubGistSummary(
                            id = obj.getString("id"),
                            description = obj.optString("description", "").ifBlank { "Priyo Nots Gist" },
                            fileName = matchedName,
                            sizeBytes = matchedSize,
                            updatedAtIso = obj.optString("updated_at", "")
                        )
                    )
                }
            }
            list
        }
    }

    suspend fun fetchGistContent(token: String, gistId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = requestJson("GET", "$API_BASE/gists/${gistId.trim()}", token, null)
            val obj = JSONObject(raw)
            val files = obj.getJSONObject("files")
            val keys = files.keys()
            var chosenObj: JSONObject? = null
            while (keys.hasNext()) {
                val key = keys.next()
                val fObj = files.optJSONObject(key) ?: continue
                if (key.contains("priyo", ignoreCase = true) || chosenObj == null) {
                    chosenObj = fObj
                    if (key.contains("priyo", ignoreCase = true)) break
                }
            }
            val fileObj = chosenObj ?: throw IllegalStateException("No backup file found inside Gist")
            if (!fileObj.optBoolean("truncated", false)) {
                fileObj.getString("content")
            } else {
                val rawUrl = fileObj.getString("raw_url")
                requestRawUrl(rawUrl, token)
            }
        }
    }

    suspend fun uploadBackupToRepo(
        token: String,
        repoInput: String,
        branch: String,
        filePath: String,
        content: String
    ): Result<GitHubCloudBackupResult> = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(repoInput)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid repository format. Use owner/repo."))
        val cleanPath = filePath.trim().trimStart('/')
        if (cleanPath.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Repository file path cannot be empty."))
        }
        val cleanBranch = branch.trim().ifBlank { "main" }

        runCatching {
            val encodedPath = cleanPath.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val endpoint = "$API_BASE/repos/$slug/contents/$encodedPath"
            val existingSha = runCatching {
                val existingJson = requestJson(
                    "GET",
                    "$endpoint?ref=${URLEncoder.encode(cleanBranch, "UTF-8")}",
                    token,
                    null
                )
                JSONObject(existingJson).optString("sha", "").takeIf { it.isNotBlank() }
            }.getOrNull()

            val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("message", "Priyo Nots backup sync")
                put("content", encodedContent)
                put("branch", cleanBranch)
                if (existingSha != null) {
                    put("sha", existingSha)
                }
            }

            val response = requestJson("PUT", endpoint, token, payload.toString())
            val resObj = JSONObject(response)
            val contentObj = resObj.optJSONObject("content")
            GitHubCloudBackupResult(
                targetId = contentObj?.optString("sha", "") ?: slug,
                url = contentObj?.optString("html_url", "") ?: "https://github.com/$slug",
                fileName = cleanPath.substringAfterLast('/'),
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
            )
        }
    }

    suspend fun fetchRepoFileContent(
        token: String,
        repoInput: String,
        branch: String,
        filePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(repoInput)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid repository format. Use owner/repo."))
        val cleanPath = filePath.trim().trimStart('/')
        val cleanBranch = branch.trim().ifBlank { "main" }

        runCatching {
            val encodedPath = cleanPath.split('/').joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val url = "$API_BASE/repos/$slug/contents/$encodedPath?ref=${URLEncoder.encode(cleanBranch, "UTF-8")}"
            val response = requestJson("GET", url, token, null)
            val obj = JSONObject(response)
            val encoded = obj.optString("content", "").replace("\n", "").replace("\r", "")
            if (encoded.isNotEmpty()) {
                String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            } else {
                val dlUrl = obj.getString("download_url")
                requestRawUrl(dlUrl, token)
            }
        }
    }

    private fun requestJson(
        method: String,
        urlStr: String,
        token: String?,
        bodyJson: String?
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PriyoNots-Android/${BuildConfig.VERSION_NAME}")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            if (bodyJson != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

        return try {
            if (bodyJson != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val apiMsg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw IllegalStateException("GitHub API error ($code): ${apiMsg?.ifBlank { null } ?: text.take(160)}")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun requestRawUrl(urlStr: String, token: String?): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "PriyoNots-Android/${BuildConfig.VERSION_NAME}")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Failed to download content from GitHub (HTTP $code)")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
