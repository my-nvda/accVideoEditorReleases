package com.example.accessiblevideoeditor.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object MediaUtils {
    
    /**
     * Copies a local file to the public Movies directory in the MediaStore so the user can see it in their Gallery.
     */
    fun saveVideoToGallery(context: Context, sourceFile: File, outputFileName: String, mimeType: String = "video/mp4"): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AccessibleVideoEditor")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val uri = resolver.insert(collection, contentValues)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
        return uri
    }
    
    /**
     * Copies a local file to the public Music/Audio directory in the MediaStore.
     */
    fun saveAudioToGallery(context: Context, sourceFile: File, outputFileName: String, mimeType: String = "audio/mp4"): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AccessibleVideoEditor")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val uri = resolver.insert(collection, contentValues)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
        return uri
    }
    
    /**
     * Copies a local file to the public Pictures directory in the MediaStore.
     */
    fun saveImageToGallery(context: Context, sourceFile: File, outputFileName: String, mimeType: String = "image/jpeg"): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AccessibleVideoEditor")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        
        val uri = resolver.insert(collection, contentValues)
        
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
        return uri
    }
    
    /**
     * Copies a URI to a temporary local file so FFmpeg can process it reliably via file path.
     */
    fun copyUriToTempFile(context: Context, uri: Uri, tempFileName: String): File? {
        try {
            val tempFile = File(context.cacheDir, tempFileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Copies a URI to a permanent projects directory so we preserve access permanently.
     */
    fun copyUriToProjectsDir(context: Context, uri: Uri, destFileName: String): File? {
        try {
            val projectsDir = File(context.filesDir, "projects")
            if (!projectsDir.exists()) {
                projectsDir.mkdirs()
            }
            val destFile = File(projectsDir, destFileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Replaces the extension of a file path with .mp4
     */
    fun replaceExtensionWithMp4(path: String): String {
        val lastDot = path.lastIndexOf('.')
        if (lastDot == -1) return "$path.mp4"
        return path.substring(0, lastDot) + ".mp4"
    }

    /**
     * Gets the duration of a video in milliseconds using MediaMetadataRetriever.
     */
    fun getVideoDuration(context: Context, uri: Uri): Long {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            return time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return 0L
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
    }

    fun isVideoFile(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)
        if (mime != null && mime.startsWith("video")) return true
        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".mov") || path.endsWith(".webm") || path.endsWith(".3gp")
    }
}
