package com.example.accessiblevideoeditor.updater

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.accessiblevideoeditor.ui.CloudConfigManager

class CloudPollWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val result = CloudConfigManager.checkCloudConfig(applicationContext)
            if (result.isSuccess) {
                for (ann in result.pendingAnnouncements) {
                    AppUpdater.showNotification(applicationContext, ann.id.hashCode(), ann.title, ann.message)
                    CloudConfigManager.markAnnouncementAsShown(applicationContext, ann.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
        return Result.success()
    }
}
