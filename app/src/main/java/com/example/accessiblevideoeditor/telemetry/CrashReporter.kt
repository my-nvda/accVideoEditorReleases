package com.example.accessiblevideoeditor.telemetry

import android.content.Context
import android.os.Build
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReporter(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveAndUploadCrash(throwable)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Hand over to Android default crash handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveAndUploadCrash(throwable: Throwable) {
        val token = CloudConfigManager.githubToken
        val repo = CloudConfigManager.githubRepo
        val proxyUrl = CloudConfigManager.githubProxyUrl

        // Local cache must always be saved first for robustness
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        // Format Stack Trace
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFilename = "${timestamp}_${androidId}.json"

        val crashObj = JSONObject().apply {
            put("deviceId", androidId)
            put("deviceName", Build.MODEL ?: "Unknown Device")
            put("androidVersion", Build.VERSION.RELEASE ?: "Unknown")
            put("timestamp", System.currentTimeMillis())
            put("errorMessage", throwable.message ?: "No error message")
            put("stackTrace", stackTrace)
        }

        // 1. Save local backup file
        try {
            val crashesDir = java.io.File(context.filesDir, "crashes")
            if (!crashesDir.exists()) {
                crashesDir.mkdirs()
            }
            val localFile = java.io.File(crashesDir, crashFilename)
            localFile.writeText(crashObj.toString(), kotlin.text.Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    companion object {
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is CrashReporter) {
                Thread.setDefaultUncaughtExceptionHandler(CrashReporter(context.applicationContext, defaultHandler))
            }
            
            // Upload cached crashes in background on startup
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    uploadCachedCrashes(context.applicationContext)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private suspend fun uploadCachedCrashes(context: Context) {
            val crashesDir = java.io.File(context.filesDir, "crashes")
            if (!crashesDir.exists() || !crashesDir.isDirectory) {
                return
            }

            val files = crashesDir.listFiles { _, name -> name.endsWith(".json") } ?: return
            if (files.isEmpty()) return

            CloudConfigManager.init(context)
            val token = CloudConfigManager.githubToken
            val repo = CloudConfigManager.githubRepo
            val proxyUrl = CloudConfigManager.githubProxyUrl

            if (proxyUrl.isBlank() && (token.isBlank() || repo.isBlank())) {
                return
            }

            for (file in files) {
                if (file.exists() && file.length() > 0) {
                    try {
                        val content = file.readText(kotlin.text.Charsets.UTF_8)
                        val success = TelemetryManager.uploadToGitHub(
                            repo,
                            token,
                            "crash_reports/${file.name}",
                            content,
                            "Upload cached remote crash report",
                            proxyUrl
                        )
                        if (success) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
