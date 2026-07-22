package com.example.accessiblevideoeditor.plugins

data class PluginModel(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val sizeMb: Double,
    val downloadUrl: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0
)
