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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoTrimmerScreen(
    progress: Int = 0,
    isProcessing: Boolean = false,
    onApplyTrim: (String, String, Uri?) -> Unit
) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var startTimeStr by remember { mutableStateOf("00:00") }
    var durationStr by remember { mutableStateOf("00:05") }

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
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_90))
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = startTimeStr,
                onValueChange = { startTimeStr = it },
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_23),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
            )

            com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                value = durationStr,
                onValueChange = { durationStr = it },
                hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_49),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
            )
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_46, progress))
        } else {
            Button(
                onClick = {
                    onApplyTrim(startTimeStr, durationStr, selectedVideoUri)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedVideoUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_76))
            }
        }
    }
}
