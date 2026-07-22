package com.example.accessiblevideoeditor.updater

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.BuildConfig
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdater {

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val status: Int
    )

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext ?: break
        }
        return null
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/update.json?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                
                val serverVersionCode = json.getInt("versionCode")
                
                if (serverVersionCode > BuildConfig.VERSION_CODE) {
                    return@withContext UpdateInfo(
                        versionCode = serverVersionCode,
                        versionName = json.getString("versionName"),
                        downloadUrl = json.getString("downloadUrl"),
                        releaseNotes = json.optString("releaseNotes", "")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)
        val title = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_202) } catch (_: Exception) { "تنزيل التحديث" }
        val desc = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_203).replace("%s", updateInfo.versionName) } catch (_: Exception) { "الإصدار ${updateInfo.versionName}" }
        
        val request = DownloadManager.Request(uri)
            .setTitle(if (title.isNotBlank()) title else "جاري تنزيل التحديث")
            .setDescription(if (desc.isNotBlank()) desc else "الإصدار ${updateInfo.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AccessibleVideoEditor_Update.apk")

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, downloadId)
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {}

        return downloadId
    }

    fun observeDownload(context: Context, downloadId: Long): Flow<DownloadProgress> = flow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var isDownloading = true
        var lastBeepPercent = 0
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                emit(DownloadProgress(bytesDownloaded, bytesTotal, status))

                if (bytesTotal > 0) {
                    val progress = ((bytesDownloaded.toFloat() / bytesTotal.toFloat()) * 100).toInt()
                    if (progress >= lastBeepPercent + 5 && progress < 100) {
                        lastBeepPercent = progress
                        BeepUtils.playProgressBeep(progress)
                    }
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    isDownloading = false
                }
            } else {
                isDownloading = false
            }
            cursor?.close()
            if (isDownloading) {
                delay(500)
            }
        }
    }

    private fun installApk(context: Context, downloadId: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = downloadManager.getUriForDownloadedFile(downloadId)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(intent)
                return
            }
            
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AccessibleVideoEditor_Update.apk")
            if (file.exists()) {
                val fileUri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(fileUri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        try {
            val channelId = "app_update_channel"
            val channelName = "App Updates"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new app updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val downloadIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("start_download_update", true)
                putExtra("download_url", updateInfo.downloadUrl)
                putExtra("download_version", updateInfo.versionName)
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val contentPendingIntent = android.app.PendingIntent.getActivity(context, 0, activityIntent, pendingIntentFlags)
            val downloadPendingIntent = android.app.PendingIntent.getActivity(context, 1, downloadIntent, pendingIntentFlags)

            val title = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_242) } catch (_: Exception) { "تحديث جديد متوفر" }
            val btnText = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_244) } catch (_: Exception) { "تنزيل الآن" }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (title.isNotBlank()) title else "تحديث جديد متوفر")
                .setContentText("الإصدار ${updateInfo.versionName} متوفر الآن للتنزيل.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    android.R.drawable.stat_sys_download,
                    if (btnText.isNotBlank()) btnText else "تنزيل الآن",
                    downloadPendingIntent
                )
                .setAutoCancel(true)

            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        val activity = getActivity(context) ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        val title = try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(activity, R.string.string_242)
        } catch (_: Exception) { "تحديث جديد متوفر" }

        val msgTemplate = try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(activity, R.string.string_243)
        } catch (_: Exception) { "" }

        val btnText = try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(activity, R.string.string_244)
        } catch (_: Exception) { "تنزيل الآن" }

        val body = try {
            if (msgTemplate.contains("%")) {
                String.format(msgTemplate, updateInfo.versionName)
            } else {
                "الإصدار ${updateInfo.versionName} متوفر الآن للتنزيل."
            }
        } catch (_: Exception) {
            "الإصدار ${updateInfo.versionName} متوفر الآن للتنزيل."
        }

        val fullMessage = if (updateInfo.releaseNotes.isNotBlank()) {
            "$body\n\n${updateInfo.releaseNotes}"
        } else {
            body
        }

        try {
            val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
            builder.setTitle(if (title.isNotBlank()) title else "تحديث جديد متوفر")
            builder.setMessage(fullMessage)
            builder.setPositiveButton(if (btnText.isNotBlank()) btnText else "تنزيل الآن") { dialog, _ ->
                dialog.dismiss()
                startDownloadWithProgress(activity, updateInfo)
            }
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startDownloadWithProgress(context: Context, updateInfo: UpdateInfo) {
        val activity = getActivity(context) ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        try {
            val downloadId = downloadAndInstall(activity, updateInfo)

            val padding = (16 * activity.resources.displayMetrics.density).toInt()
            val container = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }

            val messageView = android.widget.TextView(activity).apply {
                text = activity.getString(R.string.string_214, 0)
                setPadding(0, 0, 0, padding / 2)
                textSize = 16f
            }

            val progressBar = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                isIndeterminate = false
            }

            container.addView(messageView)
            container.addView(progressBar)

            val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.string_213))
                .setView(container)
                .setNegativeButton(activity.getString(R.string.string_218)) { d, _ ->
                    val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    downloadManager.remove(downloadId)
                    d.dismiss()
                }
                .setCancelable(false)
                .create()

            dialog.show()

            val lifecycleOwner = activity as? LifecycleOwner
            if (lifecycleOwner != null) {
                lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    observeDownload(activity, downloadId).collect { progress ->
                        if (progress.totalBytes > 0) {
                            val percent = ((progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()) * 100).toInt()
                            progressBar.progress = percent
                            messageView.text = activity.getString(R.string.string_214, percent)
                        }
                        if (progress.status == DownloadManager.STATUS_SUCCESSFUL || progress.status == DownloadManager.STATUS_FAILED) {
                            try { dialog.dismiss() } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
