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
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }

                // Fetch Strings Patch in background
                try {
                    val stringsUrl = URL("$STRINGS_PATCH_URL?t=${System.currentTimeMillis()}")
                    val stringsConn = stringsUrl.openConnection() as HttpURLConnection
                    stringsConn.useCaches = false
                    stringsConn.connectTimeout = 4000
                    stringsConn.readTimeout = 4000
                    stringsConn.requestMethod = "GET"
                    if (stringsConn.responseCode == HttpURLConnection.HTTP_OK) {
                        val stringsJsonStr = stringsConn.inputStream.bufferedReader().use { it.readText() }
                        val map = mutableMapOf<String, String>()
                        
                        try {
                            val stringsRoot = JSONObject(stringsJsonStr)
                            if (stringsRoot.has("strings")) {
                                val stringsObj = stringsRoot.getJSONObject("strings")
                                for (key in stringsObj.keys()) {
                                    map[key] = stringsObj.getString(key)
                                }
                            }
                        } catch (je: Exception) {
                            val regex = """"strings"\s*:\s*\{([\s\S]*?)\}""".toRegex()
                            val match = regex.find(stringsJsonStr)
                            if (match != null) {
                                val content = match.groupValues[1]
                                val itemRegex = """"([^"]+)"\s*:\s*"([^"]*)"""".toRegex()
                                itemRegex.findAll(content).forEach { m ->
                                    map[m.groupValues[1]] = m.groupValues[2]
                                }
                            }
                        }
                        
                        if (map.isNotEmpty()) {
                            val stringsObj = JSONObject()
                            for ((k, v) in map) {
                                stringsObj.put(k, v)
                            }
                            val currentLang = LanguageManager.getCurrentLanguageCode()
                            val file = File(context.filesDir, "custom_lang_$currentLang.json")
                            file.writeText(stringsObj.toString(), Charsets.UTF_8)
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

    fun markFeatureAsDownloaded(featureIdKey: String) {
        val downloaded = (prefs?.getStringSet(KEY_DOWNLOADED_FEATURES, emptySet()) ?: emptySet()).toMutableSet()
        downloaded.add(featureIdKey)
        prefs?.edit()?.putStringSet(KEY_DOWNLOADED_FEATURES, downloaded)?.apply()
    }
}

class CloudConfigResult {
    var isSuccess: Boolean = false
    var currentlyDisabledIds: Set<String> = emptySet()
    var reEnabledFeatureIds: List<String> = emptyList()
    val pendingDownloads: MutableList<DynamicFeatureItem> = mutableListOf()
}
