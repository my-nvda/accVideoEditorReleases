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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpeechToTextScreen(
    onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()
) {
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var transcribedText by remember { mutableStateOf("") }
    val isProcessing = com.example.accessiblevideoeditor.ui.ProcessingManager.isProcessing

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedMediaUri = uri
        transcribedText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_40), style = MaterialTheme.typography.titleLarge)
        
        Button(
            onClick = { mediaPickerLauncher.launch("audio/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedMediaUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_16) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_99))
        }

        if (false) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_30),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
        } else {
            Button(
                onClick = {
                    selectedMediaUri?.let { uri ->
                        /* isProcessing = true */
                        coroutineScope.launch {
                            try {
                                val apiKey = com.example.accessiblevideoeditor.ui.SettingsManager.geminiApiKey
                                if (apiKey.isBlank()) {
                                    transcribedText = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_3)
                                    /* isProcessing = false */
                                    return@launch
                                }
                                val model = com.google.ai.client.generativeai.GenerativeModel(
                                    modelName = "gemini-2.5-flash",
                                    apiKey = apiKey
                                )
                                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    inputStream?.readBytes() ?: ByteArray(0)
                                }
                                val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
                                val inputContent = com.google.ai.client.generativeai.type.content {
                                    blob(mimeType, bytes)
                                    text(com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_2))
                                }
                                transcribedText = model.generateContent(inputContent).text ?: com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_71)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val errorMsg = e.message ?: ""
                                if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("Unexpected Response")) {
                                    transcribedText = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_228)
                                } else {
                                    transcribedText = com.example.accessiblevideoeditor.ui.AppStrings.get(context, com.example.accessiblevideoeditor.R.string.string_73, errorMsg)
                                }
                            }
                            /* isProcessing = false */
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMediaUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_123))
            }
        }

        if (transcribedText.isNotEmpty()) {
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = transcribedText,
                onValueChange = {},
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_103),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                readOnly = true,
                minLines = 5
            )
            
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(transcribedText)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_141))
            }
        }
    }
}

