package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.arthenica.ffmpegkit.FFmpegKit
import com.example.accessiblevideoeditor.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProcessingState(
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val etaMessage: String = "",
    val isCancellable: Boolean = false,
    val errorLog: String? = null
)

object ProcessingManager {
    var appContext: Context? = null
        private set

    var sharedMediaUri: android.net.Uri? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val _state = MutableStateFlow(ProcessingState())
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    // For backwards compatibility and internal logic
    val isProcessing: Boolean get() = _state.value.isProcessing
    val progress: Float get() = _state.value.progress

    private var currentSessionId: Long? = null
    private var currentJob: Job? = null

    var lastClickedHomeScreenButton: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun showError(log: String) {
        runOnMain {
            _state.value = _state.value.copy(errorLog = log)
        }
    }

    fun dismissError() {
        runOnMain {
            _state.value = _state.value.copy(errorLog = null)
        }
    }

    private var lastBeepPercent = -5

    fun startProcessing(message: String, cancellable: Boolean = true, sessionId: Long? = null, job: Job? = null) {
        runOnMain {
            _state.value = _state.value.copy(
                isProcessing = true,
                progress = 0f,
                statusMessage = message,
                etaMessage = "",
                isCancellable = cancellable
            )
            currentSessionId = sessionId
            currentJob = job
            lastBeepPercent = -5
        }
    }

    fun updateProgress(newProgress: Float, newEta: String = "") {
        runOnMain {
            _state.value = _state.value.copy(
                progress = newProgress,
                etaMessage = if (newEta.isNotBlank()) newEta else _state.value.etaMessage
            )
            
            val percent = (newProgress * 100).toInt()
            if (percent >= lastBeepPercent + 5) {
                com.example.accessiblevideoeditor.media.SoundManager.playProgressBeep(percent)
                lastBeepPercent = percent
            }
        }
    }

    fun updateStatus(message: String) {
        runOnMain {
            _state.value = _state.value.copy(statusMessage = message)
        }
    }

    fun stopProcessing() {
        runOnMain {
            _state.value = _state.value.copy(
                isProcessing = false,
                progress = 0f,
                statusMessage = "",
                etaMessage = "",
                isCancellable = false
            )
            currentSessionId = null
            currentJob = null
        }
    }

    fun updateJob(job: Job?) {
        runOnMain {
            currentJob = job
        }
    }

    fun updateSessionId(sessionId: Long?) {
        runOnMain {
            currentSessionId = sessionId
        }
    }

    fun clearSessionId() {
        runOnMain {
            currentSessionId = null
            currentJob = null
        }
    }

    fun cancelCurrentProcess(context: Context? = null) {
        if (_state.value.isCancellable) {
            if (currentSessionId != null) {
                FFmpegKit.cancel(currentSessionId!!)
            }
            if (currentJob != null) {
                currentJob?.cancel()
            }
            runOnMain {
                _state.value = _state.value.copy(statusMessage = context?.getString(R.string.string_83) ?: "Cancelled")
                stopProcessing()
            }
        } else {
            stopProcessing()
        }
    }
}
