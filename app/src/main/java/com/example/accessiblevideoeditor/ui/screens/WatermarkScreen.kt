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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()
) {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(initialUris.firstOrNull()) }
    var selectedWatermarkUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPosition by remember { mutableStateOf(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_121)) }
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing
    
    var isTextMode by remember { mutableStateOf(false) }
    var textOptions by remember { mutableStateOf(com.example.accessiblevideoeditor.media.TextRenderer.TextOptions(text = "")) }

    val coroutineScope = rememberCoroutineScope()
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedVideoUri = uri }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedWatermarkUri = uri }

    val positions = listOf(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_121), com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_126), com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_119), com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_120))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_21), style = MaterialTheme.typography.titleLarge)
        
        Button(
            onClick = { videoPickerLauncher.launch("video/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedVideoUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_89))
        }

        TabRow(selectedTabIndex = if (isTextMode) 1 else 0) {
            Tab(
                selected = !isTextMode,
                onClick = { isTextMode = false },
                text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_200)) }
            )
            Tab(
                selected = isTextMode,
                onClick = { isTextMode = true },
                text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_199)) }
            )
        }

        if (isTextMode) {
            com.example.accessiblevideoeditor.ui.screens.TextCustomizationPanel(
                onOptionsChanged = { textOptions = it }
            )
        } else {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedWatermarkUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_14) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_24))
            }
        }

        var expanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_60)}: ${positions.find { it == selectedPosition } ?: ""}")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                positions.forEach { pos ->
                    DropdownMenuItem(
                        text = { Text(pos) },
                        onClick = {
                            selectedPosition = pos
                            expanded = false
                        }
                    )
                }
            }
        }

        if (false) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_18))
        } else {
            Button(
                onClick = {
                    /* isProcessing = true */
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val vUri = selectedVideoUri ?: return@launch
                            
                            val inputVideo = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, vUri)
                            val outputPath = context.cacheDir.absolutePath + "/watermark_${System.currentTimeMillis()}.mp4"
                            
                            var inputImage: String? = null
                            if (isTextMode) {
                                val textImgPath = context.cacheDir.absolutePath + "/text_wm_${System.currentTimeMillis()}.png"
                                if (com.example.accessiblevideoeditor.media.TextRenderer.createTickerPng(textOptions, java.io.File(textImgPath))) {
                                    inputImage = textImgPath
                                }
                            } else {
                                val wUri = selectedWatermarkUri ?: return@launch
                                inputImage = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, wUri)
                            }
                            
                            if (inputVideo != null && inputImage != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_74))
                                }
                                
                                val overlayStr = when (selectedPosition) {
                                    com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_126) -> "10:10" // Top Left
                                    com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_121) -> "W-w-10:10" // Top Right
                                    com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_120) -> "10:H-h-10" // Bottom Left
                                    com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_119) -> "W-w-10:H-h-10" // Bottom Right
                                    else -> "10:10"
                                }
                                
                                val commandArgs = arrayOf("-y", "-i", inputVideo, "-i", inputImage, "-filter_complex", "[0:v][1:v]overlay=$overlayStr", "-c:v", "mpeg4", "-q:v", "2", "-c:a", "copy", outputPath)
                                
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs, inputVideo)
                                if (success) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val successMsg = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_182)
                                        android.widget.Toast.makeText(context, successMsg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val errMsg = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_183)
                                        android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    /* isProcessing = false */
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                                }
                            } else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    /* isProcessing = false */
                                }
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = if (isTextMode) selectedVideoUri != null && textOptions.text.isNotBlank() else selectedVideoUri != null && selectedWatermarkUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_53))
            }
        }
    }
}


