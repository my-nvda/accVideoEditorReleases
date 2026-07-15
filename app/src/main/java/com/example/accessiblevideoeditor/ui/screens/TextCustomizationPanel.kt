package com.example.accessiblevideoeditor.ui.screens

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
fun <T> DropdownSelector(
    label: String,
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    colorIndicator: ((T) -> Color)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selectedOption }?.second ?: ""
    
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = colorIndicator?.let { colorOf ->
                {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colorOf(selectedOption))
                            .border(1.dp, Color.Gray, CircleShape)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            enabled = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
                .semantics {
                    contentDescription = label
                    stateDescription = selectedText
                }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            options.forEach { (option, name) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (colorIndicator != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colorIndicator(option))
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(name)
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCustomizationPanel(
    onOptionsChanged: (TextRenderer.TextOptions) -> Unit
) {
    var options by remember { mutableStateOf(TextRenderer.TextOptions(text = "")) }

    val colors = listOf(
        Color.White to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_156),
        Color.Black to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_150),
        Color.Red to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_153),
        Color.Green to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_152),
        Color.Blue to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_151),
        Color.Yellow to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_154),
        Color.Magenta to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_143),
        Color.Cyan to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_149)
    )

    val colorItems = colors.map { it.first.toArgb() to it.second }
    val bgColorItems = colors.map { it.first.copy(alpha = 0.5f).toArgb() to it.second }

    fun notifyChange() {
        onOptionsChanged(options)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        com.example.accessiblevideoeditor.ui.components.AccessibleTextField(
            value = options.text,
            onValueChange = { options = options.copy(text = it); notifyChange() },
            hint = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_104),
            modifier = Modifier.fillMaxWidth()
        )

        // 1. Text Color Dropdown
        DropdownSelector(
            label = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_135),
            options = colorItems,
            selectedOption = options.textColor,
            onOptionSelected = { options = options.copy(textColor = it); notifyChange() },
            colorIndicator = { Color(it) },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Background Color Dropdown
        DropdownSelector(
            label = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_115),
            options = bgColorItems,
            selectedOption = options.bgColor,
            onOptionSelected = { options = options.copy(bgColor = it); notifyChange() },
            colorIndicator = { Color(it) },
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Shadow Color Dropdown
        DropdownSelector(
            label = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_184),
            options = colorItems,
            selectedOption = options.shadowColor,
            onOptionSelected = { options = options.copy(shadowColor = it); notifyChange() },
            colorIndicator = { Color(it) },
            modifier = Modifier.fillMaxWidth()
        )

        // Text Size
        Column {
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
        }

        // Shadow Radius
        Column {
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
        }

        // Shadow DX
        Column {
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
        }

        // Shadow DY
        Column {
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
        }

        // Font Family Dropdown
        val fontItems = listOf(
            TextRenderer.TextFontFamily.DEFAULT to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_192),
            TextRenderer.TextFontFamily.SERIF to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_193),
            TextRenderer.TextFontFamily.SANS_SERIF to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_194),
            TextRenderer.TextFontFamily.MONOSPACE to com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_195)
        )
        DropdownSelector(
            label = com.example.accessiblevideoeditor.ui.AppStrings.get(com.example.accessiblevideoeditor.ui.ProcessingManager.appContext!!, com.example.accessiblevideoeditor.R.string.string_189),
            options = fontItems,
            selectedOption = options.fontFamily,
            onOptionSelected = { options = options.copy(fontFamily = it); notifyChange() },
            modifier = Modifier.fillMaxWidth()
        )

        // Text Position
        Column {
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
        }

        // Text Alignment
        Column {
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
        }
    }
}
