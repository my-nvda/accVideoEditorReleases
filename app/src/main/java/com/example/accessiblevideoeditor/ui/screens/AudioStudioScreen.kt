package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

@Composable
fun AudioStudioScreen(onBack: () -> Unit = {}, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing
    val progress = (com.example.accessiblevideoeditor.ui.ProcessingManager.progress * 100).toInt()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Audio Recorder States
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) } // in seconds
    var selectedOutputFormat by remember { mutableStateOf("M4A") } // M4A, WAV, MP3
    var selectedChannels by remember { mutableStateOf("MONO") } // MONO, STEREO
    var activeRecordingPath by remember { mutableStateOf<String?>(null) }
    var wavePhase by remember { mutableStateOf(0f) }

    // Waveform Animation Effect
    LaunchedEffect(isRecording, isRecordingPaused) {
        if (isRecording && !isRecordingPaused) {
            while (true) {
                kotlinx.coroutines.delay(30)
                wavePhase += 0.2f
            }
        }
    }

    // Timer Effect
    LaunchedEffect(isRecording, isRecordingPaused) {
        if (isRecording && !isRecordingPaused) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                recordingTime += 1
            }
        }
    }

    // Clean up recorder on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Format time (MM:SS)
    val formattedTime = remember(recordingTime) {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedMediaUri = uri }

    fun startRecordingAudio() {
        try {
            val audioChannels = if (selectedChannels == "STEREO") 2 else 1
            val tempFile = File(context.cacheDir, "recording_temp_${System.currentTimeMillis()}.m4a")
            activeRecordingPath = tempFile.absolutePath

            val recorderInstance = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }

            mediaRecorder = recorderInstance.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(audioChannels)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            isRecordingPaused = false
            recordingTime = 0L
            com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecordingAudio() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            isRecordingPaused = false

            val inputPath = activeRecordingPath
            if (inputPath != null) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                            com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_57)
                        )
                    }

                    val outputFormat = selectedOutputFormat.lowercase()
                    val finalFileName = "Recorded_Audio_${System.currentTimeMillis()}.$outputFormat"
                    val finalOutputPath = File(context.cacheDir, finalFileName).absolutePath

                    val success = if (outputFormat == "m4a") {
                        File(inputPath).renameTo(File(finalOutputPath))
                    } else {
                        val cmd = if (outputFormat == "wav") {
                            arrayOf("-y", "-i", inputPath, "-c:a", "pcm_s16le", finalOutputPath)
                        } else {
                            arrayOf("-y", "-i", inputPath, "-c:a", "libmp3lame", "-q:a", "2", finalOutputPath)
                        }
                        FFmpegProcessor.executeWithProgress(cmd, inputPath)
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        if (success) {
                            val mime = when (outputFormat) {
                                "wav" -> "audio/x-wav"
                                "mp3" -> "audio/mpeg"
                                else -> "audio/mp4"
                            }
                            val savedUri = FileUtils.saveToGallery(context, File(finalOutputPath), mime)
                            if (savedUri != null) {
                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182), Toast.LENGTH_SHORT).show()
                                selectedMediaUri = savedUri
                            }
                        } else {
                            com.example.accessiblevideoeditor.media.SoundManager.playError()
                            Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error stopping recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun pauseRecordingAudio() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                isRecordingPaused = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(context, "Pause not supported on this version", Toast.LENGTH_SHORT).show()
        }
    }

    fun resumeRecordingAudio() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                isRecordingPaused = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelRecordingAudio() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        isRecordingPaused = false
        recordingTime = 0L
        activeRecordingPath?.let { File(it).delete() }
        activeRecordingPath = null
        Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecordingAudio()
        } else {
            Toast.makeText(context, "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_106), style = MaterialTheme.typography.titleLarge)

        // ----------------- VOICE RECORDER CARD -----------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_title), style = MaterialTheme.typography.titleMedium)

                if (!isRecording) {
                    // Config Panel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_channels))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedChannels == "MONO",
                                onClick = { selectedChannels = "MONO" },
                                label = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_mono)) }
                            )
                            FilterChip(
                                selected = selectedChannels == "STEREO",
                                onClick = { selectedChannels = "STEREO" },
                                label = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_stereo)) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_format))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("M4A", "WAV", "MP3").forEach { fmt ->
                                FilterChip(
                                    selected = selectedOutputFormat == fmt,
                                    onClick = { selectedOutputFormat = fmt },
                                    label = { Text(fmt) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            )
                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                startRecordingAudio()
                            } else {
                                requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_start))
                    }
                } else {
                    // Active Recording UI
                    val statusText = if (isRecordingPaused) {
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_paused_status, formattedTime)
                    } else {
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_recording_status, formattedTime)
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics {
                            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                            contentDescription = statusText
                        }
                    )

                    // Waveform simulation
                    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                        val width = size.width
                        val height = size.height
                        val points = 80
                        val path = androidx.compose.ui.graphics.Path()
                        val amp = if (isRecordingPaused) 2f else 25f
                        path.moveTo(0f, height / 2)
                        for (i in 0..points) {
                            val x = (width / points) * i
                            val y = (height / 2) + sin(x * 0.08f + wavePhase) * amp
                            path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF00E5FF),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            Button(
                                onClick = {
                                    if (isRecordingPaused) resumeRecordingAudio() else pauseRecordingAudio()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (isRecordingPaused) {
                                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_resume)
                                    } else {
                                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_pause)
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = { stopRecordingAudio() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_stop))
                        }
                    }

                    OutlinedButton(
                        onClick = { cancelRecordingAudio() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_rec_cancel))
                    }
                }
            }
        }

        // ----------------- FILE SELECTOR -----------------
        Button(onClick = { pickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedMediaUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_88) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_47))
        }

        if (false) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_28, progress),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
        } else {
            Button(
                onClick = {
                    selectedMediaUri?.let { uri ->
                        coroutineScope.launch {
                            try {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_15))
                                }
                                val inputPath = FileUtils.getPathFromUri(context, uri)
                                val outputPath = context.cacheDir.absolutePath + "/extracted_audio_${System.currentTimeMillis()}.mp3"
                                if (inputPath != null) {
                                    val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                    if (success) {
                                        val savedUri = FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                        if (savedUri != null) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182), android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.example.accessiblevideoeditor.media.SoundManager.playError()
                                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183), android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            com.example.accessiblevideoeditor.media.SoundManager.playError()
                                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                e.printStackTrace()
                            } finally {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMediaUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_15))
            }

            Button(
                onClick = {
                    selectedMediaUri?.let { uri ->
                        coroutineScope.launch {
                            try {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_39))
                                }
                                val inputPath = FileUtils.getPathFromUri(context, uri)
                                val outputPath = context.cacheDir.absolutePath + "/bass_boosted_${System.currentTimeMillis()}.mp3"
                                if (inputPath != null) {
                                    val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.applyAudioStudioEffects(inputPath, "bass_boost", outputPath)
                                    if (success) {
                                        val savedUri = FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                        if (savedUri != null) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182), android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.example.accessiblevideoeditor.media.SoundManager.playError()
                                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183), android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            com.example.accessiblevideoeditor.media.SoundManager.playError()
                                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                e.printStackTrace()
                            } finally {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMediaUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_39))
            }
        }
    }
}
