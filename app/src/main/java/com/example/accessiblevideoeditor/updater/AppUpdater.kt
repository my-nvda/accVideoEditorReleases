package com.example.accessiblevideoeditor.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
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

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://github.com/my-nvda/accVideoEditorReleases/raw/main/update.json?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                
                val serverVersionCode = json.getInt("versionCode")
                
                if (serverVersionCode > BuildConfig.VERSION_CODE) {
                    return@withContext UpdateInfo(
                        versionCode = serverVersionCode,
                        versionName = json.getString("versionName"),
                        downloadUrl = json.getString("downloadUrl"),
                        releaseNotes = json.optString("releaseNotes", com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_201))
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
        val title = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_202)
        val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_203).replace("%s", updateInfo.versionName)
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setDescription(desc)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AccessibleVideoEditor_Update.apk")

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, downloadId)
                    context.unregisterReceiver(this)
                }
            }
        }

        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
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
        val channelId = "app_update_channel"
        val channelName = "App Updates"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new app updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // system icon
            .setContentTitle(context.getString(R.string.string_242))
            .setContentText(context.getString(R.string.string_243, updateInfo.versionName) + " " + context.getString(R.string.string_244))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            notificationManager.notify(1001, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.string_242)) // Update Available
        builder.setMessage(context.getString(R.string.string_243, updateInfo.versionName) + "\n\n" + updateInfo.releaseNotes)
        builder.setPositiveButton(context.getString(R.string.string_244)) { dialog, _ -> // Download
            dialog.dismiss()
            startDownloadWithProgress(context, updateInfo)
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun startDownloadWithProgress(context: Context, updateInfo: UpdateInfo) {
        val downloadId = downloadAndInstall(context, updateInfo)

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val messageView = android.widget.TextView(context).apply {
            text = context.getString(R.string.string_214, 0) // Completed: 0%
            setPadding(0, 0, 0, padding / 2)
            textSize = 16f
        }

        val progressBar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
        }

        container.addView(messageView)
        container.addView(progressBar)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.string_213)) // Downloading update
            .setView(container)
            .setNegativeButton(context.getString(R.string.string_218)) { d, _ -> // Cancel download
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(downloadId)
                d.dismiss()
            }
            .setCancelable(false)
            .create()

        dialog.show()

        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
            ?: (context as? android.content.ContextWrapper)?.baseContext as? androidx.lifecycle.LifecycleOwner

        if (lifecycleOwner != null) {
            lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                observeDownload(context, downloadId).collect { progress ->
                    if (progress.totalBytes > 0) {
                        val percent = ((progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()) * 100).toInt()
                        progressBar.progress = percent
                        messageView.text = context.getString(R.string.string_214, percent)
                    }
                    if (progress.status == DownloadManager.STATUS_SUCCESSFUL || progress.status == DownloadManager.STATUS_FAILED) {
                        dialog.dismiss()
                    }
                }
            }
        }
    }
}
