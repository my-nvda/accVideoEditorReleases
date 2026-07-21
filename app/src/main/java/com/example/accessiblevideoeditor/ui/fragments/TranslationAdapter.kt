package com.example.accessiblevideoeditor.ui.fragments

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.accessiblevideoeditor.databinding.ItemTranslationBinding

class TranslationAdapter(
    private val context: Context,
    private var keys: List<String>,
    private val originalStrings: Map<String, String>,
    private val translations: MutableMap<String, String>,
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
        holder.binding.etTranslation.setText(translations[key] ?: "")
        
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
}
