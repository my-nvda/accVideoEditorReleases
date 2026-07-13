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
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.unit.dp

@Composable
fun AudioEditorScreen(
    isProcessing: Boolean = false,
    onRemoveAudio: (Uri) -> Unit,
    onReplaceAudio: (Uri, Uri) -> Unit,
    onMixAudio: (Uri, Uri) -> Unit
) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedVideoUri = uri }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedAudioUri = uri }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_77), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedVideoUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_70) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_67))
                }
                
                Button(
                    onClick = { audioPickerLauncher.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedAudioUri != null) com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_85) else com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_19))
                }
            }
        }

        if (isProcessing) {
            val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
            Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_57))
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_110), style = MaterialTheme.typography.titleMedium)
                    
                    Button(
                        onClick = { selectedVideoUri?.let { onRemoveAudio(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVideoUri != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_25))
                    }

                    Button(
                        onClick = { 
                            if (selectedVideoUri != null && selectedAudioUri != null) {
                                onReplaceAudio(selectedVideoUri!!, selectedAudioUri!!)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVideoUri != null && selectedAudioUri != null
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_178)) // Replace Audio
                    }

                    Button(
                        onClick = { 
                            if (selectedVideoUri != null && selectedAudioUri != null) {
                                onMixAudio(selectedVideoUri!!, selectedAudioUri!!)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVideoUri != null && selectedAudioUri != null
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_179)) // Mix Audio
                    }
                }
            }
        }
    }
}

