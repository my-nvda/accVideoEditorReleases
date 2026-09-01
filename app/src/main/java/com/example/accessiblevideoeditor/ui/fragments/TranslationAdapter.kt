package com.example.accessiblevideoeditor.ui.fragments

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.accessiblevideoeditor.databinding.ItemTranslationBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.R

class TranslationAdapter(
    private val context: Context,
    private var keys: List<String>,
    private val originalStrings: Map<String, String>,
    private var translations: MutableMap<String, String>,
    private var localTranslations: Map<String, String> = emptyMap(),
    private var isCloudSource: Boolean = false,
    private val onApplySuggestion: (String, String) -> Unit = { _, _ -> },
    private val onTranslationChanged: (String, String) -> Unit
) : RecyclerView.Adapter<TranslationAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTranslationBinding) : RecyclerView.ViewHolder(binding.root) {
        var currentKey: String? = null
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranslationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val key = keys[position]
        holder.currentKey = key
        
        holder.binding.tvOriginalText.text = originalStrings[key] ?: ""
        
        // Remove old watcher
        if (holder.textWatcher != null) {
            holder.binding.etTranslation.removeTextChangedListener(holder.textWatcher)
        }
        
        // Set text
        val currentVal = translations[key] ?: ""
        holder.binding.etTranslation.setText(currentVal)
        
        // Set accessible hint and content description
        val originalVal = originalStrings[key] ?: ""
        val hintText = AppStrings.get(context, R.string.string_254, originalVal)
        holder.binding.tilTranslation.hint = hintText
        holder.binding.etTranslation.contentDescription = hintText
        
        // Setup suggestions for Cloud tab
        if (isCloudSource && currentVal.isBlank()) {
            val localVal = localTranslations[key] ?: ""
            if (localVal.isNotBlank()) {
                holder.binding.layoutSuggestion.visibility = View.VISIBLE
                holder.binding.tvSuggestionLabel.text = AppStrings.get(context, R.string.string_translation_suggestion, localVal)
                holder.binding.btnApplySuggestion.setOnClickListener {
                    onApplySuggestion(key, localVal)
                }
            } else {
                holder.binding.layoutSuggestion.visibility = View.GONE
            }
        } else {
            holder.binding.layoutSuggestion.visibility = View.GONE
        }
        
        // Add new watcher
        holder.textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (holder.currentKey == key) {
                    onTranslationChanged(key, s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.binding.etTranslation.addTextChangedListener(holder.textWatcher)
    }

    override fun getItemCount(): Int = keys.size

    fun updateKeys(newKeys: List<String>) {
        keys = newKeys
        notifyDataSetChanged()
    }
    
    fun updateData(
        newIsCloudSource: Boolean,
        newTranslations: MutableMap<String, String>,
        newLocalTranslations: Map<String, String>
    ) {
        isCloudSource = newIsCloudSource
        translations = newTranslations
        localTranslations = newLocalTranslations
        notifyDataSetChanged()
    }
}
