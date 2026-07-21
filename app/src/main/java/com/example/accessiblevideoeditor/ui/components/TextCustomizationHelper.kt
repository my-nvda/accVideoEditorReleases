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
    private var currentOptions = TextRenderer.TextOptions(text = "")

    private val colors = listOf(
        Pair(Color.WHITE, "White"),
        Pair(Color.BLACK, "Black"),
        Pair(Color.RED, "Red"),
        Pair(Color.GREEN, "Green"),
        Pair(Color.BLUE, "Blue"),
        Pair(Color.YELLOW, "Yellow"),
        Pair(Color.MAGENTA, "Magenta"),
        Pair(Color.CYAN, "Cyan")
    )

    private val bgColors = colors.map {
        val color = it.first
        val alphaColor = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))
        Pair(alphaColor, it.second + " (Transparent)")
    }.toMutableList().apply {
        add(0, Pair(Color.TRANSPARENT, "None"))
    }

    private val fonts = listOf(
        Pair(TextRenderer.TextFontFamily.DEFAULT, "Default"),
        Pair(TextRenderer.TextFontFamily.SERIF, "Serif"),
        Pair(TextRenderer.TextFontFamily.SANS_SERIF, "Sans Serif"),
        Pair(TextRenderer.TextFontFamily.MONOSPACE, "Monospace")
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
