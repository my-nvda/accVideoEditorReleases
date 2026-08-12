package com.example.accessiblevideoeditor.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TelemetryUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val success = TelemetryManager.uploadTelemetryData(applicationContext)
            if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
