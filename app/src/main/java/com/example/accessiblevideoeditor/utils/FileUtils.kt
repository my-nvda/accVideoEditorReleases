package com.example.accessiblevideoeditor.utils

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        val ext = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "tmp"
        val tempFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "temp_${System.currentTimeMillis()}.$ext")
        return tempFile?.absolutePath
    }

    fun saveToGallery(context: Context, sourceFile: File, mimeType: String): Uri? {
        val uri = when {
            mimeType.startsWith("video/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(context, sourceFile, sourceFile.name, mimeType)
            mimeType.startsWith("audio/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveAudioToGallery(context, sourceFile, sourceFile.name, mimeType)
            mimeType.startsWith("image/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveImageToGallery(context, sourceFile, sourceFile.name, mimeType)
            else -> null
        }
        if (uri != null) {
            val type = when {
                mimeType.startsWith("audio/") -> "audio"
                mimeType.startsWith("image/") -> "image"
                else -> "video"
            }
            com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                context,
                com.example.accessiblevideoeditor.media.HistoryItem(
                    uriString = uri.toString(),
                    name = sourceFile.name,
                    timestamp = System.currentTimeMillis(),
                    type = type
                )
            )
        }
        return uri
    }

    fun copyFontToCache(context: Context): String {
        val fontFileName = "cairo.ttf" // Must match a font in assets, or we create a dummy one
        val cacheFile = File(context.cacheDir, fontFileName)
        if (!cacheFile.exists()) {
            try {
                context.assets.open(fontFileName).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // If cairo.ttf does not exist, we just return a default system font path
                return "/system/fonts/DroidSansFallback.ttf"
            }
        }
        return cacheFile.absolutePath
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
