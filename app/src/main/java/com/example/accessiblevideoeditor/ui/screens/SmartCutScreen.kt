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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmartCutScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(initialUris.firstOrNull()) }
    var silenceThreshold by remember { mutableStateOf("-30") }
    var minSilenceDuration by remember { mutableStateOf("0.5") }
    var isProcessing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedVideoUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_42), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedVideoUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_131))
        }

        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = silenceThreshold,
            onValueChange = { silenceThreshold = it },
            hint = "Silence Threshold (e.g. -30)",
            modifier = Modifier.fillMaxWidth()
        )

        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = minSilenceDuration,
            onValueChange = { minSilenceDuration = it },
            hint = "Min Silence Duration (e.g. 0.5)",
            modifier = Modifier.fillMaxWidth()
        )

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val uri = selectedVideoUri ?: return@launch
                        val tempFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "temp_video_${System.currentTimeMillis()}.mp4")
                        val input = tempFile?.absolutePath
                        if (input != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_42))
                            }
                            val outputPath = context.cacheDir.absolutePath + "/smartcut_${System.currentTimeMillis()}.mp4"
                            
                            val success = com.example.accessiblevideoeditor.media.SmartCutProcessor.removeSilence(
                                context = context,
                                inputPath = input,
                                outputPath = outputPath,
                                thresholdDb = silenceThreshold.toIntOrNull() ?: -30,
                                durationSec = minSilenceDuration.toFloatOrNull() ?: 0.5f
                            )
                            
                            if (success) {
                                com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_240), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_241), android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isProcessing = false
                                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                            }
                        } else {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { isProcessing = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedVideoUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_81))
            }
        }
    }
}

