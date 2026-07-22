package com.example.accessiblevideoeditor.ui

import android.content.Context
import org.json.JSONObject
import java.io.File

object AppStrings {
    private var customStrings: Map<String, String>? = null
    var customStringsVersion = 0

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
                android.widget.Toast.makeText(context, "تم تحميل ${map.size} نص مخصص للغة: $currentLang", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "خطأ في قراءة ملف الترجمات لـ $currentLang", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            customStrings = null
            customStringsVersion++
            android.widget.Toast.makeText(context, "ملف الترجمات لـ $currentLang غير موجود محلياً", android.widget.Toast.LENGTH_SHORT).show()
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

