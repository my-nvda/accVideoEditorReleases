package com.example.accessiblevideoeditor.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import java.io.File

object AppStrings {
    private var customStrings: Map<String, String>? = null
    var customStringsVersion = mutableStateOf(0)

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
                customStringsVersion.value++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            customStrings = null
            customStringsVersion.value++
        }
    }

    @Composable
    fun get(id: Int, vararg formatArgs: Any): String {
        // Observe version to trigger recomposition when strings change
        val version = customStringsVersion.value
        val context = LocalContext.current
        
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
                e.printStackTrace()
            }
        }
        
        // Fallback to default resources
        @Suppress("SpreadOperator")
        return if (formatArgs.isNotEmpty()) {
            stringResource(id, *formatArgs)
        } else {
            stringResource(id)
        }
    }

    fun get(context: Context, id: Int, vararg formatArgs: Any): String {
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
                e.printStackTrace()
            }
        }
        @Suppress("SpreadOperator")
        return if (formatArgs.isNotEmpty()) {
            context.getString(id, *formatArgs)
        } else {
            context.getString(id)
        }
    }
}
