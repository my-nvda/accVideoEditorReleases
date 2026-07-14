package com.example.accessiblevideoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.accessiblevideoeditor.R

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.ui.LanguageManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.example.accessiblevideoeditor.ui.components.AccessibleSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToHelp: () -> Unit,
    onNavigateToTranslation: () -> Unit = {},
    onCheckUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val isStartupSoundEnabled = SettingsManager.isStartupSoundEnabled
    val isProcessingSoundEnabled = SettingsManager.isProcessingSoundEnabled
    val isSuccessSoundEnabled = SettingsManager.isSuccessSoundEnabled
    val isErrorSoundEnabled = SettingsManager.isErrorSoundEnabled
    val isDarkMode = SettingsManager.isDarkMode

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_133)) })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_96), style = MaterialTheme.typography.titleMedium)
            }
            item {
                SettingsSwitchRow(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_82), isStartupSoundEnabled) { 
                    SettingsManager.isStartupSoundEnabled = it 
                }
            }
            item {
                SettingsSwitchRow(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_111), isProcessingSoundEnabled) { 
                    SettingsManager.isProcessingSoundEnabled = it 
                }
            }
            item {
                SettingsSwitchRow(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_130), isSuccessSoundEnabled) { 
                    SettingsManager.isSuccessSoundEnabled = it 
                }
            }
            item {
                SettingsSwitchRow(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_136), isErrorSoundEnabled) { 
                    SettingsManager.isErrorSoundEnabled = it 
                }
            }

            item { Divider() }

            item {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_196), style = MaterialTheme.typography.titleMedium)
            }
            item {
                com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
                    value = SettingsManager.geminiApiKey,
                    onValueChange = { SettingsManager.geminiApiKey = it },
                    hint = com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_197),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                var expandedModel by remember { mutableStateOf(false) }
                val currentModel = SettingsManager.geminiModel
                val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
                
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(
                        onClick = { expandedModel = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Text("${com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_198)}: $currentModel")
                    }
                    DropdownMenu(
                        expanded = expandedModel,
                        onDismissRequest = { expandedModel = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    SettingsManager.geminiModel = model
                                    expandedModel = false
                                }
                            )
                        }
                    }
                }
            }

            item { Divider() }

            item {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_100), style = MaterialTheme.typography.titleMedium)
            }
            item {
                SettingsSwitchRow(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_109), isDarkMode) { 
                    SettingsManager.isDarkMode = it 
                }
            }
            item {
                var expanded by remember { mutableStateOf(false) }
                val currentLanguage = LanguageManager.getCurrentLanguageCode()
                val currentLanguageName = LanguageManager.supportedLanguages.find { it.first == currentLanguage }?.second ?: "English"

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_17))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("($currentLanguageName)")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        LanguageManager.supportedLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    LanguageManager.setLanguage(code)
                                    expanded = false
                                    if (context is android.app.Activity) {
                                        context.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item { Divider() }

            item {
                Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_87), style = MaterialTheme.typography.titleMedium)
            }
            item {
                Button(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_48))
                }
            }
            item {
                Button(onClick = onNavigateToHelp, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_105))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onNavigateToTranslation, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("Volunteer Translation (المساهمة في الترجمة)")
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@accessiblevideoeditor.com")
                        }
                        context.startActivity(Intent.createChooser(intent, "Email"))
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_27))
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/1234567890"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_61))
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/YourAppHandler"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_148))
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/YourAppPage"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_144))
                }
            }
        }
    }
}


@Composable
fun SettingsSwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AccessibleSwitchRow(
        text = text,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

