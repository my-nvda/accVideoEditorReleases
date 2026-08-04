package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.content.SharedPreferences
import com.example.accessiblevideoeditor.ui.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

import java.io.File

data class DynamicFeatureItem(
    val id: String,
    val featureId: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
    val version: Int
)

object CloudConfigManager {

    private const val CONFIG_URL = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/cloud_config.json"
    private const val STRINGS_PATCH_URL = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/strings_patch.json"
    private const val PREFS_NAME = "CloudConfigPrefs"
    private const val KEY_DISABLED_SET = "disabled_features_set"
    private const val KEY_DOWNLOADED_FEATURES = "downloaded_features_set"
    private const val KEY_STRINGS_VERSION = "strings_patch_version"

    // Set to true by checkCloudConfig when new translations were saved — read by HomeFragment to call recreate()
    @Volatile var stringsUpdated = false

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    suspend fun checkCloudConfig(context: Context): CloudConfigResult = withContext(Dispatchers.IO) {
        init(context)
        val result = CloudConfigResult()
        try {
            val url = URL("$CONFIG_URL?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }

                // Fetch Strings Patch in background
                try {
                    val stringsUrl = URL("$STRINGS_PATCH_URL?t=${System.currentTimeMillis()}")
                    val stringsConn = stringsUrl.openConnection() as HttpURLConnection
                    stringsConn.useCaches = false
                    stringsConn.instanceFollowRedirects = true
                    stringsConn.connectTimeout = 8000
                    stringsConn.readTimeout = 8000
                    stringsConn.requestMethod = "GET"
                    stringsConn.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")
                    if (stringsConn.responseCode == HttpURLConnection.HTTP_OK) {
                        val stringsJsonStr = stringsConn.inputStream.bufferedReader().use { it.readText() }
                        val map = mutableMapOf<String, String>()
                        var serverVersion = -1

                        try {
                            val stringsRoot = JSONObject(stringsJsonStr)
                            // Read version field for comparison
                            serverVersion = stringsRoot.optInt("version", -1)
                            if (stringsRoot.has("strings")) {
                                val stringsObj = stringsRoot.getJSONObject("strings")
                                for (key in stringsObj.keys()) {
                                    map[key] = stringsObj.getString(key)
                                }
                            }
                        } catch (je: Exception) {
                            // Robust line/pair level fallback if JSON has syntax errors like missing commas
                            val itemRegex = """"([^"\\]+)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
                            itemRegex.findAll(stringsJsonStr).forEach { m ->
                                val k = m.groupValues[1]
                                val v = m.groupValues[2]
                                if (k != "strings" && k != "version") {
                                    map[k] = v
                                }
                            }
                        }

                        if (map.isNotEmpty()) {
                            val stringsObj = JSONObject()
                            for ((k, v) in map) {
                                stringsObj.put(k, v)
                            }
                            val newContent = stringsObj.toString()
                            val currentLang = LanguageManager.getCurrentLanguageCode()
                            val file = File(context.filesDir, "custom_lang_$currentLang.json")

                            val cachedContent = if (file.exists()) file.readText(Charsets.UTF_8) else ""
                            val cachedVersion = prefs?.getInt(KEY_STRINGS_VERSION, -1) ?: -1

                            val hasChanged = newContent != cachedContent ||
                                (serverVersion != -1 && serverVersion > cachedVersion)

                            if (hasChanged || !file.exists()) {
                                file.writeText(newContent, Charsets.UTF_8)
                                if (serverVersion != -1) {
                                    prefs?.edit()?.putInt(KEY_STRINGS_VERSION, serverVersion)?.apply()
                                }
                                stringsUpdated = true
                            }

                            // ALWAYS reload AppStrings into memory cache so customStrings is populated
                            withContext(Dispatchers.Main) {
                                AppStrings.loadCustomStrings(context)
                            }
                        }
                    }
                } catch (se: Exception) {
                    se.printStackTrace()
                }

                // 1. Process Disabled Features with Fault-Tolerant Regex Fallback
                val currentDisabled = mutableSetOf<String>()
                try {
                    val root = JSONObject(jsonStr)
                    if (root.has("disabledFeatures")) {
                        val arr = root.getJSONArray("disabledFeatures")
                        for (i in 0 until arr.length()) {
                            currentDisabled.add(arr.getString(i))
                        }
                    }
                } catch (e: Exception) {
                    val regex = """"disabledFeatures"\s*:\s*\[([^\]]*)\]""".toRegex()
                    val match = regex.find(jsonStr)
                    if (match != null) {
                        val content = match.groupValues[1]
                        val itemRegex = """"([^"]+)"""".toRegex()
                        itemRegex.findAll(content).forEach { m ->
                            currentDisabled.add(m.groupValues[1])
                        }
                    }
                }

                val previousDisabled = prefs?.getStringSet(KEY_DISABLED_SET, emptySet()) ?: emptySet()
                
                // Detect re-enabled features (was disabled before, but no longer disabled now)
                val reEnabled = previousDisabled.filter { !currentDisabled.contains(it) }
                result.reEnabledFeatureIds = reEnabled

                // Update saved disabled set
                prefs?.edit()?.putStringSet(KEY_DISABLED_SET, currentDisabled)?.apply()
                result.currentlyDisabledIds = currentDisabled

                // 2. Process New Features & Updates requiring user download with Fault-Tolerant Regex Fallback
                val downloaded = prefs?.getStringSet(KEY_DOWNLOADED_FEATURES, emptySet()) ?: emptySet()
                try {
                    val root = JSONObject(jsonStr)
                    if (root.has("newFeatures")) {
                        val arr = root.getJSONArray("newFeatures")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.getString("id")
                            if (!downloaded.contains(id)) {
                                result.pendingDownloads.add(
                                    DynamicFeatureItem(
                                        id = id,
                                        featureId = obj.optString("featureId"),
                                        title = obj.optString("title"),
                                        description = obj.optString("description"),
                                        downloadUrl = obj.optString("downloadUrl"),
                                        version = obj.optInt("version", 1)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    val regex = """"newFeatures"\s*:\s*\[([\s\S]*?)\]""".toRegex()
                    val match = regex.find(jsonStr)
                    if (match != null) {
                        val content = match.groupValues[1]
                        val objRegex = """\{\s*"id"\s*:\s*"([^"]+)"\s*,\s*"featureId"\s*:\s*"([^"]+)"\s*,\s*"title"\s*:\s*"([^"]+)"\s*,\s*"description"\s*:\s*"([^"]+)"\s*,\s*"downloadUrl"\s*:\s*"([^"]+)"[^}]*\}""".toRegex()
                        objRegex.findAll(content).forEach { m ->
                            val id = m.groupValues[1]
                            if (!downloaded.contains(id)) {
                                result.pendingDownloads.add(
                                    DynamicFeatureItem(
                                        id = id,
                                        featureId = m.groupValues[2],
                                        title = m.groupValues[3],
                                        description = m.groupValues[4],
                                        downloadUrl = m.groupValues[5],
                                        version = 1
                                    )
                                )
                            }
                        }
                    }
                }
                
