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
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.arthenica.ffmpegkit.FFmpegKit
import com.example.accessiblevideoeditor.media.TextRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TickerTextScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(initialUris.firstOrNull()) }
    var textOptions by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_44), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_131))
        }
        
        com.example.accessiblevideoeditor.ui.screens.TextCustomizationPanel(onOptionsChanged = { textOptions = it })
        

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.ProcessingManager.statusMessage)
        } else {
            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri != null && textOptions.text.isNotBlank()) {
                        isProcessing = true
                        com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_62))
                        
                        coroutineScope.launch(Dispatchers.IO) {
                                val inputPath = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/ticker_${System.currentTimeMillis()}.mp4"
                            val pngFile = java.io.File(context.cacheDir, "ticker_${System.currentTimeMillis()}.png")
                            
                            if (inputPath != null) {
                                TextRenderer.createTickerPng(textOptions, pngFile)
                                
                                val yExpr = when (textOptions.position) {
                                    TextRenderer.TextPosition.TOP -> "H/10"
                                    TextRenderer.TextPosition.CENTER -> "(H-h)/2"
                                    TextRenderer.TextPosition.BOTTOM -> "H-H/10-h"
                                }
                                val command = "-y -i \"${inputPath}\" -i \"${pngFile.absolutePath}\" -filter_complex \"[0:v]scale=trunc(iw/2)*2:trunc(ih/2)*2[main];[1:v]format=rgba[img];[main][img]overlay=x='W-mod(t*150,W+w)':y='$yExpr'\" -c:v mpeg4 -q:v 2 -c:a copy \"${outputPath}\""
                                
                                val session = FFmpegKit.execute(command)
                                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_240), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val logs = session.failStackTrace ?: session.allLogsAsString ?: "Unknown Error"
                                    val detailedLog = "Command:\n$command\n\nLogs:\n$logs"
                                    withContext(Dispatchers.Main) {
                                        com.example.accessiblevideoeditor.ui.ProcessingManager.showError(detailedLog)
                                        android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_241), android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                                }
                            } else {
                                withContext(Dispatchers.Main) { isProcessing = false; com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing() }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null && textOptions.text.isNotBlank()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_62))
            }
        }
    }
}



