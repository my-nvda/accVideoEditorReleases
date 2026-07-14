@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
package com.example.accessiblevideoeditor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.accessiblevideoeditor.ui.screens.HomeScreen
import com.example.accessiblevideoeditor.ui.screens.FastConverterScreen
import com.example.accessiblevideoeditor.ui.screens.WatermarkScreen
import com.example.accessiblevideoeditor.ui.screens.TickerTextScreen
import com.example.accessiblevideoeditor.ui.screens.AudioStudioScreen
import com.example.accessiblevideoeditor.ui.screens.SmartCutScreen
import com.example.accessiblevideoeditor.ui.screens.AiAnalysisScreen
import com.example.accessiblevideoeditor.ui.screens.VideoEditorScreen
import com.example.accessiblevideoeditor.ui.screens.AudioEditorScreen

import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.example.accessiblevideoeditor.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import android.content.Context

// Helper to parse HH:MM:SS, MM:SS, or SS to seconds
fun parseTimeToSeconds(timeStr: String): Int {
    val parts = timeStr.split(":")
    return when (parts.size) {
        1 -> parts[0].trim().toIntOrNull() ?: 0
        2 -> (parts[0].trim().toIntOrNull() ?: 0) * 60 + (parts[1].trim().toIntOrNull() ?: 0)
        3 -> (parts[0].trim().toIntOrNull() ?: 0) * 3600 + (parts[1].trim().toIntOrNull() ?: 0) * 60 + (parts[2].trim().toIntOrNull() ?: 0)
        else -> 0
    }
}

