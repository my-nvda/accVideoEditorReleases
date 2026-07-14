package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.utils.FileUtils
import java.io.File

@Composable
fun AudioStudioScreen(onBack: () -> Unit = {}, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val progress = (com.example.accessiblevideoeditor.ui.ProcessingManager.progress * 100).toInt()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedMediaUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_106), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { pickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedMediaUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_88) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_47))
        }

        if (isProcessing) {
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
                        isProcessing = true
                        coroutineScope.launch {
                            val inputPath = FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/extracted_audio_${System.currentTimeMillis()}.mp3"
                            if (inputPath != null) {
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                if (success) {
                                    val savedUri = FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                    if (savedUri != null) {
                                        com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                            context,
                                            com.example.accessiblevideoeditor.media.HistoryItem(
                                                uriString = savedUri.toString(),
                                                name = "Extracted Audio (MP3)",
                                                timestamp = System.currentTimeMillis(),
                                                type = "audio"
                                            )
                                        )
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
                            isProcessing = false
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
                        isProcessing = true
                        coroutineScope.launch {
                            val inputPath = FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/bass_boosted_${System.currentTimeMillis()}.mp3"
                            if (inputPath != null) {
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.applyAudioStudioEffects(inputPath, "bass_boost", outputPath)
                                if (success) {
                                    val savedUri = FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                    if (savedUri != null) {
                                        com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                            context,
                                            com.example.accessiblevideoeditor.media.HistoryItem(
                                                uriString = savedUri.toString(),
                                                name = "Bass Boosted Audio",
                                                timestamp = System.currentTimeMillis(),
                                                type = "audio"
                                            )
                                        )
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
                            isProcessing = false
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

