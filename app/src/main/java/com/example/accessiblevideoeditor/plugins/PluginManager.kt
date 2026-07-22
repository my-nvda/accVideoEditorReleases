package com.example.accessiblevideoeditor.plugins

import android.content.Context
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PluginManager {

    private const val PLUGINS_URL = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/plugins.json"

    private val _pluginsState = MutableStateFlow<List<PluginModel>>(emptyList())
    val pluginsState: StateFlow<List<PluginModel>> get() = _pluginsState

    suspend fun fetchAvailablePlugins(context: Context): List<PluginModel> = withContext(Dispatchers.IO) {
        val pluginList = mutableListOf<PluginModel>()
        try {
            val url = URL("$PLUGINS_URL?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "AccessibleVideoEditorApp")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val array = root.getJSONArray("plugins")

                val pluginDir = File(context.filesDir, "plugins")
                if (!pluginDir.exists()) pluginDir.mkdirs()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    val desc = obj.getString("description")
                    val category = obj.optString("category", "General")
                    val sizeMb = obj.optDouble("sizeMb", 1.0)
                    val downloadUrl = obj.getString("downloadUrl")

                    val installedFile = File(pluginDir, "$id.json")
                    val isInstalled = installedFile.exists()

                    pluginList.add(
                        PluginModel(
                            id = id,
                            title = title,
                            description = desc,
                            category = category,
                            sizeMb = sizeMb,
                            downloadUrl = downloadUrl,
                            isInstalled = isInstalled
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (pluginList.isEmpty()) {
            // Fallback default plugins list if network offline
            pluginList.addAll(getDefaultFallbackPlugins(context))
        }

        _pluginsState.value = pluginList
        return@withContext pluginList
    }

    private fun getDefaultFallbackPlugins(context: Context): List<PluginModel> {
        val pluginDir = File(context.filesDir, "plugins")
        return listOf(
            PluginModel("plugin_ai_voice_dubbing", "دبلجة وتوليد الصوت بالذكاء الاصطناعي", "توليد تعليق صوتي ودبلجة مخصصة باللغة العربية", "صوتيات", 12.5, "", File(pluginDir, "plugin_ai_voice_dubbing.json").exists()),
            PluginModel("plugin_audio_stem_separator", "عازل ومحلل الآلات والموسيقى", "فصل الصوت البشري عن الموسيقى التصويرية والآلات", "صوتيات", 15.0, "", File(pluginDir, "plugin_audio_stem_separator.json").exists()),
            PluginModel("plugin_auto_shorts_creator", "مولد الفيديوهات القصيرة والقوالب الذكية", "تحويل الصور والفيديوهات تلقائياً لمقاطع Shorts وReels", "ميديا", 8.0, "", File(pluginDir, "plugin_auto_shorts_creator.json").exists()),
            PluginModel("plugin_cinematic_lut_shaders", "حزمة الفلاتر والتأثيرات السينمائية", "تأثيرات سينمائية وانتقالية واحترافية للألوان", "فلاتر", 5.0, "", File(pluginDir, "plugin_cinematic_lut_shaders.json").exists()),
            PluginModel("plugin_ai_scene_audio_description", "الوصف الصوتي التفاعلي للمكفوفين", "تحليل مشاهد الفيديو وتوليد وصف صوتي تفاعلي للحدث", "إتاحة", 10.0, "", File(pluginDir, "plugin_ai_scene_audio_description.json").exists()),
            PluginModel("plugin_subtitles_ocr_srt", "مستخرج وقارئ الترجمات الشاشاتية SRT", "استخراج الترجمات المدمجة بصرية من الفيديو إلى ملفات SRT", "ترجمة", 6.5, "", File(pluginDir, "plugin_subtitles_ocr_srt.json").exists())
        )
    }

    suspend fun downloadAndInstallPlugin(context: Context, plugin: PluginModel, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val pluginDir = File(context.filesDir, "plugins")
            if (!pluginDir.exists()) pluginDir.mkdirs()

            val targetFile = File(pluginDir, "${plugin.id}.json")

            // Simulate or execute download with progress beeps
            for (p in 1..10) {
                val percent = p * 10
                withContext(Dispatchers.Main) {
                    onProgress(percent)
                }
                if (percent % 20 == 0) {
                    try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                }
                kotlinx.coroutines.delay(100)
            }

            // Write dummy manifest config for installed plugin
            val configJson = JSONObject().apply {
                put("id", plugin.id)
                put("installedAt", System.currentTimeMillis())
                put("version", "1.0")
                put("enabled", true)
            }
            targetFile.writeText(configJson.toString(), Charsets.UTF_8)

            // Refresh state
            val currentList = _pluginsState.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == plugin.id }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(isInstalled = true, isDownloading = false)
                _pluginsState.value = currentList
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    fun uninstallPlugin(context: Context, pluginId: String): Boolean {
        try {
            val pluginDir = File(context.filesDir, "plugins")
            val targetFile = File(pluginDir, "$pluginId.json")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val currentList = _pluginsState.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == pluginId }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(isInstalled = false)
                _pluginsState.value = currentList
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
