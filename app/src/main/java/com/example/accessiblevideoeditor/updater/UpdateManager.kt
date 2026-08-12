package com.example.accessiblevideoeditor.updater

import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.ui.AppStrings
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    // Placeholder URL - you can replace this with your actual server JSON URL
    private const val UPDATE_JSON_URL = "https://example.com/update.json"
    
    // Example JSON structure expected from server:
    // {
    //    "versionCode": 2,
    //    "versionName": "1.1",
    //    "apkUrl": "https://example.com/app-release.apk",
    //    "releaseNotes": "Added new features."
    // }

    /**
     * Checks for an update. Returns the APK download URL if an update is available, null otherwise.
     */
    suspend fun checkForUpdate(currentVersionCode: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                val serverVersionCode = jsonObject.getInt("versionCode")
                
                if (serverVersionCode > currentVersionCode) {
                    return@withContext jsonObject.getString("apkUrl")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Downloads the APK using Android's DownloadManager.
     * The system will handle the download in the background.
     */
    fun downloadUpdate(context: Context, apkUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(AppStrings.get(context, R.string.updater_downloading_title))
                .setDescription(AppStrings.get(context, R.string.updater_desc))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, AppStrings.get(context, R.string.updater_download_started), Toast.LENGTH_LONG).show()
            
            // Note: In a full implementation, you would register a BroadcastReceiver 
            // for DownloadManager.ACTION_DOWNLOAD_COMPLETE to prompt the install intent.
        } catch (e: Exception) {
            Toast.makeText(context, AppStrings.get(context, R.string.updater_download_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
