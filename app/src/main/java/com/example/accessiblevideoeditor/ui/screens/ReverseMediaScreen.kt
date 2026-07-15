package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.accessiblevideoeditor.ui.components.AccessibleCheckboxRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReverseMediaScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(initialUris.firstOrNull()) }
    var reverseVideo by remember { mutableStateOf(true) }
    var reverseAudio by remember { mutableStateOf(true) }
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_65), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_131))
        }
        
        AccessibleCheckboxRow(text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_122), checked = reverseVideo, onCheckedChange = { reverseVideo = it })
        
        AccessibleCheckboxRow(text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_138), checked = reverseAudio, onCheckedChange = { reverseAudio = it })

        if (false) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
        } else {
            Button(
                onClick = {
                    /* isProcessing = true */
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val uri = selectedUri ?: return@launch
                            val input = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, uri)
                            if (input != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_68))
                                }
                                val outputPath = context.cacheDir.absolutePath + "/reverse_${System.currentTimeMillis()}.mp4"
                                
                                val commandArgs = mutableListOf("-y", "-i", input)
                                if (reverseVideo) {
                                    commandArgs.add("-vf")
                                    commandArgs.add("reverse")
                                }
                                if (reverseAudio) {
                                    commandArgs.add("-af")
                                    commandArgs.add("areverse")
                                }
                                commandArgs.add(outputPath)
                                
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), input)
                                
                                if (success) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, context.getString(com.example.accessiblevideoeditor.R.string.string_222), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, context.getString(com.example.accessiblevideoeditor.R.string.string_223), android.widget.Toast.LENGTH_LONG).show()
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
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null && (reverseVideo || reverseAudio)
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_137))
            }
        }
    }
}

