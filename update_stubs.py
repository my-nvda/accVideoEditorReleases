import os

screens = [
    "AudioEditorScreen", "AudioStudioScreen", "BatchProcessScreen",
    "FastConverterScreen", "HelpScreen", "HistoryScreen",
    "ImageEditorScreen", "MergeVideosScreen", "OcrScreen", "ReverseMediaScreen",
    "SimpleProcessScreen", "SlideshowMakerScreen", "SmartCutScreen",
    "SpeechToTextScreen", "TextCustomizationPanel", "TickerTextScreen", "VideoEditorScreen",
    "VideoTrimmerScreen", "WatermarkScreen"
]

base_dir = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens"

for screen in screens:
    content = f"""package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box

@Composable
fun {screen}(onBack: () -> Unit = {{}}) {{
    Box {{
        Text("Recovering {screen}...")
    }}
}}
"""
    with open(os.path.join(base_dir, f"{screen}.kt"), "w", encoding="utf-8") as f:
        f.write(content)
print("Updated all stub screens to use onBack.")
