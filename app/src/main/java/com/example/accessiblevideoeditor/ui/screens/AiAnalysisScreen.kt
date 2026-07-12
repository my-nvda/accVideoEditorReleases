package com.example.accessiblevideoeditor.ui.screens

import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun extractVideoFrames(context: android.content.Context, videoUri: Uri): List<android.graphics.Bitmap> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    val frames = mutableListOf<android.graphics.Bitmap>()
    try {
        retriever.setDataSource(context, videoUri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = durationStr?.toLongOrNull() ?: 0L
        if (duration > 0) {
            for (i in 1..3) {
                val timeUs = (duration * 1000 * i) / 4
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    frames.add(bitmap)
                }
            }
        } else {
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) frames.add(bitmap)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        retriever.release()
    }
    frames
}

@Composable
fun AiAnalysisScreen(onBack: () -> Unit = {}, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var userQuestion by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(SettingsManager.geminiApiKey) }
    var generatedDescription by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImage = uri
            selectedVideo = null
        }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideo = uri
            selectedImage = null
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).height(60.dp)
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_124))
            }
            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier.weight(1f).height(60.dp)
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_113))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = userQuestion,
            onValueChange = { userQuestion = it },
            hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_5),
            modifier = Modifier.fillMaxWidth() // Removed contentDescription to let TalkBack read the label naturally
        )

        Spacer(modifier = Modifier.height(16.dp))

        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = apiKey,
            onValueChange = { 
                apiKey = it
                SettingsManager.geminiApiKey = it 
            },
            hint = "Gemini API Key",
            modifier = Modifier.fillMaxWidth() // Removed contentDescription to let TalkBack read the label naturally
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_91), cancellable = true)
                    ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                    try {
                        val model = GenerativeModel(
                            modelName = "gemini-2.5-flash",
                            apiKey = apiKey.trim()
                        )
                        val bitmaps = if (selectedImage != null) {
                            withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(selectedImage!!)
                                listOf(android.graphics.BitmapFactory.decodeStream(inputStream))
                            }
                        } else if (selectedVideo != null) {
                            extractVideoFrames(context, selectedVideo!!)
                        } else {
                            emptyList()
                        }
                        
                        val inputContent = content {
                            bitmaps.forEach { image(it) }
                            val promptText = if (userQuestion.isNotBlank()) userQuestion else com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_1)
                            text(promptText)
                        }
                        
                        var response = ""
                        try {
                            response = model.generateContent(inputContent).text ?: com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_66)
                        } catch (e: Exception) {
                            // Fallback to gemini-2.0-flash
                            val fallbackModel = GenerativeModel(
                                modelName = "gemini-2.0-flash",
                                apiKey = apiKey.trim()
                            )
                            response = fallbackModel.generateContent(inputContent).text ?: com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_66)
                        }
                        
                        generatedDescription = response
                    } catch (e: Exception) {
                        generatedDescription = com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_56, e.message ?: "")
                    } finally {
                        ProcessingManager.stopProcessing()
                    }
                }
            },
            enabled = (selectedImage != null || selectedVideo != null) && apiKey.isNotBlank() && !ProcessingManager.isProcessing,
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_50))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (generatedDescription.isNotEmpty()) {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = generatedDescription,
                onValueChange = {},
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_103),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                readOnly = true,
                minLines = 5
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedDescription)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_141))
            }
        }
    }
}
