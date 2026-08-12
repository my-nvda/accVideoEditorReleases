package com.example.accessiblevideoeditor.telemetry

import android.content.Context
import android.os.Build
import com.example.accessiblevideoeditor.ui.CloudConfigManager
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

        if (token.isBlank() || repo.isBlank()) {
            return
        }

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

        // Run blockingly since app is crashing/terminating
        runBlocking {
            TelemetryManager.uploadToGitHub(
                repo,
                token,
                "crash_reports/$crashFilename",
                crashObj.toString(),
                "Upload remote crash report"
            )
        }
    }

    companion object {
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is CrashReporter) {
                Thread.setDefaultUncaughtExceptionHandler(CrashReporter(context.applicationContext, defaultHandler))
            }
        }
    }
}
