package com.example.accessiblevideoeditor.utils

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.view.View
import android.widget.Toast
import com.example.accessiblevideoeditor.media.SoundManager
import java.io.File

object StorageUtils {

    /**
     * Gets the free space available in the app's cache directory in bytes.
     */
    fun getFreeSpaceBytes(context: Context): Long {
        return try {
            val path = context.cacheDir
            val stats = StatFs(path.absolutePath)
            stats.availableBytes
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: assume 500MB if we fail to get stats
            500 * 1024 * 1024L
        }
    }

    /**
     * Checks if there's enough free space for the required bytes (including a 50MB safety buffer).
     */
    fun isSpaceAvailable(context: Context, requiredBytes: Long): Boolean {
        val freeSpace = getFreeSpaceBytes(context)
        val safetyBuffer = 50 * 1024 * 1024L // 50MB safety margin
        return freeSpace > (requiredBytes + safetyBuffer)
    }

    /**
     * Checks space for a local File with a safety multiplier.
     */
    fun checkSpaceForInputFile(context: Context, file: File, multiplier: Double = 1.5): Boolean {
        val required = (file.length() * multiplier).toLong()
        return isSpaceAvailable(context, required)
    }

    /**
     * Checks space for a given Uri with a safety multiplier.
     */
    fun checkSpaceForUri(context: Context, uri: Uri, multiplier: Double = 1.5): Boolean {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                val size = it.length
                if (size > 0) {
                    return isSpaceAvailable(context, (size * multiplier).toLong())
                }
            }
        } catch (_: Exception) {}
        // Fallback if size cannot be determined: require at least 150MB
        return isSpaceAvailable(context, 150 * 1024 * 1024L)
    }

    /**
     * Shows a localized alert warning the user of low disk space and plays a warning sound.
     */
    fun showLowSpaceWarning(context: Context, view: View? = null) {
        val msg = "تنبيه: مساحة التخزين على الهاتف غير كافية لإتمام هذه العملية. يرجى تحرير بعض المساحة أولاً."
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        view?.announceForAccessibility(msg)
        try {
            // Play a warning sound or error beep if possible
            SoundManager.playError()
        } catch (_: Exception) {}
    }
}
