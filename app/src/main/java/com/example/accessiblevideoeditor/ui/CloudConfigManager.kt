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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

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

    @Volatile var githubToken: String = ""
    @Volatile var githubRepo: String = ""
    @Volatile var githubProxyUrl: String = ""
    @Volatile var stringsUpdated: Boolean = false

    private var prefs: SharedPreferences? = null

    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (prefs == null) {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedToken = prefs?.getString("github_token", "") ?: ""
                githubToken = decodeBase64(savedToken)
                githubRepo = prefs?.getString("github_repo", "") ?: ""
                githubProxyUrl = prefs?.getString("github_proxy_url", "") ?: ""
                
                try {
                    if (com.google.firebase.FirebaseApp.getApps(context.applicationContext).isEmpty()) {
                        com.google.firebase.FirebaseApp.initializeApp(context.applicationContext)
                    }
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all")
                    val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
                    val configSettings = com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(3600)
                        .build()
                    remoteConfig.setConfigSettingsAsync(configSettings)
                    remoteConfig.fetchAndActivate()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun checkCloudConfig(context: Context): CloudConfigResult = withContext(Dispatchers.IO) {
        init(context)
        stringsUpdated = false
        val result = CloudConfigResult()
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$CONFIG_URL?t=${System.currentTimeMillis()}")
            connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    val cachedFile = File(context.filesDir, "cached_cloud_config.json")
                    cachedFile.writeText(jsonStr, Charsets.UTF_8)
                } catch (e: Exception) { e.printStackTrace() }

                // Fetch Strings Patch in background
                var stringsConn: HttpURLConnection? = null
                try {
                    val stringsUrl = URL("$STRINGS_PATCH_URL?t=${System.currentTimeMillis()}")
                    stringsConn = stringsUrl.openConnection() as HttpURLConnection
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
                        val currentLang = LanguageManager.getCurrentLanguageCode(context)

                        try {
                            val stringsRoot = JSONObject(stringsJsonStr)
                            // Read version field for comparison
                            serverVersion = stringsRoot.optInt("version", -1)
                            if (stringsRoot.has(currentLang)) {
                                val stringsObj = stringsRoot.getJSONObject(currentLang)
                                for (key in stringsObj.keys()) {
                                    map[key] = stringsObj.getString(key)
                                }
                            } else {
                                val targetLang = stringsRoot.optString("lang", "ar")
                                if (currentLang == targetLang && stringsRoot.has("strings")) {
                                    val stringsObj = stringsRoot.getJSONObject("strings")
                                    for (key in stringsObj.keys()) {
                                        map[key] = stringsObj.getString(key)
                                    }
                                }
                            }
                        } catch (je: Exception) {
                            // Robust line/pair level fallback if JSON has syntax errors like missing commas
                            val langMatch = """"lang"\s*:\s*"([^"]+)"""".toRegex().find(stringsJsonStr)
                            val targetLang = langMatch?.groupValues?.get(1) ?: "ar"
                            if (currentLang == targetLang) {
                                val itemRegex = """"([^"\\]+)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
                                itemRegex.findAll(stringsJsonStr).forEach { m ->
                                    val k = m.groupValues[1]
                                    val v = m.groupValues[2]
                                    if (k != "strings" && k != "version" && k != "lang") {
                                        map[k] = v
                                    }
                                }
                            }
                        }

                        val file = File(context.filesDir, "cloud_lang_$currentLang.json")
                        if (map.isNotEmpty()) {
                            val stringsObj = JSONObject()
                            for ((k, v) in map) {
                                stringsObj.put(k, v)
                            }
                            val newContent = stringsObj.toString()
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
                        } else {
                            // Only delete if the current language is Arabic (the server's targetLang)
                            // to avoid deleting other languages' local custom files since the server does not host them
                            if (currentLang == "ar" && file.exists()) {
                                file.delete()
                                stringsUpdated = true
                            }
                        }

                        // Offload AppStrings.loadCustomStrings to background thread
                        AppStrings.loadCustomStrings(context)
                    }
                } catch (se: Exception) {
                    se.printStackTrace()
                } finally {
                    stringsConn?.disconnect()
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
                
                // Detect re-enabled features
                val reEnabled = previousDisabled.filter { !currentDisabled.contains(it) }
                result.reEnabledFeatureIds = reEnabled

                // Update saved disabled set (clear immediately if current is empty)
                if (currentDisabled.isEmpty()) {
                    prefs?.edit()?.remove(KEY_DISABLED_SET)?.apply()
                } else {
                    prefs?.edit()?.putStringSet(KEY_DISABLED_SET, currentDisabled)?.apply()
                }
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
                
                // 3. Process Whitelisted Devices
                val currentWhitelist = mutableMapOf<String, List<String>>()
                try {
                    val root = JSONObject(jsonStr)
                    if (root.has("whitelistedDevices")) {
                        val whitelistObj = root.getJSONObject("whitelistedDevices")
                        whitelistObj.keys().forEach { key ->
                            val arr = whitelistObj.getJSONArray(key)
                            val devices = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                devices.add(arr.getString(i))
                            }
                            currentWhitelist[key] = devices
                        }
                    }

                    // Parse telemetryConfig
                    var proxyUrlToSave = ""
                    val tokenToSave = if (root.has("telemetryConfig")) {
                        val tc = root.getJSONObject("telemetryConfig")
                        githubToken = decodeBase64(tc.optString("token", ""))
                        githubRepo = tc.optString("repo", "")
                        proxyUrlToSave = tc.optString("proxyUrl", "")
                        tc.optString("token", "")
                    } else {
                        githubToken = decodeBase64(root.optString("github_token", ""))
                        githubRepo = root.optString("github_repo", "")
                        proxyUrlToSave = root.optString("proxy_url", "")
                        root.optString("github_token", "")
                    }
                    githubProxyUrl = proxyUrlToSave

                    // Save to SharedPreferences
                    prefs?.edit()?.apply {
                        putString("github_token", tokenToSave)
                        putString("github_repo", githubRepo)
                        putString("github_proxy_url", proxyUrlToSave)
                        apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                result.whitelistedFeatures = currentWhitelist

                // 4. Process Announcements and Device Groups
                try {
                    val root = JSONObject(jsonStr)
                    val androidId = try {
                        android.provider.Settings.Secure.getString(
                            context.contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        ) ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    
                    val myGroups = mutableSetOf<String>()
                    if (root.has("deviceGroups")) {
                        val dgObj = root.getJSONObject("deviceGroups")
                        dgObj.keys().forEach { groupName ->
                            val members = dgObj.getJSONArray(groupName)
                            for (i in 0 until members.length()) {
                                if (members.getString(i) == androidId) {
                                    myGroups.add(groupName)
                                }
                            }
                        }
                    }

                    val shownAnnouncements = prefs?.getStringSet("shown_announcements", emptySet()) ?: emptySet()

                    if (root.has("announcements")) {
                        val annArr = root.getJSONArray("announcements")
                        val currentTime = System.currentTimeMillis()

                        for (i in 0 until annArr.length()) {
                            val annObj = annArr.getJSONObject(i)
                            val annId = annObj.getString("id")

                            if (!shownAnnouncements.contains(annId)) {
                                val title = annObj.optString("title", "")
                                val msg = annObj.optString("message", "")
                                val targetDevices = mutableListOf<String>()
                                if (annObj.has("targetDevices")) {
                                    val tdArr = annObj.getJSONArray("targetDevices")
                                    for (j in 0 until tdArr.length()) {
                                        targetDevices.add(tdArr.getString(j))
                                    }
                                }
                                val targetGroup = annObj.optString("targetGroup", "all")
                                val scheduleTime = annObj.optLong("scheduleTime", 0L)

                                val timeMatch = scheduleTime == 0L || currentTime >= scheduleTime
                                val deviceMatch = targetDevices.isEmpty() || (androidId.isNotBlank() && targetDevices.contains(androidId))
                                val groupMatch = targetGroup == "all" || targetGroup.isBlank() || myGroups.contains(targetGroup)

                                if (timeMatch && deviceMatch && groupMatch) {
                                    result.pendingAnnouncements.add(
                                        CloudAnnouncementItem(
                                            id = annId,
                                            title = title,
                                            message = msg,
                                            targetDevices = targetDevices,
                                            targetGroup = targetGroup,
                                            scheduleTime = scheduleTime
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                result.isSuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
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
        var connection: HttpURLConnection? = null
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val ext = if (downloadUrl.contains(".onnx")) ".onnx" else if (downloadUrl.contains(".tar.bz2")) ".tar.bz2" else if (downloadUrl.contains(".traineddata")) ".traineddata" else ".json"
            val targetFile = File(modelsDir, "${featureId}_model$ext")
            val tmpFile = File(modelsDir, "${featureId}_model$ext.tmp")

            var downloadedBytes = 0L
            if (tmpFile.exists()) {
                downloadedBytes = tmpFile.length()
            }

            var currentUrl = downloadUrl
            var redirectCount = 0

            try {
                System.setProperty("java.net.preferIPv4Stack", "true")
                System.setProperty("java.net.preferIPv6Addresses", "false")
            } catch (_: Exception) {}

            while (redirectCount < 5) {
                val url = URL(currentUrl)
                val conn = url.openConnection() as HttpURLConnection
                connection = conn
                conn.useCaches = false
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                conn.setRequestProperty("Accept-Encoding", "identity")
                conn.setRequestProperty("Connection", "Keep-Alive")
                
                // Add Range header for resumption
                if (downloadedBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
                }

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!newUrl.isNullOrEmpty()) {
                        currentUrl = newUrl
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val conn = connection ?: return@withContext false
            var responseCode = conn.responseCode

            // HTTP 416 (Range Not Satisfiable) fix
            if (responseCode == 416) {
                tmpFile.delete()
                downloadedBytes = 0L
                
                // Reconnect without Range header
                conn.disconnect()
                val url = URL(downloadUrl)
                val retryConn = url.openConnection() as HttpURLConnection
                connection = retryConn
                retryConn.useCaches = false
                retryConn.connectTimeout = 30000
                retryConn.readTimeout = 30000
                retryConn.requestMethod = "GET"
                retryConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                retryConn.setRequestProperty("Accept-Encoding", "identity")
                retryConn.setRequestProperty("Connection", "Keep-Alive")
                responseCode = retryConn.responseCode
            }

            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL // 206
            val isOk = responseCode == HttpURLConnection.HTTP_OK // 200

            if (isPartial || isOk) {
                var append = false
                var totalLength = conn.contentLength.toLong()
                
                if (isPartial) {
                    append = true
                    totalLength += downloadedBytes
                } else {
                    downloadedBytes = 0L
                }

                java.io.BufferedInputStream(conn.inputStream, 131072).use { input ->
                    java.io.BufferedOutputStream(java.io.FileOutputStream(tmpFile, append), 131072).use { output ->
                        val buffer = ByteArray(131072) // 128 KB high-speed buffer
                        var bytesRead: Int
                        var lastReportedProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalLength > 0) {
                                val progress = ((downloadedBytes * 100) / totalLength).toInt()
                                if (progress != lastReportedProgress && progress % 5 == 0) {
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

                // Download completed, rename tmp to target
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (tmpFile.renameTo(targetFile)) {
                        if (ext == ".tar.bz2") {
                            val extracted = decompressTarBz2(targetFile, modelsDir, featureId)
                            if (targetFile.exists()) {
                                targetFile.delete()
                            }
                            if (extracted != null && extracted.exists()) {
                                if (validateModelStructure(context, extracted, featureId)) {
                                    markFeatureAsDownloaded(featureId)
                                    return@withContext true
                                } else {
                                    try { extracted.delete() } catch (_: Exception) {}
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "النموذج الذي تم تنزيله غير صالح أو معطوب على الخادم. يرجى إبلاغ المطور.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    return@withContext false
                                }
                            } else {
                                return@withContext false
                            }
                        } else {
                            if (ext == ".onnx") {
                                if (!validateModelStructure(context, targetFile, featureId)) {
                                    try { targetFile.delete() } catch (_: Exception) {}
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "النموذج الذي تم تنزيله غير صالح أو معطوب على الخادم. يرجى إبلاغ المطور.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    return@withContext false
                                }
                            }
                            markFeatureAsDownloaded(featureId)
                            return@withContext true
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return@withContext false
    }

    private fun decompressTarBz2(archiveFile: File, outputDir: File, featureId: String): File? {
        try {
            var firstOutFile: File? = null
            FileInputStream(archiveFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    BZip2CompressorInputStream(bis).use { bzIn ->
                        TarArchiveInputStream(bzIn).use { tarIn ->
                            var entry = tarIn.nextTarEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                // Skip PAX global headers or macOS metadata files (starts with ._)
                                if (!entry.isDirectory && (entryName.endsWith(".onnx") || entryName.endsWith(".traineddata")) && !entryName.contains("pax_global_header") && !entryName.contains("._")) {
                                    val targetFileName = if (featureId == "resemble_enhance_fp32") {
                                        entry.name
                                    } else {
                                        "${featureId}_model.onnx"
                                    }
                                    val outFile = File(outputDir, targetFileName)
                                    FileOutputStream(outFile).use { fos ->
                                        val buffer = ByteArray(4096)
                                        var len: Int
                                        while (tarIn.read(buffer).also { len = it } != -1) {
                                            fos.write(buffer, 0, len)
                                        }
                                    }
                                    if (firstOutFile == null) {
                                        firstOutFile = outFile
                                    }
                                    if (featureId != "resemble_enhance_fp32") {
                                        return outFile
                                    }
                                }
                                entry = tarIn.nextTarEntry
                            }
                        }
                    }
                }
            }
            return firstOutFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getDownloadedModelFile(context: Context, featureId: String): File? {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) return null
        if (featureId == "resemble_enhance_fp32") {
            val file = File(modelsDir, "clear-studio.onnx")
            return if (file.exists() && file.length() > 0) file else null
        }
        val files = modelsDir.listFiles { _, name -> 
            (name.startsWith("${featureId}_model") && !name.endsWith(".tmp") && !name.endsWith(".tar.bz2"))
        }
        val file = files?.firstOrNull()
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    private fun decodeBase64(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("ghp_") || trimmed.startsWith("github_pat_") || trimmed.startsWith("gho_")) {
            return trimmed
        }
        return try {
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            val decoded = String(decodedBytes, Charsets.UTF_8).trim()
            if (decoded.startsWith("ghp_") || decoded.startsWith("github_pat_") || decoded.startsWith("gho_") || decoded.reversed().startsWith("ghp_") || decoded.reversed().startsWith("github_pat_")) {
                if (decoded.reversed().startsWith("ghp_") || decoded.reversed().startsWith("github_pat_") || decoded.reversed().startsWith("gho_")) {
                    decoded.reversed()
                } else {
                    decoded
                }
            } else {
                trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }
    fun markAnnouncementAsShown(context: Context, annId: String) {
        init(context)
        val shown = (prefs?.getStringSet("shown_announcements", emptySet()) ?: emptySet()).toMutableSet()
        shown.add(annId)
        prefs?.edit()?.putStringSet("shown_announcements", shown)?.apply()
    }

    fun getCachedConfig(context: Context): JSONObject? {
        val file = File(context.filesDir, "cached_cloud_config.json")
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun getGeminiModels(context: Context): List<String> {
        val defaultModels = listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro", "gemini-3.0-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
        val json = getCachedConfig(context) ?: return defaultModels
        if (json.has("geminiModels")) {
            try {
                val arr = json.getJSONArray("geminiModels")
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return defaultModels
    }

    fun getTextAnimations(context: Context): List<Pair<String, String>> {
        val defaultAnims = listOf(
            Pair("none", "none (بدون حركة)"),
            Pair("fade_in", "fade_in (ظهور وتلاشي ناعم 🌟)"),
            Pair("fade_out", "fade_out (تلاشي ختامي تدريجي 🌑)"),
            Pair("fade_in_out", "fade_in_out (ظهور ناعم وتلاشي ختامي 🌅)"),
            Pair("slide_up", "slide_up (انزلاق صاعد من الأسفل ⬆️)"),
            Pair("slide_down", "slide_down (انزلاق هابط من الأعلى ⬇️)"),
            Pair("slide_left", "slide_left (انزلاق جانبي لليسار ⬅️)"),
            Pair("slide_right", "slide_right (انزلاق جانبي لليمين ➡️)"),
            Pair("zoom_in", "zoom_in (انبثاق وتكبير مفاجئ 💥)"),
            Pair("elastic_zoom", "elastic_zoom (تكبير مرن ممتد 🪀)"),
            Pair("pulse", "pulse (نبض متكرر 💓)"),
            Pair("typewriter", "typewriter (آلة كاتبة ⌨️)"),
            Pair("bounce_in", "bounce_in (ارتطام مرن ومطاطي 🏀)"),
            Pair("mask_reveal", "mask_reveal (ظهور سينمائي خلف قناع 🎬)"),
            Pair("blink", "blink (وميض سريع متقطع 💡)"),
            Pair("flicker", "flicker (تأثير تشويش كهربائي ⚡)"),
            Pair("wave", "wave (حركة موجية تموجية 🌊)"),
            Pair("rotate_in", "rotate_in (دوران حلزوني للداخل 🌀)")
        )
        val json = getCachedConfig(context) ?: return defaultAnims
        if (json.has("textAnimations")) {
            try {
                val arr = json.getJSONArray("textAnimations")
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Pair(obj.getString("value"), obj.getString("name")))
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return defaultAnims
    }

    fun getShapeMasks(context: Context): List<Pair<String, String>> {
        val defaultShapes = listOf(
            Pair("none", "none (بدون شكل)"),
            Pair("top_bottom_cinematic", "top_bottom_cinematic (شكل سينمائي علوي وسفلي)"),
            Pair("circle_center", "circle_center (شكل دائري في المنتصف)")
        )
        val json = getCachedConfig(context) ?: return defaultShapes
        if (json.has("shapeMasks")) {
            try {
                val arr = json.getJSONArray("shapeMasks")
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Pair(obj.getString("value"), obj.getString("name")))
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return defaultShapes
    }

    fun getSupportedLanguages(context: Context): List<Pair<String, String>> {
        val defaultLangs = listOf(
            Pair("ar", "العربية (Arabic)"),
            Pair("en", "English"),
            Pair("es", "Español (Spanish)"),
            Pair("fr", "Français (French)"),
            Pair("fa", "فارسی (Persian)"),
            Pair("he", "עברית (Hebrew)"),
            Pair("ru", "Русский (Russian)"),
            Pair("tr", "Türkçe (Turkish)"),
            Pair("ur", "اردو (Urdu)"),
            Pair("ja", "日本語 (Japanese)"),
            Pair("zh", "中文 (Chinese)")
        )
        val json = getCachedConfig(context) ?: return defaultLangs
        if (json.has("supportedLanguages")) {
            try {
                val arr = json.getJSONArray("supportedLanguages")
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Pair(obj.getString("code"), obj.getString("name")))
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return defaultLangs
    }

    fun getAiModelDownloadInfo(context: Context, featureId: String): Pair<String, Int> {
        val defaultCleanUNetUrl = "https://media.githubusercontent.com/media/my-nvda/accVideoEditorReleases/main/models/cleanunet_fp16.tar.bz2"
        val defaultSeparatorUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/vocal_separator_model.tar.bz2"
        val defaultResembleUrl = "https://media.githubusercontent.com/media/my-nvda/accVideoEditorReleases/main/models/resemble_enhance_fp32.tar.bz2"
        val defaultUrl = when (featureId) {
            "cleanunet_fp16" -> defaultCleanUNetUrl
            "resemble_enhance_fp32" -> defaultResembleUrl
            else -> defaultSeparatorUrl
        }
        val json = getCachedConfig(context) ?: return Pair(defaultUrl, 1)
        if (json.has("aiModelsConfig")) {
            try {
                val config = json.getJSONObject("aiModelsConfig")
                if (config.has(featureId)) {
                    val obj = config.getJSONObject(featureId)
                    return Pair(obj.getString("downloadUrl"), obj.optInt("version", 1))
                }
            } catch (_: Exception) {}
        }
        return Pair(defaultUrl, 1)
    }

    private fun validateModelStructure(context: Context, modelFile: File, featureId: String): Boolean {
        try {
            if (!modelFile.exists() || modelFile.length() == 0L) return false
            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            val opts = ai.onnxruntime.OrtSession.SessionOptions()
            try {
                val session = env.createSession(modelFile.absolutePath, opts)
                try {
                    val inputNames = session.inputNames
                    val inputInfo = session.inputInfo
                    
                    val isTtsModel = inputNames.contains("phonemes")

                    if (isTtsModel) {
                        if (featureId == "cleanunet_fp16" || featureId == "audio_stem_separator" || featureId == "btnAudioStemSeparator" || featureId == "btnNoiseReduction") {
                            android.util.Log.e("ModelValidation", "Rejected TTS model for feature $featureId")
                            return false
                        }
                    }
                    
                    if (featureId == "cleanunet_fp16" || featureId == "btnNoiseReduction" || featureId == "resemble_enhance_fp32") {
                        val firstNode = inputInfo.values.firstOrNull()?.info
                        if (firstNode !is ai.onnxruntime.TensorInfo || firstNode.type != ai.onnxruntime.OnnxJavaType.FLOAT) {
                            return false
                        }
                    }
                } finally {
                    session.close()
                }
            } finally {
                opts.close()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getDolbyToken(context: Context): String {
        init(context)
        try {
            val userToken = context.getSharedPreferences("AccessibleVideoEditorPrefs", Context.MODE_PRIVATE)
                .getString("dolby_token_input", "")
            if (!userToken.isNullOrEmpty()) return userToken
        } catch (_: Exception) {}

        // 1. Try Firebase Remote Config
        try {
            val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
            val token = remoteConfig.getString("dolby_token")
            if (!token.isNullOrEmpty()) return token
        } catch (_: Exception) {}

        // 2. Try Github Config JSON
        try {
            val cachedFile = File(context.filesDir, "cached_cloud_config.json")
            if (cachedFile.exists()) {
                val json = org.json.JSONObject(cachedFile.readText())
                val token = json.optString("dolbyToken", "")
                if (token.isNotEmpty()) return token
            }
        } catch (_: Exception) {}

        // 3. Fallback default developer token
        return "YOUR_DOLBY_TOKEN_HERE"
    }
}

data class CloudAnnouncementItem(
    val id: String,
    val title: String,
    val message: String,
    val targetDevices: List<String>,
    val targetGroup: String,
    val scheduleTime: Long
)

class CloudConfigResult {
    var isSuccess: Boolean = false
    var currentlyDisabledIds: Set<String> = emptySet()
    var reEnabledFeatureIds: List<String> = emptyList()
    val pendingDownloads: MutableList<DynamicFeatureItem> = mutableListOf()
    var whitelistedFeatures: Map<String, List<String>> = emptyMap()
    val pendingAnnouncements: MutableList<CloudAnnouncementItem> = mutableListOf()
}
