package com.example.accessiblevideoeditor.ui

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

    fun setLanguage(languageCode: String) {
        val localeList = LocaleListCompat.create(Locale.forLanguageTag(languageCode))
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getCurrentLanguageCode(): String {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        return if (!currentLocales.isEmpty) {
            currentLocales[0]?.toLanguageTag() ?: "en"
        } else {
            "en"
        }
    }
}
