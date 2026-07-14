package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.accessiblevideoeditor.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit = {}) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_159)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" }
                    ) {
                        Text("<-")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_160)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_161)) }
                )
            }
            
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (selectedTabIndex == 0) {
                    HelpGuideContent()
                } else {
                    AboutContent()
                }
            }
        }
    }
}

@Composable
fun HelpGuideContent() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_176), style = MaterialTheme.typography.titleMedium)
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_177))
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_178))
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_179))
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_180))
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_181))
    }
}

@Composable
fun AboutContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_162), style = MaterialTheme.typography.bodyLarge)
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_163), style = MaterialTheme.typography.bodyMedium)
        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(R.string.string_164), style = MaterialTheme.typography.bodyMedium)
    }
}

