import os

base_dir = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens"

files = {
    "ImageEditorScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ImageEditorScreen(onBack: () -> Unit) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("„Õ—— «·’Ê—", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedImageUri != null) " „ «Œ Ì«— «·’Ê—…" else "«Œ — ’Ê—…")
        }

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageUri != null
            ) {
                Text(" ÿ»Ìﬁ «· ⁄œÌ·« ")
            }
        }
    }
}""",

    "SmartCutScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmartCutScreen(onBack: () -> Unit) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var silenceThreshold by remember { mutableStateOf("-30dB") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedVideoUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("«·ﬁ’ «·–ﬂÌ (≈“«·… «·’„ )", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedVideoUri != null) " „ «Œ Ì«— «·›ÌœÌÊ" else "«Œ — ›ÌœÌÊ")
        }

        OutlinedTextField(
            value = silenceThreshold,
            onValueChange = { silenceThreshold = it },
            label = { Text("„” ÊÏ «·’„  („À«·: -30dB)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
        )

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedVideoUri != null
            ) {
                Text(" ÿ»Ìﬁ «·ﬁ’ «·–ﬂÌ")
            }
        }
    }
}""",

    "MergeVideosScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MergeVideosScreen(onBack: () -> Unit) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("œ„Ã «·›ÌœÌÊÂ« ", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("«Œ — ›ÌœÌÊÂÌ‰ √Ê √ﬂÀ—")
        }
        
        if (selectedUris.isNotEmpty()) {
            Text(" „ «Œ Ì«— \ ›ÌœÌÊÂ« ")
        }

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.size > 1
            ) {
                Text("œ„Ã «·¬‰")
            }
        }
    }
}""",

    "ReverseMediaScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReverseMediaScreen(onBack: () -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var reverseVideo by remember { mutableStateOf(true) }
    var reverseAudio by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("⁄ﬂ” «·›ÌœÌÊ / «·’Ê ", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) " „ «Œ Ì«— «·›ÌœÌÊ" else "«Œ — ›ÌœÌÊ")
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = reverseVideo, onCheckedChange = { reverseVideo = it })
            Text("⁄ﬂ” «·›ÌœÌÊ")
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = reverseAudio, onCheckedChange = { reverseAudio = it })
            Text("⁄ﬂ” «·’Ê ")
        }

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null && (reverseVideo || reverseAudio)
            ) {
                Text("»œ¡ «·⁄ﬂ”")
            }
        }
    }
}""",

    "SlideshowMakerScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SlideshowMakerScreen(onBack: () -> Unit) {
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var durationPerImage by remember { mutableStateOf("3") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> selectedUris = uris }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("’«‰⁄ ‘—«∆Õ «·⁄—÷", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("«Œ — «·’Ê—")
        }
        
        if (selectedUris.isNotEmpty()) {
            Text(" „ «Œ Ì«— \ ’Ê—")
        }
        
        OutlinedTextField(
            value = durationPerImage,
            onValueChange = { durationPerImage = it },
            label = { Text("„œ… ⁄—÷ «·’Ê—… (»«·ÀÊ«‰Ì)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        )

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUris.size > 1
            ) {
                Text("≈‰‘«¡ «·›ÌœÌÊ")
            }
        }
    }
}""",

    "FastConverterScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastConverterScreen(onBack: () -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFormat by remember { mutableStateOf("MP4") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val formats = listOf("MP4", "MKV", "AVI", "GIF")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("«·„ÕÊ· «·”—Ì⁄", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) " „ «Œ Ì«— «·„·›" else "«Œ — „·›")
        }
        
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedFormat,
                onValueChange = {},
                readOnly = true,
                label = { Text("’Ì€… «· ÕÊÌ·") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                formats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = { selectedFormat = format; expanded = false }
                    )
                }
            }
        }

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null
            ) {
                Text(" ÕÊÌ·")
            }
        }
    }
}""",

    "TickerTextScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TickerTextScreen(onBack: () -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var text by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("«·‘—Ìÿ «·≈Œ»«—Ì «·„ Õ—ﬂ", style = MaterialTheme.typography.titleLarge)
        
        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedUri != null) " „ «Œ Ì«— «·›ÌœÌÊ" else "«Œ — ›ÌœÌÊ")
        }
        
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("«·‰’ «·„ Õ—ﬂ") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        if (isProcessing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    isProcessing = true
                    coroutineScope.launch { delay(2000); isProcessing = false }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUri != null && text.isNotBlank()
            ) {
                Text("≈÷«›… «·‘—Ìÿ «·„ Õ—ﬂ")
            }
        }
    }
}""",

    "HistoryScreen.kt": """package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("«·”Ã· («·„·›«  «·„’œ—…)") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("·«  ÊÃœ „·›«  „’œ—… ÕœÌÀ«.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}"""
}

for filename, code in files.items():
    filepath = os.path.join(base_dir, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(code)
    print(f"Written {filename}")

