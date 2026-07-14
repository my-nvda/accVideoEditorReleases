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
import androidx.compose.ui.semantics.LiveRegionMode
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.media.OcrProcessor
import kotlinx.coroutines.launch

@Composable
fun OcrScreen(
    onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    val ocrProcessor = remember { OcrProcessor() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        extractedText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_26), style = MaterialTheme.typography.titleLarge)
        
        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedImageUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_11) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_134))
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_58),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
        } else {
            Button(
                onClick = {
                    selectedImageUri?.let { uri ->
                        isProcessing = true
                        coroutineScope.launch {
                            extractedText = ocrProcessor.extractTextFromImage(context, uri)
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_118))
            }
        }

        if (extractedText.isNotEmpty()) {
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = extractedText,
                onValueChange = {},
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_103),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                readOnly = true,
                minLines = 5
            )
            
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(extractedText)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_141))
            }
        }
    }
}

