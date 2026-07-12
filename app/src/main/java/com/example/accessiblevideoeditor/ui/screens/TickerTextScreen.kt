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
    var text by remember { mutableStateOf("") }
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
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_44), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_131))
        }
        
        com.example.accessiblevideoeditor.ui.screens.TextCustomizationPanel(onOptionsChanged = { textOptions = it.copy(text = textOptions.text) })
        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = text,
            onValueChange = { text = it; textOptions = textOptions.copy(text = it) },
            hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_107),
            modifier = Modifier.fillMaxWidth()
        )

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.ProcessingManager.statusMessage)
        } else {
            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri != null && text.isNotBlank()) {
                        isProcessing = true
                        com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_62))
                        
                        coroutineScope.launch(Dispatchers.IO) {
                                val inputPath = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/ticker_${System.currentTimeMillis()}.mp4"
                            val pngFile = java.io.File(context.cacheDir, "ticker_${System.currentTimeMillis()}.png")
                            
                            if (inputPath != null) {
                                TextRenderer.createTickerPng(textOptions.copy(text = text), pngFile)
                                
                                val yExpr = when (textOptions.position) {
                                    TextRenderer.TextPosition.TOP -> "h/10"
                                    TextRenderer.TextPosition.CENTER -> "(h-th)/2"
                                    TextRenderer.TextPosition.BOTTOM -> "h-h/10-th"
                                }
                                val command = "-y -i \"${inputPath}\" -i \"${pngFile.absolutePath}\" -filter_complex \"[1:v]format=rgba[img];[0:v][img]overlay=x='w-mod(t*150,w+tw)':y='$yExpr'\" -c:v libx264 -preset fast -pix_fmt yuv420p -c:a copy \"${outputPath}\""
                                
                                val session = FFmpegKit.execute(command)
                                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "تمت العملية بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "حدث خطأ أثناء معالجة الفيديو", android.widget.Toast.LENGTH_LONG).show()
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
                enabled = selectedUri != null && text.isNotBlank()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_62))
            }
        }
    }
}
