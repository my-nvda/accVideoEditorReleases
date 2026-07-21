package com.example.accessiblevideoeditor.ui.fragments

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.ItemHistoryBinding
import com.example.accessiblevideoeditor.media.HistoryItem
import com.example.accessiblevideoeditor.ui.AppStrings

class HistoryAdapter(
    private val context: Context,
    private var items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onShareClick: (HistoryItem) -> Unit,
    private val onRenameClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }
            binding.btnMenu.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = items[position]
                    val popup = PopupMenu(context, binding.btnMenu)
                    popup.menu.add(0, 1, 0, AppStrings.get(context, R.string.string_172))
                    popup.menu.add(0, 2, 0, AppStrings.get(context, R.string.string_174))
                    popup.menu.add(0, 3, 0, AppStrings.get(context, R.string.string_175))
                    
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> onShareClick(item)
                            2 -> onRenameClick(item)
                            3 -> onDeleteClick(item)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.name
        holder.binding.tvDate.text = DateFormat.format("yyyy-MM-dd HH:mm", item.timestamp).toString()
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
