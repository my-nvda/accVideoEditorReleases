package com.example.accessiblevideoeditor.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object TelemetryManager {

    private const val PREFS_NAME = "TelemetryPrefs"
    private const val PREFIX_CLICK = "click_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun recordFeatureClick(context: Context, featureId: String) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(PREFIX_CLICK + featureId, 0)
        prefs.edit().putInt(PREFIX_CLICK + featureId, current + 1).apply()
    }

    fun getUsageStatistics(context: Context): JSONObject {
        val prefs = getPrefs(context)
        val statsObj = JSONObject()
        
        val keys = prefs.all
        for ((key, value) in keys) {
            if (key.startsWith(PREFIX_CLICK) && value is Int) {
                val featureId = key.substring(PREFIX_CLICK.length)
                statsObj.put(featureId, value)
            }
        }
        return statsObj
    }

    fun clearUsageStatistics(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }

    suspend fun uploadTelemetryData(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = CloudConfigManager.githubToken
        val repo = CloudConfigManager.githubRepo
        val proxyUrl = CloudConfigManager.githubProxyUrl
        
        if (proxyUrl.isBlank() && (token.isBlank() || repo.isBlank())) {
            return@withContext false
        }

        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        // Build telemetry JSON
        val telemetryJson = JSONObject().apply {
            put("deviceId", androidId)
            put("deviceName", Build.MODEL ?: "Unknown Android Device")
            put("androidVersion", Build.VERSION.RELEASE ?: "Unknown")
            put("lastActive", System.currentTimeMillis())
            put("featuresUsage", getUsageStatistics(context))
        }

        val path = "device_stats/$androidId.json"
        return@withContext uploadToGitHub(repo, token, path, telemetryJson.toString(), "Upload usage statistics", proxyUrl)
    }

    suspend fun uploadToGitHub(
        repo: String,
        token: String,
        path: String,
        contentStr: String,
        commitMsg: String,
        proxyUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (!proxyUrl.isNullOrBlank()) {
            return@withContext uploadToProxy(proxyUrl, path, contentStr, commitMsg)
        }
        try {
            val apiUrlStr = "https://api.github.com/repos/$repo/contents/$path"
            
            // 1. Check if file already exists to get SHA
            var sha: String? = null
            var conn: HttpURLConnection? = null
            try {
                val checkUrl = URL(apiUrlStr)
                conn = checkUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "token $token")
                conn.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(response)
                    val shaVal = jsonObj.optString("sha")
                    sha = if (shaVal.isNullOrEmpty()) null else shaVal
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }

            // 2. Perform PUT request to write/update file
            val putUrl = URL(apiUrlStr)
            var putConn: HttpURLConnection? = null
            try {
                putConn = putUrl.openConnection() as HttpURLConnection
                putConn.requestMethod = "PUT"
                putConn.doOutput = true
                putConn.setRequestProperty("Authorization", "token $token")
                putConn.setRequestProperty("Content-Type", "application/json")
                putConn.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")
                putConn.connectTimeout = 10000
                putConn.readTimeout = 10000

                val base64Content = Base64.encodeToString(
                    contentStr.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )

                val bodyObj = JSONObject().apply {
                    put("message", commitMsg)
                    put("content", base64Content)
                    if (sha != null) {
                        put("sha", sha)
                    }
                }

                putConn.outputStream.use { os ->
                    os.write(bodyObj.toString().toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }

                val code = putConn.responseCode
                return@withContext (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED)
            } finally {
                try { putConn?.disconnect() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private suspend fun uploadToProxy(
        proxyUrl: String,
        path: String,
        contentStr: String,
        commitMsg: String
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(proxyUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val base64Content = Base64.encodeToString(
                contentStr.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val bodyObj = JSONObject().apply {
                put("path", path)
                put("content", base64Content)
                put("message", commitMsg)
            }

            conn.outputStream.use { os ->
                os.write(bodyObj.toString().toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            return@withContext (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
