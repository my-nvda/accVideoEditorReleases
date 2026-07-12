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
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProcessScreen(
    onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()
) {
    val context = LocalContext.current
    var selectedUris by remember { mutableStateOf<List<Uri>>(initialUris) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var selectedOperation by remember { mutableStateOf(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_51)) }

    val coroutineScope = rememberCoroutineScope()
    
    val multipleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedUris = uris
    }

    val operations = listOf(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_51), com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_22), com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_95))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_12), style = MaterialTheme.typography.titleLarge)
        
        Button(
            onClick = { multipleMediaPicker.launch("video/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_93))
        }

        if (selectedUris.isNotEmpty()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_6, selectedUris.size), style = MaterialTheme.typography.bodyMedium)
            
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_98), modifier = Modifier.align(Alignment.Start))
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_142)}: $selectedOperation")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    operations.forEach { op ->
                        DropdownMenuItem(
                            text = { Text(op) },
                            onClick = {
                                selectedOperation = op
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_28, progress))
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    progress = 0
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        selectedUris.forEachIndexed { index, uri ->
                            val tempFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "batch_temp_${System.currentTimeMillis()}.mp4")
                            if (tempFile != null) {
                                val inputPath = tempFile.absolutePath
                                val isAudioExtraction = selectedOperation.contains(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_146))
                                val ext = if (isAudioExtraction) "mp3" else "mp4"
                                val outputPath = context.cacheDir.absolutePath + "/batch_${System.currentTimeMillis()}_${index}.$ext"
                                if (isAudioExtraction) {
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "audio/mpeg")
                                } else if (selectedOperation.contains("MP4")) {
                                    val commandArgs = arrayOf("-y", "-i", inputPath, "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", outputPath)
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                } else {
                                    com.example.accessiblevideoeditor.media.FFmpegProcessor.compressVideo(inputPath, outputPath)
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                progress = ((index + 1) * 100) / selectedUris.size
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isProcessing = false
                            com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_176), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.isNotEmpty()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_117))
            }
        }
    }
}
