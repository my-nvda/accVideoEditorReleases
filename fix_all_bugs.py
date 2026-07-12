import os

def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

# 1. ImageEditorScreen.kt
img_path = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens\ImageEditorScreen.kt"
img_content = '''package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.utils.FileUtils
import com.example.accessiblevideoeditor.ui.components.AccessibleTextField
import java.io.File

@Composable
fun ImageEditorScreen(onBack: () -> Unit) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var textToDraw by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ffmpegProcessor = remember { FFmpegProcessor(context) { p -> progress = p } }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ãÍÑÑ ÇáÕæÑ (ÇáßÊÇÈÉ Úáì ÇáÕæÑ)", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedImageUri != null) "Êã ÇÎÊíÇÑ ÇáÕæÑÉ" else "ÇÎÊÑ ÕæÑÉ")
        }

        AccessibleTextField(
            value = textToDraw,
            onValueChange = { textToDraw = it },
            hint = "ÇáäÕ ÇáãÑÇÏ ßÊÇÈÊå Úáì ÇáÕæÑÉ",
            modifier = Modifier.fillMaxWidth()
        )

        if (isProcessing) {
            CircularProgressIndicator()
            Text("ÌÇÑí ãÚÇáÌÉ ÇáÕæÑÉ... \%")
        } else {
            Button(
                onClick = {
                    selectedImageUri?.let { uri ->
                        isProcessing = true
                        coroutineScope.launch {
                            val inputPath = FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/edited_image_\.jpg"
                            if (inputPath != null) {
                                val success = ffmpegProcessor.drawTextOnImage(inputPath, textToDraw, outputPath)
                                if (success) {
                                    FileUtils.saveToGallery(context, File(outputPath), "image/jpeg")
                                    // Toast handled inside saveToGallery usually, or add here
                                }
                            }
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageUri != null && textToDraw.isNotBlank()
            ) {
                Text("ÊØÈíÞ ÇáßÊÇÈÉ æÍÝÙ ÇáÕæÑÉ")
            }
        }
    }
}
'''
write_file(img_path, img_content)

# 2. AudioStudioScreen.kt
audio_path = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens\AudioStudioScreen.kt"
audio_content = '''package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.utils.FileUtils
import java.io.File

@Composable
fun AudioStudioScreen(onBack: () -> Unit = {}) {
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val ffmpegProcessor = remember { FFmpegProcessor(context) { p -> progress = p } }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedMediaUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ÇÓÊæÏíæ ÇáÕæÊ", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { pickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedMediaUri != null) "Êã ÇÎÊíÇÑ ÇáãáÝ" else "ÇÎÊÑ ÝíÏíæ Ãæ ãáÝ ÕæÊí")
        }

        if (isProcessing) {
            CircularProgressIndicator()
            Text("ÌÇÑí ÇáãÚÇáÌÉ... \%")
        } else {
            Button(
                onClick = {
                    selectedMediaUri?.let { uri ->
                        isProcessing = true
                        coroutineScope.launch {
                            val inputPath = FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/extracted_audio_\.mp3"
                            if (inputPath != null) {
                                val success = ffmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                if (success) {
                                    FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                }
                            }
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMediaUri != null
            ) {
                Text("ÇÓÊÎÑÇÌ ÇáÕæÊ (MP3)")
            }
            
            Button(
                onClick = {
                    selectedMediaUri?.let { uri ->
                        isProcessing = true
                        coroutineScope.launch {
                            val inputPath = FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/bass_boosted_\.mp3"
                            if (inputPath != null) {
                                val success = ffmpegProcessor.applyAudioStudioEffects(inputPath, "bass_boost", outputPath)
                                if (success) {
                                    FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                                }
                            }
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMediaUri != null
            ) {
                Text("ÊÖÎíã ÇáÕæÊ (Bass Boost)")
            }
        }
    }
}
'''
write_file(audio_path, audio_content)

# 3. FFmpegProcessor.kt changes
ffmpeg_path = r"app\src\main\java\com\example\accessiblevideoeditor\media\FFmpegProcessor.kt"
content = read_file(ffmpeg_path)

# Add drawTextOnImage
if "drawTextOnImage" not in content:
    draw_text_code = '''
    suspend fun drawTextOnImage(sourceImage: String, text: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        val fontPath = com.example.accessiblevideoeditor.utils.FileUtils.copyFontToCache(context)
        val escapedText = text.replace("'", "\\\\'").replace(":", "\\\\:")
        val drawtext = "drawtext=fontfile='':text='':x=(w-text_w)/2:y=(h-text_h)/2:fontcolor=white:fontsize=48:shadowcolor=black:shadowx=2:shadowy=2"
        val commandArgs = arrayOf("-y", "-i", sourceImage, "-vf", drawtext, outputPath)
        executeWithProgress(commandArgs, sourceImage)
    }
'''
    # Insert before applyAudioStudioEffects
    content = content.replace("suspend fun applyAudioStudioEffects", draw_text_code + "\n    suspend fun applyAudioStudioEffects")

# Fix replaceAudio (remove -shortest)
content = content.replace(
    "-y -i \\\"\\\" -i \\\"\\\" -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -shortest",
    "-y -i \\\"\\\" -i \\\"\\\" -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac"
)
write_file(ffmpeg_path, content)

# 4. BatchProcessScreen.kt - fix extraction loop
batch_path = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens\BatchProcessScreen.kt"
batch_content = read_file(batch_path)
batch_content = batch_content.replace('''for (i in 1..100) {
                            delay(50)
                            progress = i
                        }''', '''
                        val ffmpegProcessor = com.example.accessiblevideoeditor.media.FFmpegProcessor(context) { p -> progress = p }
                        selectedUris.forEachIndexed { index, uri ->
                            val inputPath = com.example.accessiblevideoeditor.utils.FileUtils.getPathFromUri(context, uri)
                            val outputPath = context.cacheDir.absolutePath + "/batch_extracted_.mp3"
                            if (inputPath != null) {
                                ffmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                                com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, File(outputPath), "audio/mpeg")
                            }
                            progress = ((index + 1) * 100) / selectedUris.size
                        }''')
write_file(batch_path, batch_content)

print("Pass 1 done.")
