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
        return when {
            mimeType.startsWith("video/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(context, sourceFile, sourceFile.name)
            mimeType.startsWith("audio/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveAudioToGallery(context, sourceFile, sourceFile.name, mimeType)
            mimeType.startsWith("image/") -> com.example.accessiblevideoeditor.media.MediaUtils.saveImageToGallery(context, sourceFile, sourceFile.name)
            else -> null
        }
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
}
