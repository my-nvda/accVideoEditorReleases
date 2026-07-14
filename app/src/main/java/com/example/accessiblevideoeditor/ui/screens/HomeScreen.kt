package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToVideoEditor: () -> Unit,
    onNavigateToImageEditor: () -> Unit,
    onNavigateToVideoTrimmer: () -> Unit,
    onNavigateToAudioEditor: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToStt: () -> Unit,
    onNavigateToBoostVolume: () -> Unit,
    onNavigateToExtractAudio: () -> Unit,
    onNavigateToCompressVideo: () -> Unit,
    onNavigateToMergeVideos: () -> Unit,
    onNavigateToReverseMedia: () -> Unit,
    onNavigateToSlideshowMaker: () -> Unit,
    onNavigateToFastConverter: () -> Unit,
    onNavigateToWatermark: () -> Unit,
    onNavigateToTickerText: () -> Unit,
    onNavigateToAudioStudio: () -> Unit,
    onNavigateToSmartCut: () -> Unit,
    onNavigateToAiAnalysis: () -> Unit,
    onNavigateToBatchProcess: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessible Video Editor") },
                actions = {
                    val settingsDesc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_133)
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = settingsDesc }
                    ) {
                        Text(settingsDesc)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_112), onNavigateToVideoEditor) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_128), onNavigateToImageEditor) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_94), onNavigateToVideoTrimmer) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_45), onNavigateToSmartCut) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_102), onNavigateToAudioEditor) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_55), onNavigateToAudioStudio) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_31), onNavigateToAiAnalysis) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_63), onNavigateToStt) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_20), onNavigateToOcr) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_59), onNavigateToFastConverter) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_86), onNavigateToBoostVolume) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_41), onNavigateToExtractAudio) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_125), onNavigateToCompressVideo) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_75), onNavigateToMergeVideos) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_68), onNavigateToReverseMedia) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_80), onNavigateToSlideshowMaker) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_74), onNavigateToWatermark) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_52), onNavigateToTickerText) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_32), onNavigateToBatchProcess) }
            item { AccessibleMenuButton(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_116), onNavigateToHistory) }
        }
    }
}

@Composable
fun AccessibleMenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors()
    ) {
        Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}


