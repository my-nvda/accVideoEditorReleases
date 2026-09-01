package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "accessible_video_editor_prefs"
    private const val KEY_SOUND_STARTUP = "sound_startup_enabled"
    private const val KEY_SOUND_PROCESSING = "sound_processing_enabled"
    private const val KEY_SOUND_SUCCESS = "sound_success_enabled"
    private const val KEY_SOUND_ERROR = "sound_error_enabled"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    private const val KEY_WIT_AI_TOKEN = "wit_ai_token"
    private const val KEY_OPENAI_KEY = "openai_key"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    private const val KEY_EXPORT_QUALITY = "export_quality"
    private const val KEY_SHOULD_ASK_QUALITY = "should_ask_export_quality"

    private lateinit var prefs: SharedPreferences

    // App configuration states
    var isStartupSoundEnabledState = true
        private set
    var isProcessingSoundEnabledState = true
        private set
    var isSuccessSoundEnabledState = true
        private set
    var isErrorSoundEnabledState = true
        private set
        
    var isDarkModeState = true
        private set

    var witAiTokenState = ""
        private set
    var openAiKeyState = ""
        private set
    var geminiApiKeyState = ""
        private set
    var geminiModelState = "gemini-3.5-flash"
        private set
    var exportQualityState = "high"
        private set
    var shouldAskExportQualityState = true
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isStartupSoundEnabledState = prefs.getBoolean(KEY_SOUND_STARTUP, true)
        isProcessingSoundEnabledState = prefs.getBoolean(KEY_SOUND_PROCESSING, true)
        isSuccessSoundEnabledState = prefs.getBoolean(KEY_SOUND_SUCCESS, true)
        isErrorSoundEnabledState = prefs.getBoolean(KEY_SOUND_ERROR, true)
        isDarkModeState = prefs.getBoolean(KEY_DARK_MODE, true)
        witAiTokenState = prefs.getString(KEY_WIT_AI_TOKEN, "") ?: ""
        openAiKeyState = prefs.getString(KEY_OPENAI_KEY, "") ?: ""
        geminiApiKeyState = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        geminiModelState = prefs.getString(KEY_GEMINI_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
        exportQualityState = prefs.getString(KEY_EXPORT_QUALITY, "high") ?: "high"
        shouldAskExportQualityState = prefs.getBoolean(KEY_SHOULD_ASK_QUALITY, true)
    }

    var isStartupSoundEnabled: Boolean
        get() = isStartupSoundEnabledState
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_STARTUP, value).apply()
            isStartupSoundEnabledState = value
        }

    var isProcessingSoundEnabled: Boolean
        get() = isProcessingSoundEnabledState
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_PROCESSING, value).apply()
            isProcessingSoundEnabledState = value
        }

    var isSuccessSoundEnabled: Boolean
        get() = isSuccessSoundEnabledState
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_SUCCESS, value).apply()
            isSuccessSoundEnabledState = value
        }

    var isErrorSoundEnabled: Boolean
        get() = isErrorSoundEnabledState
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ERROR, value).apply()
            isErrorSoundEnabledState = value
        }

    var isDarkMode: Boolean
        get() = isDarkModeState
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            isDarkModeState = value
        }

    var witAiToken: String
        get() = witAiTokenState
        set(value) {
            prefs.edit().putString(KEY_WIT_AI_TOKEN, value).apply()
            witAiTokenState = value
        }

    var openAiKey: String
        get() = openAiKeyState
        set(value) {
            prefs.edit().putString(KEY_OPENAI_KEY, value).apply()
            openAiKeyState = value
        }

    var geminiApiKey: String
        get() = geminiApiKeyState
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
            geminiApiKeyState = value
        }

    var geminiModel: String
        get() = geminiModelState
        set(value) {
            prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()
            geminiModelState = value
        }

    var exportQuality: String
        get() = exportQualityState
        set(value) {
            prefs.edit().putString(KEY_EXPORT_QUALITY, value).apply()
            exportQualityState = value
        }

    var shouldAskExportQuality: Boolean
        get() = shouldAskExportQualityState
        set(value) {
            prefs.edit().putBoolean(KEY_SHOULD_ASK_QUALITY, value).apply()
            shouldAskExportQualityState = value
        }
}

