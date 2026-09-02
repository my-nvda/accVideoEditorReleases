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
import kotlinx.coroutines.CoroutineScope
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
                        releaseNotes = json.optString("releaseNotes", "").replace("\\n", "\n")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    fun downloadAndInstall(context: Context, updateInfo: UpdateInfo): Long {
        val targetFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AccessibleVideoEditor_Update.apk")
        if (targetFile.exists()) {
            try { targetFile.delete() } catch (_: Exception) {}
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        val uri = Uri.parse(updateInfo.downloadUrl)
        val title = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_202) } catch (_: Exception) { context.getString(R.string.string_202) }
        val desc = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_203, updateInfo.versionName) } catch (_: Exception) { context.getString(R.string.string_203, updateInfo.versionName) }

        if (downloadManager == null) {
            startDirectHttpDownload(context, updateInfo)
            return -1L
        }

        try {
            val request = DownloadManager.Request(uri)
                .setTitle(if (title.isNotBlank()) title else context.getString(R.string.string_213))
                .setDescription(if (desc.isNotBlank()) desc else context.getString(R.string.string_243, updateInfo.versionName))
                .setMimeType("application/vnd.android.package-archive")
                .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addRequestHeader("Accept-Encoding", "identity")
                .addRequestHeader("Connection", "Keep-Alive")
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
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
        } catch (e: Exception) {
            e.printStackTrace()
            startDirectHttpDownload(context, updateInfo)
            return -1L
        }
    }

    private fun startDirectHttpDownload(context: Context, updateInfo: UpdateInfo) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    System.setProperty("java.net.preferIPv4Stack", "true")
                    System.setProperty("java.net.preferIPv6Addresses", "false")
                } catch (_: Exception) {}

                val targetFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AccessibleVideoEditor_Update.apk")
                if (targetFile.exists()) try { targetFile.delete() } catch (_: Exception) {}

                var connUrl = URL(updateInfo.downloadUrl)
                var connection = connUrl.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Connection", "Keep-Alive")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                var redirects = 0
                while ((connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        connection.responseCode == 307 || connection.responseCode == 308) && redirects < 5) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connUrl = URL(newUrl)
                    connection = connUrl.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("Connection", "Keep-Alive")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    redirects++
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    java.io.BufferedInputStream(connection.inputStream, 131072).use { input ->
                        java.io.BufferedOutputStream(targetFile.outputStream(), 131072).use { output ->
                            val buffer = ByteArray(131072) // 128 KB high-speed buffer
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        installFromFile(appContext, targetFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun installFromFile(context: Context, file: File) {
        if (!file.exists()) return
        try {
            val fileUri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                        try { com.example.accessiblevideoeditor.media.SoundManager.playProgressBeep(progress) } catch (_: Exception) {}
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

            val title = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_242) } catch (_: Exception) { context.getString(R.string.string_242) }
            val btnText = try { com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_244) } catch (_: Exception) { context.getString(R.string.btn_download_now) }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (title.isNotBlank()) title else context.getString(R.string.string_242))
                .setContentText(context.getString(R.string.string_243, updateInfo.versionName))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_save,
                    if (btnText.isNotBlank()) btnText else context.getString(R.string.btn_download_now),
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

        try {
            val title = try {
                com.example.accessiblevideoeditor.ui.AppStrings.get(activity, R.string.string_242)
            } catch (_: Exception) { activity.getString(R.string.string_242) }

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
                    activity.getString(R.string.string_243, updateInfo.versionName)
                }
            } catch (_: Exception) {
                activity.getString(R.string.string_243, updateInfo.versionName)
            }

            val fullMessage = if (updateInfo.releaseNotes.isNotBlank()) {
                "$body\n\n${updateInfo.releaseNotes}"
            } else {
                body
            }

            val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
            builder.setTitle(if (title.isNotBlank()) title else activity.getString(R.string.string_242))
            builder.setMessage(fullMessage)
            builder.setPositiveButton(if (btnText.isNotBlank()) btnText else activity.getString(R.string.btn_download_now)) { dialog, _ ->
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
                            
                            val downloadedMb = progress.bytesDownloaded.toDouble() / (1024.0 * 1024.0)
                            val totalMb = progress.totalBytes.toDouble() / (1024.0 * 1024.0)
                            
                            val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                activity.resources.configuration.locales[0]
                            } else {
                                @Suppress("DEPRECATION")
                                activity.resources.configuration.locale
                            }
                            val isArabic = locale.language == "ar"
                            
                            val fmt = com.example.accessiblevideoeditor.ui.AppStrings.get(activity, R.string.updater_progress_mb)
                            val formatLocale = if (isArabic) java.util.Locale("ar") else java.util.Locale.US
                            val detailText = String.format(formatLocale, fmt, downloadedMb, totalMb, percent)
                            messageView.text = detailText
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

    fun showNotification(context: Context, notificationId: Int, title: String, message: String) {
        try {
            val channelId = "app_general_channel"
            val channelName = "General Announcements"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val contentPendingIntent = android.app.PendingIntent.getActivity(context, notificationId, activityIntent, pendingIntentFlags)

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
