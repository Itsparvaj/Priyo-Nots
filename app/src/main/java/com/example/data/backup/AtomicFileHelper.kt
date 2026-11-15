package com.example.data.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AtomicFileHelper {

    private const val MAX_IMPORT_BYTES = 25L * 1024L * 1024L // 25 MB safety limit

    fun getPriyoNotsRootDir(context: Context): File {
        return File(context.filesDir, "PriyoNots").apply { if (!exists()) mkdirs() }
    }

    fun getBackupsDir(context: Context): File {
        return File(getPriyoNotsRootDir(context), "backups").apply { if (!exists()) mkdirs() }
    }

    fun getExportsDir(context: Context): File {
        return File(getPriyoNotsRootDir(context), "exports").apply { if (!exists()) mkdirs() }
    }

    /**
     * Writes content using a temporary file, flushes and fsyncs to disk, then atomically renames
     * to prevent data corruption on interrupted writes.
     */
    @Throws(IOException::class)
    fun writeTextAtomically(targetFile: File, content: String) {
        val parent = targetFile.parentFile ?: throw IOException("Target parent directory missing")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create directory: ${parent.absolutePath}")
        }
        val tempFile = File(parent, ".${targetFile.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (targetFile.exists() && !targetFile.delete()) {
                // Attempt direct rename overwrite on POSIX systems
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw if (t is IOException) t else IOException("Atomic write failed", t)
        }
    }

    @Throws(IOException::class)
    fun writeTextToUri(context: Context, uri: Uri, content: String): Long {
        val bytes = content.toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IOException("Unable to open destination URI for writing")
        return bytes.size.toLong()
    }

    @Throws(IOException::class)
    fun readTextFromUri(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            if (afd.length > MAX_IMPORT_BYTES) {
                throw IOException("Backup file exceeds maximum allowed size (25 MB)")
            }
        }
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_IMPORT_BYTES) {
                    throw IOException("Backup file exceeds maximum allowed size (25 MB)")
                }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        } ?: throw IOException("Unable to open selected file")
    }
}
