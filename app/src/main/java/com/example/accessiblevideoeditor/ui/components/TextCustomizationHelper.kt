package com.example.accessiblevideoeditor.ui.components

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.media.TextRenderer
import com.google.android.material.slider.Slider
import com.example.accessiblevideoeditor.databinding.LayoutTextCustomizationPanelBinding

class TextCustomizationHelper(
    private val context: Context,
    private val binding: LayoutTextCustomizationPanelBinding,
    private val onOptionsChanged: (TextRenderer.TextOptions) -> Unit
) {
    private var currentOptions = TextRenderer.TextOptions(
        text = "",
        textSizeSp = binding.slTextSize.value,
        shadowRadius = binding.slShadowRadius.value,
        shadowDx = binding.slShadowDx.value,
        shadowDy = binding.slShadowDy.value
    )

    private val colors = listOf(
        Pair(Color.WHITE, context.getString(R.string.string_color_white)),
        Pair(Color.BLACK, context.getString(R.string.string_color_black)),
        Pair(Color.RED, context.getString(R.string.string_color_red)),
        Pair(Color.GREEN, context.getString(R.string.string_color_green)),
        Pair(Color.BLUE, context.getString(R.string.string_color_blue)),
        Pair(Color.YELLOW, context.getString(R.string.string_color_yellow)),
        Pair(Color.MAGENTA, context.getString(R.string.string_color_magenta)),
        Pair(Color.CYAN, context.getString(R.string.string_color_cyan))
    )

    private val bgColors = colors.map {
        val color = it.first
        val alphaColor = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))
        Pair(alphaColor, it.second + context.getString(R.string.string_color_transparent_suffix))
    }.toMutableList().apply {
        add(0, Pair(Color.TRANSPARENT, context.getString(R.string.string_color_none)))
    }

    private val fonts = listOf(
        Pair(TextRenderer.TextFontFamily.DEFAULT, context.getString(R.string.string_font_default)),
        Pair(TextRenderer.TextFontFamily.SERIF, context.getString(R.string.string_font_serif)),
        Pair(TextRenderer.TextFontFamily.SANS_SERIF, context.getString(R.string.string_font_sans_serif)),
        Pair(TextRenderer.TextFontFamily.MONOSPACE, context.getString(R.string.string_font_monospace))
    )

    init {
        setupListeners()
        setupSpinners()
    }

    private fun notifyChange() {
        onOptionsChanged(currentOptions)
    }

    private fun setupListeners() {
        binding.etTextContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentOptions = currentOptions.copy(text = s.toString())
                notifyChange()
            }
        })

        binding.slTextSize.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            currentOptions = currentOptions.copy(textSizeSp = value)
            notifyChange()
        }

        binding.slShadowRadius.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            currentOptions = currentOptions.copy(shadowRadius = value)
            notifyChange()
        }

        binding.slShadowDx.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            currentOptions = currentOptions.copy(shadowDx = value)
            notifyChange()
        }

        binding.slShadowDy.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            currentOptions = currentOptions.copy(shadowDy = value)
            notifyChange()
        }

        binding.rgPosition.setOnCheckedChangeListener { _, checkedId ->
            val position = when (checkedId) {
                R.id.rbPosTop -> TextRenderer.TextPosition.TOP
                R.id.rbPosCenter -> TextRenderer.TextPosition.CENTER
                R.id.rbPosBottom -> TextRenderer.TextPosition.BOTTOM
                else -> TextRenderer.TextPosition.BOTTOM
            }
            currentOptions = currentOptions.copy(position = position)
            notifyChange()
        }

        binding.rgAlignment.setOnCheckedChangeListener { _, checkedId ->
            val alignment = when (checkedId) {
                R.id.rbAlignLeft -> TextRenderer.TextAlignment.LEFT
                R.id.rbAlignCenter -> TextRenderer.TextAlignment.CENTER
                R.id.rbAlignRight -> TextRenderer.TextAlignment.RIGHT
                else -> TextRenderer.TextAlignment.CENTER
            }
            currentOptions = currentOptions.copy(alignment = alignment)
            notifyChange()
        }
    }

    private fun setupSpinners() {
        // Text Color
        val colorAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, colors.map { it.second })
        binding.spTextColor.adapter = colorAdapter
        binding.spTextColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentOptions = currentOptions.copy(textColor = colors[position].first)
                notifyChange()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spTextColor.setSelection(0)

        // Background Color
        val bgAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, bgColors.map { it.second })
        binding.spBgColor.adapter = bgAdapter
        binding.spBgColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentOptions = currentOptions.copy(bgColor = bgColors[position].first)
                notifyChange()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spBgColor.setSelection(0)

        // Shadow Color
        val shadowColorAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, colors.map { it.second })
        binding.spShadowColor.adapter = shadowColorAdapter
        binding.spShadowColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentOptions = currentOptions.copy(shadowColor = colors[position].first)
                notifyChange()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spShadowColor.setSelection(1) // Default to Black

        // Font Family
        val fontAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, fonts.map { it.second })
        binding.spFontFamily.adapter = fontAdapter
        binding.spFontFamily.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentOptions = currentOptions.copy(fontFamily = fonts[position].first)
                notifyChange()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spFontFamily.setSelection(0)
    }
}
