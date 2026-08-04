package com.gamevault.app.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * File helpers for user-picked and downloaded cover images.
 * Custom covers live in the app's private files dir (device-local, not backed
 * up); downloads go to the shared Downloads/GameVault folder on API 29+.
 */

/**
 * Copies a user-picked image into filesDir/covers/{gameId}.<ext> and returns
 * its absolute path, or null on failure. Any previous custom cover for the
 * same game is removed first (the extension may have changed).
 */
suspend fun savePickedImage(context: Context, uri: Uri, gameId: Long): String? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "covers")
            dir.mkdirs()

            dir.listFiles()?.filter { it.name.startsWith("${gameId}.") }?.forEach { it.delete() }

            val ext = mimeToExt(context.contentResolver.getType(uri))
            val dest = File(dir, "$gameId.$ext")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open picked image")
            input.use { ins ->
                dest.outputStream().use { outs -> ins.copyTo(outs) }
            }
            dest.absolutePath
        }.getOrNull()
    }
}

/**
 * Downloads the cover (http(s) URL or local file path) to the device.
 * Returns a user-facing message describing the outcome: "Saved to Downloads"
 * (API 29+, shared MediaStore collection) or "Saved to app folder" (API < 29,
 * app-specific pictures dir). Failures return an error message.
 */
suspend fun downloadCoverToDevice(context: Context, source: String, gameTitle: String): String {
    return withContext(Dispatchers.IO) {
        runCatching {
            val ext = sourceExt(source)
            val mimeType = extToMime(ext)
            val displayName = "GameVault - ${sanitizeFileName(gameTitle)}.$ext"
            val bytes = readSourceBytes(source)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/GameVault",
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val entry = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create download entry")
                try {
                    resolver.openOutputStream(entry)?.use { it.write(bytes) }
                        ?: throw IOException("Could not open download output")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(entry, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(entry, null, null)
                    throw e
                }
                "Saved to Downloads"
            } else {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: throw IOException("External files dir unavailable")
                val dest = File(picturesDir, displayName)
                dest.writeBytes(bytes)
                "Saved to app folder"
            }
        }.getOrElse { e -> "Download failed: ${e.message ?: "unknown error"}" }
    }
}

private fun readSourceBytes(source: String): ByteArray {
    return if (source.startsWith("http://") || source.startsWith("https://")) {
        val connection = URL(source).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Server returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    } else {
        File(source).inputStream().use { it.readBytes() }
    }
}

private fun mimeToExt(mime: String?): String {
    if (mime == null) return "jpg"
    return when (mime.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> mime.substringAfterLast('/', "jpg").takeIf { it.length <= 4 } ?: "jpg"
    }
}

private fun sourceExt(source: String): String {
    val path = source.substringBefore('?')
    val ext = path.substringAfterLast('.', "").lowercase()
    return if (ext.isNotBlank() && ext.length <= 4 && ext.all { it.isLetterOrDigit() }) {
        ext
    } else {
        "jpg"
    }
}

private fun extToMime(ext: String): String = when (ext) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    else -> "image/jpeg"
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(60)
        .ifBlank { "cover" }
