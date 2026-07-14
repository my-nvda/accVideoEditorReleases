package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.components.AccessibleTextField
import com.example.accessiblevideoeditor.utils.FileUtils

@Composable
fun SlideshowMakerScreen(onBack: () -> Unit, initialUris: List<Uri> = emptyList()) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(initialUris) }
    var durationPerImage by remember { mutableStateOf("3") }
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(AppStrings.get(context, R.string.string_79), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(AppStrings.get(context, R.string.string_132))
        }
        
        if (selectedUris.isNotEmpty()) {
            Text(AppStrings.get(context, R.string.string_8, selectedUris.size))
        }
        
        var addAudio by remember { mutableStateOf(false) }
        var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
        val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedAudioUri = uri }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val desc = AppStrings.get(context, R.string.string_99)
            Checkbox(
                checked = addAudio,
                onCheckedChange = { addAudio = it; if (!it) selectedAudioUri = null },
                modifier = Modifier.semantics { contentDescription = desc }
            )
            Text(AppStrings.get(context, R.string.string_99), modifier = Modifier.padding(start = 8.dp))
        }

        if (addAudio) {
            Button(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedAudioUri != null) AppStrings.get(context, R.string.string_85) else AppStrings.get(context, R.string.string_99))
            }
        }

        AccessibleTextField(
            value = durationPerImage,
            onValueChange = { durationPerImage = it },
            hint = AppStrings.get(context, R.string.string_36),
            modifier = Modifier.fillMaxWidth()
        )

        val progress = (ProcessingManager.progress * 100).toInt()

        if (false) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val desc = AppStrings.get(context, R.string.string_111)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().semantics {
                        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress / 100f, 0f..1f)
                    }
                )
                Text(
                    text = AppStrings.get(context, R.string.string_10, progress),
                    modifier = Modifier.semantics {
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                    }
                )
            }
        } else {
            Button(
                onClick = {
                    /* isProcessing = true */
                    ProcessingManager.startProcessing(AppStrings.get(context, R.string.string_111), true)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val imagePaths = selectedUris.mapIndexedNotNull { index, uri -> 
                                MediaUtils.copyUriToTempFile(context, uri, "img_${System.currentTimeMillis()}_$index.jpg")?.absolutePath 
                            }
                            var audioPath: String? = null
                            if (selectedAudioUri != null) {
                                audioPath = MediaUtils.copyUriToTempFile(context, selectedAudioUri!!, "audio_${System.currentTimeMillis()}.mp3")?.absolutePath
                            }
                            if (imagePaths.isNotEmpty()) {
                                val outputPath = context.cacheDir.absolutePath + "/slideshow_${System.currentTimeMillis()}.mp4"
                                val duration = durationPerImage.toIntOrNull() ?: 3
                                val success = FFmpegProcessor.createSlideshow(imagePaths, audioPath, duration, outputPath)
                                if (success) {
                                    FileUtils.saveToGallery(context, File(outputPath), "video/mp4")
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                ProcessingManager.showError(e.message ?: "Unknown error occurred")
                            }
                        } finally {
                            withContext(Dispatchers.Main) {
                                /* isProcessing = false */
                                ProcessingManager.stopProcessing()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.size > 1
            ) {
                Text(AppStrings.get(context, R.string.string_97))
            }
        }
    }
}
