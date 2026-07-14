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
import com.example.accessiblevideoeditor.media.HistoryItem
import com.example.accessiblevideoeditor.media.HistoryManager
import com.example.accessiblevideoeditor.media.SoundManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    
    var itemToRename by remember { mutableStateOf<HistoryItem?>(null) }
    var newFileName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<HistoryItem?>(null) }

    fun loadHistory() {
        historyItems = HistoryManager.loadHistory(context)
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_174)) },
            text = {
                com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_174),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    itemToRename?.let { item ->
                        if (newFileName.isNotBlank()) {
                            val list = historyItems.map { 
                                if (it == item) it.copy(name = newFileName) else it
                            }
                            HistoryManager.saveFullHistory(context, list)
                            loadHistory()
                            SoundManager.playSuccess()
                            android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_182), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    itemToRename = null
                }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_174))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_207))
                }
            }
        )
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_204)) },
            text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_205)) },
            confirmButton = {
                TextButton(onClick = {
                    itemToDelete?.let { item ->
                        val list = historyItems.toMutableList()
                        list.remove(item)
                        HistoryManager.saveFullHistory(context, list)
                        loadHistory()
                        try {
                            val uri = Uri.parse(item.uriString)
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        SoundManager.playSuccess()
                        android.widget.Toast.makeText(context, com.example.accessiblevideoeditor.ui.AppStrings.get(context, R.string.string_182), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    itemToDelete = null
                }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_206))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_207))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_116)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (historyItems.isEmpty()) {
                item {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_29), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                items(historyItems, key = { it.uriString }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(item.uriString), if (item.type == "video") "video/*" else if (item.type == "audio") "audio/*" else "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", item.timestamp).toString()
                                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.semantics { contentDescription = context.getString(com.example.accessiblevideoeditor.R.string.string_226) }
                                ) {
                                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                                }
                                
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_172)) },
                                        onClick = {
                                            menuExpanded = false
                                            try {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = if (item.type == "video") "video/*" else if (item.type == "audio") "audio/*" else "image/*"
                                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
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
                                            itemToRename = item
                                            newFileName = item.name
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_175)) },
                                        onClick = {
                                            menuExpanded = false
                                            itemToDelete = item
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
