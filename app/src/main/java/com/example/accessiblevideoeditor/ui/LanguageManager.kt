package com.example.accessiblevideoeditor.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {

    val supportedLanguages = listOf(
        "en" to "English",
        "ar" to "العربية",
        "he" to "עברית",
        "fr" to "Français",
        "fa" to "فارسی",
        "ur" to "اردو",
        "tr" to "Türkçe",
        "es" to "Español",
        "ru" to "Русский",
        "zh-CN" to "中文",
        "ja" to "日本語"
    )

    fun setLanguage(context: Context, languageCode: String) {
        try {
            val prefs = context.getSharedPreferences("accessible_video_editor_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_language_code", languageCode).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val localeList = LocaleListCompat.create(Locale.forLanguageTag(languageCode))
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun setLanguage(languageCode: String) {
        val localeList = LocaleListCompat.create(Locale.forLanguageTag(languageCode))
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentLanguageCode(context: Context? = null): String {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("accessible_video_editor_prefs", Context.MODE_PRIVATE)
                val savedLang = prefs.getString("selected_language_code", "")
                if (!savedLang.isNullOrEmpty()) {
                    return savedLang
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (!currentLocales.isEmpty) {
            val lang = currentLocales[0]?.language ?: java.util.Locale.getDefault().language
            return if (lang == "iw" || lang.startsWith("iw")) "he" else lang
        }
        val sysLang = java.util.Locale.getDefault().language ?: "en"
        return if (sysLang == "iw" || sysLang.startsWith("iw")) "he" else sysLang
    }
}
