package com.example.accessiblevideoeditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.res.stringResource

@Composable
fun GlobalProgressDialog() {
    if (ProcessingManager.isProcessing) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = { /* No dismiss by clicking outside */ },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.app_name)) },
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
                            modifier = Modifier.fillMaxWidth().semantics {
                                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(ProcessingManager.progress, 0f..1f)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(ProcessingManager.progress * 100).toInt()}%",
                            modifier = Modifier.semantics {
                                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                            }
                        )
                    } else {
                        val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_111)
                        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = desc })
                    }
                    
                    if (ProcessingManager.etaMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ProcessingManager.etaMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            confirmButton = {
                val isDone = ProcessingManager.progress >= 1f
                
                if (isDone) {
                    TextButton(onClick = { ProcessingManager.stopProcessing() }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                val isDone = ProcessingManager.progress >= 1f

                if (ProcessingManager.isCancellable && !isDone) {
                    TextButton(onClick = { ProcessingManager.cancelCurrentProcess() }) {
                        Text(stringResource(android.R.string.cancel), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    // Global Error Dialog
    ProcessingManager.errorLog?.let { log ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = { ProcessingManager.dismissError() },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_224)) },
            text = {
                // Use a scrollable column for long logs
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scrollState)
                ) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_225), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ProcessingManager.dismissError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}
