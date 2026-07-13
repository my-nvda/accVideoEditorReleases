package com.example.accessiblevideoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    fun loadFiles() {
        val cacheDir = context.cacheDir
        files = cacheDir.listFiles()?.filter { 
            it.name.endsWith(".mp4") || it.name.endsWith(".mp3") || it.name.endsWith(".jpg")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        loadFiles()
    }

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_174)) },
            text = {
                com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_20),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    fileToRename?.let { oldFile ->
                        if (newFileName.isNotBlank()) {
                            val ext = oldFile.extension
                            val newFile = File(oldFile.parent, "$newFileName.$ext")
                            if (oldFile.renameTo(newFile)) {
                                loadFiles()
                                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_182), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                com.example.accessiblevideoeditor.media.SoundManager.playError()
                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_183), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    fileToRename = null
                }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_40))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_24))
                }
            }
        )
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_175)) },
            text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_44)) },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { file ->
                        if (file.delete()) {
                            loadFiles()
                            com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_182), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            com.example.accessiblevideoeditor.media.SoundManager.playError()
                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_183), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    fileToDelete = null
                }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_40))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_24))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_43)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_29), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                items(files, key = { it.absolutePath }) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                val mimeType = when {
                                    file.name.endsWith(".mp4") -> "video/mp4"
                                    file.name.endsWith(".mp3") -> "audio/mpeg"
                                    file.name.endsWith(".jpg") -> "image/jpeg"
                                    else -> "*/*"
                                }
                                setDataAndType(uri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_183), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.semantics { contentDescription = "ط®ظٹط§ط±ط§طھ ط¥ط¶ط§ظپظٹط©" }
                                ) {
                                    Text("â‹®", style = MaterialTheme.typography.titleLarge)
                                }
                                
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_172)) },
                                        onClick = {
                                            menuExpanded = false
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                val mimeType = when {
                                                    file.name.endsWith(".mp4") -> "video/mp4"
                                                    file.name.endsWith(".mp3") -> "audio/mpeg"
                                                    file.name.endsWith(".jpg") -> "image/jpeg"
                                                    else -> "*/*"
                                                }
                                                type = mimeType
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(Intent.createChooser(intent, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_172)))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_174)) },
                                        onClick = {
                                            menuExpanded = false
                                            fileToRename = file
                                            newFileName = file.nameWithoutExtension
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_175)) },
                                        onClick = {
                                            menuExpanded = false
                                            fileToDelete = file
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

