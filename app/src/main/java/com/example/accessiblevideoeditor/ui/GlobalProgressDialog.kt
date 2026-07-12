package com.example.accessiblevideoeditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.res.stringResource

@Composable
fun GlobalProgressDialog() {
    if (ProcessingManager.isProcessing) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = { /* No dismiss by clicking outside */ },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(ProcessingManager.statusMessage)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (ProcessingManager.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { ProcessingManager.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(ProcessingManager.progress * 100).toInt()}%")
                    } else {
                        val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111)
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
                    }
                    
                    if (ProcessingManager.etaMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ProcessingManager.etaMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            confirmButton = {
                // If finished (100%), failed, or success, show OK button
                val isDone = ProcessingManager.progress >= 1f || 
                             ProcessingManager.statusMessage == com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name) || 
                             ProcessingManager.statusMessage == com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name)
                
                if (isDone) {
                    TextButton(onClick = { ProcessingManager.stopProcessing() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                val isDone = ProcessingManager.progress >= 1f || 
                             ProcessingManager.statusMessage == com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name) || 
                             ProcessingManager.statusMessage == com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name)

                if (ProcessingManager.isCancellable && !isDone) {
                    TextButton(onClick = { ProcessingManager.cancelCurrentProcess() }) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.app_name), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}