                result.isSuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }

    fun getCachedDisabledFeatures(context: Context): Set<String> {
        init(context)
        return prefs?.getStringSet(KEY_DISABLED_SET, emptySet()) ?: emptySet()
    }

    fun isFeatureDownloaded(featureIdKey: String): Boolean {
        val downloaded = prefs?.getStringSet(KEY_DOWNLOADED_FEATURES, emptySet()) ?: emptySet()
        return downloaded.contains(featureIdKey)
    }

    fun markFeatureAsDownloaded(featureIdKey: String) {
        val downloaded = (prefs?.getStringSet(KEY_DOWNLOADED_FEATURES, emptySet()) ?: emptySet()).toMutableSet()
        downloaded.add(featureIdKey)
        prefs?.edit()?.putStringSet(KEY_DOWNLOADED_FEATURES, downloaded)?.apply()
    }

    suspend fun downloadFeatureModel(
        context: Context,
        featureId: String,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        init(context)
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val ext = if (downloadUrl.contains(".onnx")) ".onnx" else if (downloadUrl.contains(".tar.bz2")) ".tar.bz2" else if (downloadUrl.contains(".traineddata")) ".traineddata" else ".json"
            val targetFile = File(modelsDir, "${featureId}_model$ext")

            var currentUrl = downloadUrl
            var connection: HttpURLConnection? = null
            var redirectCount = 0

            while (redirectCount < 5) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.useCaches = false
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    if (!newUrl.isNullOrEmpty()) {
                        currentUrl = newUrl
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val conn = connection ?: return@withContext false
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val totalLength = conn.contentLength
                var downloadedBytes = 0L

                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var lastReportedProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalLength > 0) {
                                val progress = ((downloadedBytes * 100) / totalLength).toInt()
                                if (progress != lastReportedProgress && progress % 10 == 0) {
                                    lastReportedProgress = progress
                                    try {
                                        withContext(Dispatchers.Main) {
                                            onProgress(progress)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }

                markFeatureAsDownloaded(featureId)
                return@withContext true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return@withContext false
    }

    fun getDownloadedModelFile(context: Context, featureId: String): File? {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) return null
        val files = modelsDir.listFiles { _, name -> name.startsWith("${featureId}_model") }
        val file = files?.firstOrNull()
        return if (file != null && file.exists() && file.length() > 0) file else null
    }
}

class CloudConfigResult {
    var isSuccess: Boolean = false
    var currentlyDisabledIds: Set<String> = emptySet()
    var reEnabledFeatureIds: List<String> = emptyList()
    val pendingDownloads: MutableList<DynamicFeatureItem> = mutableListOf()
}
