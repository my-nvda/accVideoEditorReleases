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

    private lateinit var prefs: SharedPreferences

    // Observable states for Compose
    var isStartupSoundEnabledState = androidx.compose.runtime.mutableStateOf(true)
        private set
    var isProcessingSoundEnabledState = androidx.compose.runtime.mutableStateOf(true)
        private set
    var isSuccessSoundEnabledState = androidx.compose.runtime.mutableStateOf(true)
        private set
    var isErrorSoundEnabledState = androidx.compose.runtime.mutableStateOf(true)
        private set
        
    var isDarkModeState = androidx.compose.runtime.mutableStateOf(true)
        private set

    var witAiTokenState = androidx.compose.runtime.mutableStateOf("")
        private set
    var openAiKeyState = androidx.compose.runtime.mutableStateOf("")
        private set
    var geminiApiKeyState = androidx.compose.runtime.mutableStateOf("")
        private set
    var geminiModelState = androidx.compose.runtime.mutableStateOf("gemini-2.5-flash")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isStartupSoundEnabledState.value = prefs.getBoolean(KEY_SOUND_STARTUP, true)
        isProcessingSoundEnabledState.value = prefs.getBoolean(KEY_SOUND_PROCESSING, true)
        isSuccessSoundEnabledState.value = prefs.getBoolean(KEY_SOUND_SUCCESS, true)
        isErrorSoundEnabledState.value = prefs.getBoolean(KEY_SOUND_ERROR, true)
        isDarkModeState.value = prefs.getBoolean(KEY_DARK_MODE, true)
        witAiTokenState.value = prefs.getString(KEY_WIT_AI_TOKEN, "") ?: ""
        openAiKeyState.value = prefs.getString(KEY_OPENAI_KEY, "") ?: ""
        geminiApiKeyState.value = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        geminiModelState.value = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    var isStartupSoundEnabled: Boolean
        get() = isStartupSoundEnabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_STARTUP, value).apply()
            isStartupSoundEnabledState.value = value
        }

    var isProcessingSoundEnabled: Boolean
        get() = isProcessingSoundEnabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_PROCESSING, value).apply()
            isProcessingSoundEnabledState.value = value
        }

    var isSuccessSoundEnabled: Boolean
        get() = isSuccessSoundEnabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_SUCCESS, value).apply()
            isSuccessSoundEnabledState.value = value
        }

    var isErrorSoundEnabled: Boolean
        get() = isErrorSoundEnabledState.value
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ERROR, value).apply()
            isErrorSoundEnabledState.value = value
        }

    var isDarkMode: Boolean
        get() = isDarkModeState.value
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            isDarkModeState.value = value
        }

    var witAiToken: String
        get() = witAiTokenState.value
        set(value) {
            prefs.edit().putString(KEY_WIT_AI_TOKEN, value).apply()
            witAiTokenState.value = value
        }

    var openAiKey: String
        get() = openAiKeyState.value
        set(value) {
            prefs.edit().putString(KEY_OPENAI_KEY, value).apply()
            openAiKeyState.value = value
        }

    var geminiApiKey: String
        get() = geminiApiKeyState.value
        set(value) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()
            geminiApiKeyState.value = value
        }

    var geminiModel: String
        get() = geminiModelState.value
        set(value) {
            prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()
            geminiModelState.value = value
        }
}
