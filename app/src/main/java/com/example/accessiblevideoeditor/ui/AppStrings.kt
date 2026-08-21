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
        val currentLang = LanguageManager.getCurrentLanguageCode()
        val file = File(context.filesDir, "custom_lang_$currentLang.json")
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val map = mutableMapOf<String, String>()
                for (key in json.keys()) {
                    map[key] = json.getString(key)
                }
                customStrings = map
                customStringsVersion++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            customStrings = null
            customStringsVersion++
        }
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
