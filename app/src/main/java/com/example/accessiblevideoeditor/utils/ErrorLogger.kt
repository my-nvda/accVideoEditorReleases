package com.example.accessiblevideoeditor.utils

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorLogger {
    private const val LOG_FILE_NAME = "error_log.txt"

    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = sdf.format(Date())
            
            val writer = StringWriter()
            throwable?.printStackTrace(PrintWriter(writer))
            val stackTrace = writer.toString()

            val logEntry = """
                =============================================
                [$timestamp] [$tag] $message
                ${if (stackTrace.isNotEmpty()) "Stacktrace:\n$stackTrace" else ""}
                =============================================
                
            """.trimIndent()

            logFile.appendText(logEntry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLogContent(context: Context): String {
        return try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.readText() else "لا توجد أخطاء مسجلة حالياً."
        } catch (e: Exception) {
            "فشل قراءة سجل الأخطاء: ${e.message}"
        }
    }

    fun clearLog(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
