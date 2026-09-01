package com.example.accessiblevideoeditor.ui

import android.content.Context
import org.json.JSONObject
import java.io.File

object AppStrings {
    // Exposed as internal so AppStringResources can read it directly
    @Volatile var customStrings: Map<String, String>? = null
    var customStringsVersion = 0

    /**
     * Load translations from the locally cached file.
     * Call this as early as possible (Application.onCreate or before setContentView).
     */
    fun loadCustomStrings(context: Context) {
        val currentLang = LanguageManager.getCurrentLanguageCode(context)
        val cloudFile = File(context.filesDir, "cloud_lang_$currentLang.json")
        val localFile = File(context.filesDir, "local_lang_$currentLang.json")
        
        val mergedMap = mutableMapOf<String, String>()
        
        // 1. Load cloud translations (lower priority)
        if (cloudFile.exists()) {
            try {
                val json = JSONObject(cloudFile.readText(Charsets.UTF_8))
                for (key in json.keys()) {
                    mergedMap[key] = json.getString(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 2. Load local translations (higher priority, overrides cloud)
        if (localFile.exists()) {
            try {
                val json = JSONObject(localFile.readText(Charsets.UTF_8))
                for (key in json.keys()) {
                    mergedMap[key] = json.getString(key)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 3. Backward compatibility: If old custom_lang file exists and localFile doesn't, load it as local and rename/save it
        val oldFile = File(context.filesDir, "custom_lang_$currentLang.json")
        if (oldFile.exists() && !localFile.exists()) {
            try {
                val json = JSONObject(oldFile.readText(Charsets.UTF_8))
                for (key in json.keys()) {
                    mergedMap[key] = json.getString(key)
                }
                oldFile.renameTo(localFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (mergedMap.isNotEmpty()) {
            customStrings = mergedMap
        } else {
            customStrings = null
        }
        customStringsVersion++
    }

    /**
     * Helper to get string directly without fallback, returning null if not found.
     */
    fun getDirect(context: Context, id: Int, vararg formatArgs: Any): String? {
        customStrings?.let { strings ->
            try {
                val name = context.resources.getResourceEntryName(id)
                if (strings.containsKey(name)) {
                    val str = strings[name]!!
                    if (formatArgs.isNotEmpty()) {
                        return String.format(str, *formatArgs)
                    }
                    return str
                }
            } catch (e: Exception) {
                // Ignore to allow fallback
            }
        }
        return null
    }

    /**
     * Programmatic helper for code that needs a translated string directly.
     * Prefers cloud translation, falls back to APK string.
     */
    fun get(context: Context, id: Int, vararg formatArgs: Any): String {
        return getDirect(context, id, *formatArgs) ?: run {
            @Suppress("SpreadOperator")
            if (formatArgs.isNotEmpty()) {
                context.getString(id, *formatArgs)
            } else {
                context.getString(id)
            }
        }
    }
}
