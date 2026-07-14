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
import androidx.compose.ui.platform.LocalContext
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MergeVideosScreen(onBack: () -> Unit, initialUris: List<android.net.Uri> = emptyList()) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(initialUris) }
    var isProcessing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_92), style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_54))
        }
        
        if (selectedUris.isNotEmpty()) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_4, selectedUris.size))
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val inputs = selectedUris.mapNotNull { 
                            com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, it, "merge_temp_${System.currentTimeMillis()}.mp4")?.absolutePath 
                        }
                        if (inputs.size > 1) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_92))
                            }
                            val outputPath = context.cacheDir.absolutePath + "/merged_${System.currentTimeMillis()}.mp4"
                            
                            val inputStr = inputs.joinToString(" ") { "-i \"$it\"" }
                            val filterParts = StringBuilder()
                            val concatParts = StringBuilder()
                            
                            inputs.forEachIndexed { index, _ ->
                                filterParts.append("[$index:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30[v$index];")
                                filterParts.append("[$index:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a$index];")
                                concatParts.append("[v$index][a$index]")
                            }
                            
                            val commandArgs = mutableListOf<String>()
                            inputs.forEach { commandArgs.addAll(listOf("-i", it)) }
                            commandArgs.addAll(listOf("-filter_complex", "${filterParts.toString()}${concatParts.toString()}concat=n=${inputs.size}:v=1:a=1[outv][outa]", "-map", "[outv]", "-map", "[outa]", "-c:v", "mpeg4", "-q:v", "2", outputPath))
                            
                            val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray())
                            if (success) {
                                com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "video/mp4")
                            }
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isProcessing = false
                                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                            }
                        } else {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                isProcessing = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.size > 1
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_139))
            }
        }
    }
}

