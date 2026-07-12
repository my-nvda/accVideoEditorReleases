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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.accessiblevideoeditor.media.TextRenderer

@Composable
fun VideoEditorScreen(
    progress: Int = 0,
    isProcessing: Boolean = false,
    onApplyText: (TextRenderer.TextOptions, String, String, Uri?) -> Unit
) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var textOptions by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }
    var startTimeStr by remember { mutableStateOf("00:00") }
    var endTimeStr by remember { mutableStateOf("00:05") }
    
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { videoPickerLauncher.launch("video/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_131))
        }

        if (selectedVideoUri != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        TextCustomizationPanel(
            onOptionsChanged = { textOptions = it }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = startTimeStr,
                onValueChange = { startTimeStr = it },
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_35),
                modifier = Modifier.weight(1f)
            )

            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = endTimeStr,
                onValueChange = { endTimeStr = it },
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_34),
                modifier = Modifier.weight(1f)
            )
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_28, progress))
        } else {
            Button(
                onClick = {
                    onApplyText(textOptions, startTimeStr, endTimeStr, selectedVideoUri)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedVideoUri != null && textOptions.text.isNotBlank()
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_38))
            }
        }
    }
}
