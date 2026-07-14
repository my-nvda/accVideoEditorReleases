package com.example.accessiblevideoeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.accessiblevideoeditor.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.accessiblevideoeditor.media.TextRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCustomizationPanel(
    onOptionsChanged: (TextRenderer.TextOptions) -> Unit
) {
    var options by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }

    val colors = listOf(
        Color.White to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_156), Color.Black to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_150), Color.Red to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_153), Color.Green to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_152), Color.Blue to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_151), Color.Yellow to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_154), Color.Magenta to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_143), Color.Cyan to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_149)
    )

    fun notifyChange() {
        onOptionsChanged(options)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = options.text,
            onValueChange = { options = options.copy(text = it); notifyChange() },
            hint = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_104),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_135))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(colors) { (color, name) ->
                val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_84, name)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (options.textColor == color.toArgb()) 2.dp else 0.dp, Color.Gray, CircleShape)
                        .semantics { contentDescription = desc }
                        .clickable { options = options.copy(textColor = color.toArgb()); notifyChange() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_115))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(colors) { (color, name) ->
                val bgColorAlpha = color.copy(alpha = 0.5f)
                val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_69, name)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(bgColorAlpha)
                        .border(if (options.bgColor == bgColorAlpha.toArgb()) 2.dp else 0.dp, Color.Gray, CircleShape)
                        .semantics { contentDescription = desc }
                        .clickable { options = options.copy(bgColor = bgColorAlpha.toArgb()); notifyChange() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_184))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(colors) { (color, name) ->
                val desc = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_184) + ": " + name
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (options.shadowColor == color.toArgb()) 2.dp else 0.dp, Color.Gray, CircleShape)
                        .semantics { contentDescription = desc }
                        .clickable { options = options.copy(shadowColor = color.toArgb()); notifyChange() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_13, options.textSizeSp.toInt()))
        val descSize = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_64)
        Slider(
            value = options.textSizeSp,
            onValueChange = { options = options.copy(textSizeSp = it); notifyChange() },
            valueRange = 12f..120f,
            modifier = Modifier.semantics { 
                contentDescription = descSize
                stateDescription = "${options.textSizeSp.toInt()}" 
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val descShadowRadius = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_185)
        Text(descShadowRadius + ": ${options.shadowRadius.toInt()}")
        Slider(
            value = options.shadowRadius,
            onValueChange = { options = options.copy(shadowRadius = it); notifyChange() },
            valueRange = 0f..50f,
            modifier = Modifier.semantics { 
                contentDescription = descShadowRadius
                stateDescription = "${options.shadowRadius.toInt()}" 
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val descShadowOffsetX = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_186)
        Text(descShadowOffsetX + ": ${options.shadowDx.toInt()}")
        Slider(
            value = options.shadowDx,
            onValueChange = { options = options.copy(shadowDx = it); notifyChange() },
            valueRange = -50f..50f,
            modifier = Modifier.semantics { 
                contentDescription = descShadowOffsetX
                stateDescription = "${options.shadowDx.toInt()}" 
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val descShadowOffsetY = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_187)
        Text(descShadowOffsetY + ": ${options.shadowDy.toInt()}")
        Slider(
            value = options.shadowDy,
            onValueChange = { options = options.copy(shadowDy = it); notifyChange() },
            valueRange = -50f..50f,
            modifier = Modifier.semantics { 
                contentDescription = descShadowOffsetY
                stateDescription = "${options.shadowDy.toInt()}" 
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_129))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(TextRenderer.TextPosition.TOP to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_155), TextRenderer.TextPosition.CENTER to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_158), TextRenderer.TextPosition.BOTTOM to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_157)).forEach { (pos, labelStr) ->
                FilterChip(
                    selected = options.position == pos,
                    onClick = { options = options.copy(position = pos); notifyChange() },
                    label = { Text(labelStr) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_188))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val aligns = listOf(
                TextRenderer.TextAlignment.LEFT to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_190),
                TextRenderer.TextAlignment.CENTER to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_158),
                TextRenderer.TextAlignment.RIGHT to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_191)
            )
            aligns.forEach { (align, labelStr) ->
                FilterChip(
                    selected = options.alignment == align,
                    onClick = { options = options.copy(alignment = align); notifyChange() },
                    label = { Text(labelStr) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_189))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val fonts = listOf(
                TextRenderer.TextFontFamily.DEFAULT to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_192),
                TextRenderer.TextFontFamily.SERIF to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_193),
                TextRenderer.TextFontFamily.SANS_SERIF to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_194),
                TextRenderer.TextFontFamily.MONOSPACE to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_195)
            )
            fonts.forEach { (font, labelStr) ->
                FilterChip(
                    selected = options.fontFamily == font,
                    onClick = { options = options.copy(fontFamily = font); notifyChange() },
                    label = { Text(labelStr) }
                )
            }
        }
    }
}