@Composable
fun MainNavigation(sharedUris: List<android.net.Uri> = emptyList()) {
    val navController = rememberNavController()
    var showShareDialog by remember { mutableStateOf(sharedUris.isNotEmpty()) }
    var selectedSharedUris by remember { mutableStateOf(sharedUris) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var updateInfoToInstall by remember { mutableStateOf<com.example.accessiblevideoeditor.updater.AppUpdater.UpdateInfo?>(null) }
    var activeDownloadId by remember { mutableStateOf<Long?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val info = com.example.accessiblevideoeditor.updater.AppUpdater.checkForUpdate(context)
        if (info != null) {
            updateInfoToInstall = info
            com.example.accessiblevideoeditor.updater.AppUpdater.showUpdateNotification(context, info)
        }
    }
    
    updateInfoToInstall?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfoToInstall = null },
            title = { Text("تحديث جديد") },
            text = {
                val scrollState = androidx.compose.foundation.rememberScrollState()
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "تم العثور على تحديث جديد (الإصدار ${info.versionName}).\n\nملاحظات الإصدار:\n${info.releaseNotes}\n\nهل تريد التحديث الآن؟",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    activeDownloadId = com.example.accessiblevideoeditor.updater.AppUpdater.downloadAndInstall(context, info)
                    updateInfoToInstall = null
                }) {
                    Text("تنزيل وتثبيت")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfoToInstall = null }) {
                    Text("لاحقاً")
                }
            }
        )
    }

    activeDownloadId?.let { downloadId ->
        val progress by com.example.accessiblevideoeditor.updater.AppUpdater.observeDownload(context, downloadId).collectAsState(initial = null)
        
        AlertDialog(
            onDismissRequest = { },
            title = { Text("جاري تحميل التحديث") },
            text = {
                Column {
                    if (progress != null) {
                        val downloadedMB = progress!!.bytesDownloaded / (1024f * 1024f)
                        val totalMB = progress!!.totalBytes / (1024f * 1024f)
                        val percent = if (progress!!.totalBytes > 0) ((progress!!.bytesDownloaded * 100) / progress!!.totalBytes).toInt() else 0
                        val remainingMB = totalMB - downloadedMB

                        Text("اكتمل: $percent%")
                        LinearProgressIndicator(
                            progress = { if (progress!!.totalBytes > 0) progress!!.bytesDownloaded.toFloat() / progress!!.totalBytes.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                                    if (progress!!.totalBytes > 0) progress!!.bytesDownloaded.toFloat() / progress!!.totalBytes.toFloat() else 0f, 0f..1f
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("تم تحميل: %.2f MB من %.2f MB".format(downloadedMB, totalMB))
                        Text("المتبقي: %.2f MB".format(remainingMB))

                        if (progress!!.status == android.app.DownloadManager.STATUS_SUCCESSFUL || progress!!.status == android.app.DownloadManager.STATUS_FAILED) {
                            activeDownloadId = null
                        }
                    } else {
                        Text("جاري التهيئة...")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.remove(downloadId)
                    activeDownloadId = null
                }) {
                    Text("إلغاء التحميل")
                }
            }
        )
    }

    if (showShareDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_178)) }, // "What do you want to do with these files?"
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        Button(onClick = { showShareDialog = false; navController.navigate("fast_converter") }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_13))
                        }
                    }
                    item {
                        Button(onClick = { showShareDialog = false; navController.navigate("batch_process") }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_11))
                        }
                    }
                    item {
                        Button(onClick = { showShareDialog = false; navController.navigate("slideshow_maker") }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_9))
                        }
                    }
                    item {
                        Button(onClick = { showShareDialog = false; navController.navigate("video_editor") }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_15))
                        }
                    }
                    item {
                        Button(onClick = { showShareDialog = false; navController.navigate("audio_studio") }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_17))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) {
                    Text(AppStrings.get(androidx.compose.ui.platform.LocalContext.current, com.example.accessiblevideoeditor.R.string.string_36))
                }
            }
        )
    }


    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToVideoEditor = { navController.navigate("video_editor") },
                onNavigateToImageEditor = { navController.navigate("image_editor") },
                onNavigateToVideoTrimmer = { navController.navigate("video_trimmer") },
                onNavigateToAudioEditor = { navController.navigate("audio_editor") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToOcr = { navController.navigate("ocr") },
                onNavigateToStt = { navController.navigate("stt") },
                onNavigateToBoostVolume = { navController.navigate("boost_volume") },
                onNavigateToExtractAudio = { navController.navigate("extract_audio") },
                onNavigateToCompressVideo = { navController.navigate("compress_video") },
                onNavigateToMergeVideos = { navController.navigate("merge_videos") },
                onNavigateToReverseMedia = { navController.navigate("reverse_media") },
                onNavigateToBatchProcess = { navController.navigate("batch_process") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSlideshowMaker = { navController.navigate("slideshow_maker") },
                onNavigateToFastConverter = { navController.navigate("fast_converter") },
                onNavigateToWatermark = { navController.navigate("watermark") },
                onNavigateToTickerText = { navController.navigate("ticker_text") },
                onNavigateToAudioStudio = { navController.navigate("audio_studio") },
                onNavigateToSmartCut = { navController.navigate("smart_cut") },
                onNavigateToAiAnalysis = { navController.navigate("ai_analysis") }
            )
        }
        
        composable("slideshow_maker") {
            com.example.accessiblevideoeditor.ui.screens.SlideshowMakerScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        
        composable("stt") {
            com.example.accessiblevideoeditor.ui.screens.SpeechToTextScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }

        composable("reverse_media") {
            com.example.accessiblevideoeditor.ui.screens.ReverseMediaScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }

        composable("batch_process") {
            com.example.accessiblevideoeditor.ui.screens.BatchProcessScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }

        composable("ocr") {
            com.example.accessiblevideoeditor.ui.screens.OcrScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("history") {
            com.example.accessiblevideoeditor.ui.screens.HistoryScreen(onBack = { navController.popBackStack() })
        }
        
        composable("boost_volume") {
            var isProcessing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            com.example.accessiblevideoeditor.ui.screens.SimpleProcessScreen(
                titleRes = R.string.app_name,
                isProcessing = isProcessing,
                onBack = { navController.popBackStack() },
                onProcess = { uri, tempPath ->
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val outputFile = java.io.File(context.cacheDir, "output_boosted_${System.currentTimeMillis()}.mp4")
                            val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.boostVolume(
                                sourceVideo = tempPath,
                                multiplier = 3.0f,
                                outputPath = outputFile.absolutePath
                            )
                            if (success) {
                                val savedUri = com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                    context, outputFile, "AccessibleEditor_Boosted_${System.currentTimeMillis()}.mp4"
                                )
                                if (savedUri != null) {
                                    com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                        context,
                                        com.example.accessiblevideoeditor.media.HistoryItem(
                                            uriString = savedUri.toString(),
                                            name = "Boosted Video",
                                            timestamp = System.currentTimeMillis(),
                                            type = "video"
                                        )
                                    )
                                }
                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                            } else {
                                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.app_name), Toast.LENGTH_LONG).show() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                        } finally {
                            isProcessing = false
                        }
                    }
                }
            )
        }
        
        composable("extract_audio") {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            var showFormatDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var pendingUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }
            var pendingPath by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            
            if (showFormatDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showFormatDialog = false },
                    title = { androidx.compose.material3.Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_114)) },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            val formats = listOf("m4a", "mp3", "wav", "aac")
                            formats.forEach { format ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showFormatDialog = false
                                        val uri = pendingUri
                                        val path = pendingPath
                                        if (uri != null && path != null) {
                                            com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                                                com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_41)
                                            )
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val outputFile = java.io.File(context.cacheDir, "output_extracted_${System.currentTimeMillis()}.$format")
                                                    val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.extractAudio(
                                                        sourceVideo = path,
                                                        outputPath = outputFile.absolutePath,
                                                        format = format
                                                    )
                                                    if (success) {
                                                        val mimeType = when (format) {
                                                            "mp3" -> "audio/mpeg"
                                                            "wav" -> "audio/wav"
                                                            "aac" -> "audio/aac"
                                                            else -> "audio/mp4"
                                                        }
                                                        val savedUri = com.example.accessiblevideoeditor.media.MediaUtils.saveAudioToGallery(
                                                            context, outputFile, "AccessibleEditor_Audio_${System.currentTimeMillis()}.$format", mimeType
                                                        )
                                                        if (savedUri != null) {
                                                            com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                                                context,
                                                                com.example.accessiblevideoeditor.media.HistoryItem(
                                                                    uriString = savedUri.toString(),
                                                                    name = "Extracted Audio ($format)",
                                                                    timestamp = System.currentTimeMillis(),
                                                                    type = "audio"
                                                                )
                                                            )
                                                        }
                                                        com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                                    } else {
                                                        withContext(Dispatchers.Main) { Toast.makeText(context, "حدث خطأ أثناء استخراج الصوت", Toast.LENGTH_LONG).show() }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                                                } finally {
                                                    com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                                                }
                                            }
                                        }
                                    },
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.material3.Text(format.uppercase())
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showFormatDialog = false }) {
                            androidx.compose.material3.Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_207))
                        }
                    }
                )
            }

            com.example.accessiblevideoeditor.ui.screens.SimpleProcessScreen(
                titleRes = R.string.string_41,
                isProcessing = false,
                onBack = { navController.popBackStack() },
                onProcess = { uri, tempPath ->
                    pendingUri = uri
                    pendingPath = tempPath
                    showFormatDialog = true
                }
            )
        }
        
        composable("compress_video") {
            var isProcessing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            com.example.accessiblevideoeditor.ui.screens.SimpleProcessScreen(
                titleRes = R.string.string_125,
                isProcessing = isProcessing,
                onBack = { navController.popBackStack() },
                onProcess = { uri, tempPath ->
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val outputFile = java.io.File(context.cacheDir, "output_compressed_${System.currentTimeMillis()}.mp4")
                            val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.compressVideo(
                                sourceVideo = tempPath,
                                outputPath = outputFile.absolutePath
                            )
                            if (success) {
                                val savedUri = com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                    context, outputFile, "AccessibleEditor_Compressed_${System.currentTimeMillis()}.mp4"
                                )
                                if (savedUri != null) {
                                    com.example.accessiblevideoeditor.media.HistoryManager.saveToHistory(
                                        context,
                                        com.example.accessiblevideoeditor.media.HistoryItem(
                                            uriString = savedUri.toString(),
                                            name = "Compressed Video",
                                            timestamp = System.currentTimeMillis(),
                                            type = "video"
                                        )
                                    )
                                }
                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                            } else {
                                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.app_name), Toast.LENGTH_LONG).show() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                        } finally {
                            isProcessing = false
                        }
                    }
                }
            )
        }
        
        composable("merge_videos") {
            com.example.accessiblevideoeditor.ui.screens.MergeVideosScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("image_editor") {
            com.example.accessiblevideoeditor.ui.screens.ImageEditorScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("settings") {
            com.example.accessiblevideoeditor.ui.screens.SettingsScreen(
                onNavigateToHelp = { navController.navigate("help") },
                onNavigateToTranslation = { navController.navigate("translation") },
                onCheckUpdates = {
                    Toast.makeText(context, "جاري التحقق من التحديثات...", Toast.LENGTH_SHORT).show()
                    coroutineScope.launch {
                        val info = com.example.accessiblevideoeditor.updater.AppUpdater.checkForUpdate(context)
                        withContext(Dispatchers.Main) {
                            if (info != null) {
                                updateInfoToInstall = info
                                com.example.accessiblevideoeditor.updater.AppUpdater.showUpdateNotification(context, info)
                            } else {
                                Toast.makeText(context, "أنت تستخدم أحدث إصدار بالفعل!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
        composable("translation") {
            com.example.accessiblevideoeditor.ui.screens.VolunteerTranslationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("fast_converter") {
            com.example.accessiblevideoeditor.ui.screens.FastConverterScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("watermark") {
            com.example.accessiblevideoeditor.ui.screens.WatermarkScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("ticker_text") {
            TickerTextScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("audio_studio") {
            AudioStudioScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("smart_cut") {
            SmartCutScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
                composable("ai_analysis") {
            AiAnalysisScreen(onBack = { navController.popBackStack() }, initialUris = selectedSharedUris)
        }
        composable("video_trimmer") {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current

            com.example.accessiblevideoeditor.ui.screens.VideoTrimmerScreen(
                onApplyTrim = { startStr, durationStr, uri ->
                    if (uri == null) return@VideoTrimmerScreen
                    
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    val trimMsg = context.getString(R.string.string_46).replace(" %1\$s%%", "")
                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(trimMsg)
                    
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // 1. Copy video to temp file
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(
                                context, uri, "temp_trim_in_${System.currentTimeMillis()}.mp4"
                            )
                            
                            if (tempVideo != null) {
                                // 2. Process with FFmpeg
                                val outputFile = java.io.File(context.cacheDir, "output_trim_${System.currentTimeMillis()}.mp4")
                                
                                val startSecs = parseTimeToSeconds(startStr).toString()
                                val durationSecs = parseTimeToSeconds(durationStr).toString()
                                
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.trimVideo(
                                    sourceVideo = tempVideo.absolutePath,
                                    startTimeInSeconds = startSecs,
                                    durationInSeconds = durationSecs,
                                    outputPath = outputFile.absolutePath
                                )
                                
                                // 3. Save to Gallery
                                if (success) {
                                    com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                        context,
                                        outputFile,
                                        "AccessibleEditor_Trim_${System.currentTimeMillis()}.mp4"
                                    )
                                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        }
                    }
                }
            )
        }
        composable("help") {
            com.example.accessiblevideoeditor.ui.screens.HelpScreen(onBack = { navController.popBackStack() })
        }
        composable("audio_editor") {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current

            AudioEditorScreen(
                onRemoveAudio = { videoUri ->
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_25)
                    )
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(
                                context, videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4"
                            )
                            if (tempVideo != null) {
                                val outputFile = java.io.File(context.cacheDir, "output_no_audio_${System.currentTimeMillis()}.mp4")
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.removeAudio(
                                    sourceVideo = tempVideo.absolutePath,
                                    outputPath = outputFile.absolutePath
                                )
                                if (success) {
                                    com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                        context, outputFile, "AccessibleEditor_NoAudio_${System.currentTimeMillis()}.mp4"
                                    )
                                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.app_name)} ${e.message}", Toast.LENGTH_LONG).show() }
                        } finally {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        }
                    }
                },
                onReplaceAudio = { videoUri, audioUri ->
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_7)
                    )
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4")
                            val tempAudio = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, audioUri, "temp_audio_only_${System.currentTimeMillis()}.mp3")
                            
                            if (tempVideo != null && tempAudio != null) {
                                val outputFile = java.io.File(context.cacheDir, "output_merged_audio_${System.currentTimeMillis()}.mp4")
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.replaceAudio(
                                    sourceVideo = tempVideo.absolutePath,
                                    newAudio = tempAudio.absolutePath,
                                    outputPath = outputFile.absolutePath
                                )
                                if (success) {
                                    com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                        context, outputFile, "AccessibleEditor_MergedAudio_${System.currentTimeMillis()}.mp4"
                                    )
                                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.app_name)} ${e.message}", Toast.LENGTH_LONG).show() }
                        } finally {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        }
                    }
                },
                onMixAudio = { videoUri, audioUri ->
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                        com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_9)
                    )
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4")
                            val tempAudio = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, audioUri, "temp_audio_only_${System.currentTimeMillis()}.mp3")
                            
                            if (tempVideo != null && tempAudio != null) {
                                val outputFile = java.io.File(context.cacheDir, "output_mixed_audio_${System.currentTimeMillis()}.mp4")
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.mixAudio(
                                    sourceVideo = tempVideo.absolutePath,
                                    newAudio = tempAudio.absolutePath,
                                    outputPath = outputFile.absolutePath
                                )
                                if (success) {
                                    com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                        context, outputFile, "AccessibleEditor_MixedAudio_${System.currentTimeMillis()}.mp4"
                                    )
                                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "${context.getString(R.string.app_name)} ${e.message}", Toast.LENGTH_LONG).show() }
                        } finally {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        }
                    }
                }
            )
        }
        composable("video_editor") {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current

            VideoEditorScreen(
                onApplyText = { textOptions, start, end, uri ->
                    if (uri == null) return@VideoEditorScreen
                    
                    com.example.accessiblevideoeditor.media.SoundManager.playProcessing()
                    val processMsg = context.getString(R.string.string_28).replace(" %1\$s%%", "")
                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(processMsg)
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            // 1. Copy video to temp file
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(
                                context, uri, "temp_video_${System.currentTimeMillis()}.mp4"
                            )
                            
                            if (tempVideo != null) {
                                // 2. Get Video Dimensions
                                val (width, height) = com.example.accessiblevideoeditor.media.FFmpegProcessor.getVideoDimensions(tempVideo.absolutePath)
                                
                                // 3. Create Text Overlay PNG
                                val overlayFile = java.io.File(context.cacheDir, "overlay_${System.currentTimeMillis()}.png")
                                com.example.accessiblevideoeditor.media.TextRenderer.createOverlayPng(width, height, textOptions, overlayFile)
                                
                                // 4. Parse Times robustly
                                val startSecs = parseTimeToSeconds(start)
                                val endSecs = parseTimeToSeconds(end)
                                
                                // 5. Process with FFmpeg
                                val outputFile = java.io.File(context.cacheDir, "output_video_${System.currentTimeMillis()}.mp4")
                                
                                val resultLog = com.example.accessiblevideoeditor.media.FFmpegProcessor.addTextOverlay(
                                    sourceVideo = tempVideo.absolutePath,
                                    overlayPngPath = overlayFile.absolutePath,
                                    startTimeInSeconds = startSecs,
                                    endTimeInSeconds = endSecs,
                                    outputPath = outputFile.absolutePath
                                ) { currentProgress ->
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.updateProgress(currentProgress / 100f)
                                }
                                
                                // 6. Save to Gallery
                                if (resultLog == "SUCCESS") {
                                    com.example.accessiblevideoeditor.media.MediaUtils.saveVideoToGallery(
                                        context,
                                        outputFile,
                                        "AccessibleEditor_Video_${System.currentTimeMillis()}.mp4"
                                    )
                                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                } else {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.showError(resultLog)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.string_183), Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "${context.getString(R.string.app_name)} ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                        }
                    }
                }
            )
        }
    }
}




