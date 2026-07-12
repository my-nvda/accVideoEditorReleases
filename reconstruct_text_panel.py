import os

filepath = r"app\src\main\java\com\example\accessiblevideoeditor\ui\screens\TextCustomizationPanel.kt"
content = """package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.media.TextRenderer

@Composable
fun TextCustomizationPanel(
    onOptionsChanged: (TextRenderer.TextOptions) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var textColor by remember { mutableIntStateOf(Color.White.toArgb()) }
    var bgColor by remember { mutableIntStateOf(Color.Black.copy(alpha = 0.5f).toArgb()) }
    var textSizeSp by remember { mutableFloatStateOf(48f) }
    var position by remember { mutableStateOf(TextRenderer.TextPosition.BOTTOM) }

    val colors = listOf(
        Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan
    )

    fun notifyChange() {
        onOptionsChanged(
            TextRenderer.TextOptions(
                text = text,
                textColor = textColor,
                bgColor = bgColor,
                textSizeSp = textSizeSp,
                position = position
            )
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; notifyChange() },
            label = { Text("ÇáäÕ ÇáãÖÇÝ") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("áæä ÇáäÕ:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (textColor == color.toArgb()) 2.dp else 0.dp, Color.Gray, CircleShape)
                        .clickable { textColor = color.toArgb(); notifyChange() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("áæä ÇáÎáÝíÉ:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val bgColors = colors.map { it.copy(alpha = 0.5f) }
            bgColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (bgColor == color.toArgb()) 2.dp else 0.dp, Color.Gray, CircleShape)
                        .clickable { bgColor = color.toArgb(); notifyChange() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("ÍÌã ÇáÎØ: \")
        Slider(
            value = textSizeSp,
            onValueChange = { textSizeSp = it; notifyChange() },
            valueRange = 12f..120f
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("ÇáãæÖÚ:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(TextRenderer.TextPosition.TOP, TextRenderer.TextPosition.CENTER, TextRenderer.TextPosition.BOTTOM).forEach { pos ->
                FilterChip(
                    selected = position == pos,
                    onClick = { position = pos; notifyChange() },
                    label = { Text(pos.name) }
                )
            }
        }
    }
}
"""
with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
print("Reconstructed TextCustomizationPanel")
