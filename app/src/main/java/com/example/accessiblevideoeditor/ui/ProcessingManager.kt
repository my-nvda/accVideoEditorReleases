package com.example.accessiblevideoeditor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Job
import android.content.Context
import com.example.accessiblevideoeditor.R

object ProcessingManager {
    var isProcessing by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var statusMessage by mutableStateOf("")
        private set

    var etaMessage by mutableStateOf("")
        private set

    var currentSessionId by mutableStateOf<Long?>(null)
        private set

    var currentJob by mutableStateOf<Job?>(null)
        private set

    var isCancellable by mutableStateOf(false)
        private set

    fun startProcessing(message: String, cancellable: Boolean = false, sessionId: Long? = null, job: Job? = null) {
        isProcessing = true
        progress = 0f
        statusMessage = message
        etaMessage = ""
        isCancellable = cancellable
        currentSessionId = sessionId
        currentJob = job
    }

    private var lastSoundPlayTime = 0L

    fun updateProgress(newProgress: Float, newEta: String = "") {
        progress = newProgress
        if (newEta.isNotBlank()) {
            etaMessage = newEta
        }
        
        val currentMs = System.currentTimeMillis()
        if (currentMs - lastSoundPlayTime > 1500) { // play beep every 1.5 seconds
            com.example.accessiblevideoeditor.media.SoundManager.playProgressBeep((newProgress * 100).toInt())
            lastSoundPlayTime = currentMs
        }
    }

    fun updateStatus(message: String) {
        statusMessage = message
    }

    fun stopProcessing() {
        isProcessing = false
        progress = 0f
        statusMessage = ""
        etaMessage = ""
        currentSessionId = null
        currentJob = null
        isCancellable = false
    }

    fun updateJob(job: Job?) {
        currentJob = job
    }

    fun updateSessionId(sessionId: Long?) {
        currentSessionId = sessionId
    }

    fun clearSessionId() {
        currentSessionId = null
        currentJob = null
    }


    fun cancelCurrentProcess(context: Context? = null) {
        if (isCancellable) {
            if (currentSessionId != null) {
                FFmpegKit.cancel(currentSessionId!!)
            }
            if (currentJob != null) {
                currentJob?.cancel()
            }
            statusMessage = context?.getString(R.string.string_83) ?: "Cancelled"
        } else {
            stopProcessing()
        }
    }
}
