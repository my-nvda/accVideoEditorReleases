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
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.R
import kotlinx.coroutines.launch

@Composable
fun BatchProcessScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(initialUris) }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedOperationId by remember { mutableStateOf(R.string.string_51) }

    val multipleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedUris = uris
    }

    val operations = listOf(R.string.string_51, R.string.string_22, R.string.string_95)

    // Calculate real-time overall progress based on ProcessingManager's progress for the active file
    val currentFileProgress = com.example.accessiblevideoeditor.ui.ProcessingManager.progress
    // We need to keep track of current index during calculation. Since the processing runs on background threads,
    // we can save the current index in a state variable inside the processing block.
    var currentIndex by remember { mutableStateOf(0) }
    
    val overallProgress = if (selectedUris.isNotEmpty()) {
        ((currentIndex.toFloat() + currentFileProgress) / selectedUris.size * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_32), style = MaterialTheme.typography.titleLarge)
        
        Button(
            onClick = { multipleMediaPicker.launch("video/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_93))
        }

        if (selectedUris.isNotEmpty()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_6, selectedUris.size), style = MaterialTheme.typography.bodyMedium)
            
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_98), modifier = Modifier.align(Alignment.Start))
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = com.example.accessiblevideoeditor.ui.AppStrings.get(context, selectedOperationId)
                    Text("${com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_142)}: $label")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    operations.forEach { opId ->
                        DropdownMenuItem(
                            text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, opId)) },
                            onClick = {
                                selectedOperationId = opId
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_28, overallProgress.toInt()),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
            LinearProgressIndicator(
                progress = { overallProgress / 100f },
                modifier = Modifier.fillMaxWidth().semantics {
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(overallProgress / 100f, 0f..1f)
                }
            )
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    currentIndex = 0
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_32))
                        }
                        var successCount = 0
                        selectedUris.forEachIndexed { index, uri ->
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                currentIndex = index
                            }
                            val tempFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "batch_temp_${System.currentTimeMillis()}_${index}.mp4")
                            if (tempFile != null) {
                                val inputPath = tempFile.absolutePath
                                val isAudioExtraction = (selectedOperationId == R.string.string_22)
                                val ext = if (isAudioExtraction) "mp3" else "mp4"
                                val outputPath = context.cacheDir.absolutePath + "/batch_${System.currentTimeMillis()}_${index}.$ext"
                                
                                val success = if (isAudioExtraction) {
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                } else if (selectedOperationId == R.string.string_95) {
                                    // Convert to MP4 with optional audio mapping
                                    val commandArgs = arrayOf("-y", "-i", inputPath, "-map", "0:v", "-map", "0:a?", "-c:v", "mpeg4", "-q:v", "2", "-c:a", "aac", outputPath)
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
                                } else {
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.compressVideo(inputPath, outputPath)
                                }

                                if (success) {
                                    val mimeType = if (isAudioExtraction) "audio/mpeg" else "video/mp4"
                                    val savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), mimeType)
                                    if (savedUri != null) {
                                        val opName = when (selectedOperationId) {
                                            R.string.string_22 -> "Extracted Audio (Batch)"
                                            R.string.string_95 -> "Converted Video (Batch)"
                                            else -> "Compressed Video (Batch)"
                                        }
                                        com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                            context,
                                            com.example.accessiblevideoeditor.media.HistoryItem(
                                                uriString = savedUri.toString(),
                                                name = opName,
                                                timestamp = System.currentTimeMillis(),
                                                type = if (isAudioExtraction) "audio" else "video"
                                            )
                                        )
                                    }
                                    successCount++
                                }
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isProcessing = false
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                            if (successCount > 0) {
                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                android.widget.Toast.makeText(context, "${com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182)} ($successCount/${selectedUris.size})", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                com.example.accessiblevideoeditor.media.SoundManager.playError()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.isNotEmpty()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_117))
            }
        }
    }
}
