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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SlideshowMakerScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(initialUris) }
    var durationPerImage by remember { mutableStateOf("3") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_79), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_132))
        }
        
        if (selectedUris.isNotEmpty()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_8, selectedUris.size))
        }
        
        var addAudio by remember { mutableStateOf(false) }
        var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
        val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedAudioUri = uri }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_99)
            Checkbox(
                checked = addAudio,
                onCheckedChange = { addAudio = it; if (!it) selectedAudioUri = null },
                modifier = Modifier.semantics { contentDescription = desc }
            )
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_99), modifier = Modifier.padding(start = 8.dp))
        }

        if (addAudio) {
            Button(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedAudioUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_85) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_99))
            }
        }

        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = durationPerImage,
            onValueChange = { durationPerImage = it },
            hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_36),
            modifier = Modifier.fillMaxWidth()
        )

        var progress by remember { mutableStateOf(0) }
        val context = androidx.compose.ui.platform.LocalContext.current

        if (isProcessing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_10, progress))
            }
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    progress = 0
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val imagePaths = selectedUris.mapNotNull { 
                            com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, it, "img_${System.currentTimeMillis()}.jpg")?.absolutePath 
                        }
                        var audioPath: String? = null
                        if (selectedAudioUri != null) {
                            audioPath = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, selectedAudioUri!!, "audio_${System.currentTimeMillis()}.mp3")?.absolutePath
                        }
                        if (imagePaths.isNotEmpty()) {
                            val outputPath = context.cacheDir.absolutePath + "/slideshow_${System.currentTimeMillis()}.mp4"
                            val duration = durationPerImage.toIntOrNull() ?: 3
                            val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.createSlideshow(imagePaths, audioPath, duration, outputPath)
                            if (success) {
                                com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.size > 1
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_97))
            }
        }
    }
}
