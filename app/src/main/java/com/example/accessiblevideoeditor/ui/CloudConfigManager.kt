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
            val url = URL(CONFIG_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)

                // 1. Process Disabled Features
                val currentDisabled = mutableSetOf<String>()
                if (root.has("disabledFeatures")) {
                    val arr = root.getJSONArray("disabledFeatures")
                    for (i in 0 until arr.length()) {
                        currentDisabled.add(arr.getString(i))
                    }
                }

                val previousDisabled = prefs?.getStringSet(KEY_DISABLED_SET, emptySet()) ?: emptySet()
                
                // Detect re-enabled features (was disabled before, but no longer disabled now)
                val reEnabled = previousDisabled.filter { !currentDisabled.contains(it) }
                result.reEnabledFeatureIds = reEnabled

                // Update saved disabled set
                prefs?.edit()?.putStringSet(KEY_DISABLED_SET, currentDisabled)?.apply()
                result.currentlyDisabledIds = currentDisabled

                // 2. Process New Features & Updates requiring user download
                val downloaded = prefs?.getStringSet(KEY_DOWNLOADED_FEATURES, emptySet()) ?: emptySet()

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
    var currentlyDisabledIds: Set<String> = emptySet()
    var reEnabledFeatureIds: List<String> = emptyList()
    val pendingDownloads: MutableList<DynamicFeatureItem> = mutableListOf()
}
