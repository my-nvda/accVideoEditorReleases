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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastConverterScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(initialUris.firstOrNull()) }
    var selectedFormat by remember { mutableStateOf("MP4") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val formats = listOf("MP4", "MKV", "AVI", "GIF")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_101), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_88) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_140))
        }
        
        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_114)}: $selectedFormat")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                formats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = { selectedFormat = format; expanded = false }
                    )
                }
            }
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        selectedUri?.let { uri ->
                            val tempFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, uri, "temp_fast_conv_${System.currentTimeMillis()}.mp4")
                            if (tempFile != null) {
                                val inputPath = tempFile.absolutePath
                                val fileName = "converted_${System.currentTimeMillis()}"
                                val outputPath = context.cacheDir.absolutePath + "/" + fileName + "." + selectedFormat.lowercase()
                                
                                val commandArgs = arrayOf("-y", "-i", inputPath, outputPath)
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
                                if (success) {
                                    val mimeType = if (selectedFormat == "GIF") "image/gif" else "video/${selectedFormat.lowercase()}"
                                    val savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), mimeType)
                                    if (savedUri != null) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_176), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            com.example.accessiblevideoeditor.media.SoundManager.playError()
                                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_177), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        com.example.accessiblevideoeditor.media.SoundManager.playError()
                                        android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_177), android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_147))
            }
        }
    }
}
