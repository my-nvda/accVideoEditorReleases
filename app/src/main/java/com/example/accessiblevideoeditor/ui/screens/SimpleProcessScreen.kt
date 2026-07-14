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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SimpleProcessScreen(
    titleRes: Int = 0,
    isProcessing: Boolean = false,
    onBack: () -> Unit = {},
    onProcess: (Uri, String) -> Unit = { _, _ -> },
    initialUris: List<android.net.Uri> = emptyList()
) {
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(initialUris.firstOrNull()) }
    var isPreparing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedUri = uri }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val titleStr = if (titleRes != 0) stringResource(id = titleRes) else "Processing"
        Text(titleStr, style = MaterialTheme.typography.titleLarge)

        Button(
            onClick = { pickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_88) else com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_140))
        }

        if (isProcessing || isPreparing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(
                text = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_142),
                modifier = Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }
            )
        } else {
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        isPreparing = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val tempFile = MediaUtils.copyUriToTempFile(context, uri, "temp_simple_${System.currentTimeMillis()}.mp4")
                            withContext(Dispatchers.Main) {
                                isPreparing = false
                                if (tempFile != null) {
                                    onProcess(uri, tempFile.absolutePath)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null
            ) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_117))
            }
        }
    }
}

