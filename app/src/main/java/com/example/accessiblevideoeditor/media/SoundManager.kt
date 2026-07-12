package com.example.accessiblevideoeditor.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.accessiblevideoeditor.R

object SoundManager {
    private var soundPool: SoundPool? = null
    
    private var startupSoundId: Int = 0
    private var processingSoundId: Int = 0
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0

    private var isStartupLoaded = false

    fun init(context: Context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION) // For UI sounds
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == startupSoundId) {
                isStartupLoaded = true
                if (com.example.accessiblevideoeditor.ui.SettingsManager.isStartupSoundEnabled) {
                    soundPool?.play(startupSoundId, 1f, 1f, 1, 0, 1f)
                }
            }
        }

        // Load sounds from raw resources
        soundPool?.let {
            startupSoundId = it.load(context, R.raw.sound_startup, 1)
            processingSoundId = it.load(context, R.raw.sound_processing, 1)
            successSoundId = it.load(context, R.raw.sound_success, 1)
            errorSoundId = it.load(context, R.raw.sound_error, 1)
        }
    }

    fun playStartup() {
        if (isStartupLoaded && com.example.accessiblevideoeditor.ui.SettingsManager.isStartupSoundEnabled) {
            soundPool?.play(startupSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playProcessing() {
        if (com.example.accessiblevideoeditor.ui.SettingsManager.isProcessingSoundEnabled) {
            soundPool?.play(processingSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Plays the processing beep with increasing pitch like NVDA.
     * @param percentage 0 to 100
     */
    fun playProgressBeep(percentage: Int) {
        if (com.example.accessiblevideoeditor.ui.SettingsManager.isProcessingSoundEnabled) {
            // Rate range is 0.5 to 2.0. We map 0-100% to 0.5-2.0
            val rate = 0.5f + (percentage / 100f) * 1.5f
            soundPool?.play(processingSoundId, 0.5f, 0.5f, 1, 0, rate)
        }
    }

    fun playSuccess() {
        if (com.example.accessiblevideoeditor.ui.SettingsManager.isSuccessSoundEnabled) {
            soundPool?.play(successSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun playError() {
        if (com.example.accessiblevideoeditor.ui.SettingsManager.isErrorSoundEnabled) {
            soundPool?.play(errorSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
