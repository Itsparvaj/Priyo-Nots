package com.example.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateMetadata(
    val versionCode: Int,
    val versionName: String,
    val updateTitle: String,
    val updateMessage: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val sha256: String? = null,
    val hasExplicitVersionCode: Boolean = true
) {
    val isNewerThanInstalled: Boolean
        get() {
            val semVerCmp = com.example.data.github.GitHubService.compareSemanticVersions(
                versionName,
                BuildConfig.VERSION_NAME
            )
            if (semVerCmp != 0) return semVerCmp > 0
            return hasExplicitVersionCode && versionCode > BuildConfig.VERSION_CODE
        }

    val hasDirectApkAsset: Boolean
        get() {
            val trimmed = downloadUrl.trim()
            if (trimmed.isEmpty()) return false
            val cleanPath = trimmed.substringBefore('?').substringBefore('#')
            return cleanPath.endsWith(".apk", ignoreCase = true)
        }
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float, val bytesRead: Long, val totalBytes: Long) : DownloadState
    data class Verified(val apkFile: File, val sha256: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

class UpdateChecker(private val context: Context) {

    suspend fun checkForUpdate(endpointUrl: String): Result<UpdateMetadata?> = withContext(Dispatchers.IO) {
        val trimmed = endpointUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("No update endpoint configured. Set an HTTPS JSON update URL in Settings.")
            )
        }
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                IllegalArgumentException("Update endpoint must use a secure HTTPS URL.")
            )
        }

        runCatching {
            val url = URL(trimmed)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Update server returned HTTP $code")
                }
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val versionCode = json.getInt("versionCode")
                val versionName = json.getString("versionName")
                val downloadUrl = json.optString("downloadUrl", "").trim()
                if (downloadUrl.isNotEmpty() && !downloadUrl.startsWith("https://", ignoreCase = true)) {
                    throw IllegalArgumentException("APK downloadUrl must use HTTPS")
                }
                UpdateMetadata(
                    versionCode = versionCode,
                    versionName = versionName,
                    updateTitle = json.optString("updateTitle", "Update Available"),
                    updateMessage = json.optString("updateMessage", "A newer version of Priyo Nots is ready."),
                    downloadUrl = downloadUrl,
                    releaseNotes = json.optString("releaseNotes", ""),
                    forceUpdate = json.optBoolean("forceUpdate", false),
                    sha256 = json.optString("sha256", "").takeIf { it.isNotBlank() }
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    fun downloadAndVerifyApk(metadata: UpdateMetadata): Flow<DownloadState> = flow {
        val urlString = metadata.downloadUrl.trim()
        if (!urlString.startsWith("https://", ignoreCase = true)) {
            emit(DownloadState.Failed("Invalid or non-HTTPS APK download URL"))
            return@flow
        }

        val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
        val tempFile = File(updatesDir, "PriyoNots_v${metadata.versionName}.apk.part")
        val targetFile = File(updatesDir, "PriyoNots_v${metadata.versionName}.apk")

        var connection: HttpURLConnection? = null
        try {
            emit(DownloadState.Downloading(0f, 0L, -1L))
            connection = openDownloadConnectionWithRedirects(urlString)
            val code = connection.responseCode
            if (code !in 200..299) {
                emit(DownloadState.Failed("Download failed with HTTP $code"))
                return@flow
            }
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        emit(DownloadState.Downloading(progress, downloaded, total))
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Verify APK magic bytes (PK zip header) & Android package archive info
            val isZip = targetFile.inputStream().use { ins ->
                val header = ByteArray(2)
                ins.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            }
            if (!isZip) {
                targetFile.delete()
                emit(DownloadState.Failed("Downloaded file is not a valid APK package"))
                return@flow
            }

            val computedSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!metadata.sha256.isNullOrBlank() && !computedSha.equals(metadata.sha256, ignoreCase = true)) {
                targetFile.delete()
                emit(DownloadState.Failed("APK checksum verification failed"))
                return@flow
            }

            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    targetFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
            }
            if (pkgInfo == null || pkgInfo.packageName != context.packageName) {
                targetFile.delete()
                emit(DownloadState.Failed("APK package signature/ID does not match Priyo Nots"))
                return@flow
            }

            emit(DownloadState.Verified(targetFile, computedSha))
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Failed(e.message ?: "APK download failed"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildInstallSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchSystemPackageInstaller(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun openDownloadConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0 until 6) {
            if (!currentUrl.startsWith("https://", ignoreCase = true)) {
                throw IllegalArgumentException("Update download redirected to non-HTTPS URL")
            }
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                setRequestProperty("User-Agent", "PriyoNots-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location").orEmpty().trim()
                conn.disconnect()
                if (location.isEmpty()) {
                    throw IllegalStateException("Redirect location missing (HTTP $code)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            return conn
        }
        throw IllegalStateException("Too many redirects while downloading APK")
    }
}
