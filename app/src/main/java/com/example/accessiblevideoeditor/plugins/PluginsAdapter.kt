package com.example.accessiblevideoeditor.plugins

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.accessiblevideoeditor.databinding.ItemPluginBinding

class PluginsAdapter(
    private val onInstallClick: (PluginModel) -> Unit,
    private val onUninstallClick: (PluginModel) -> Unit
) : RecyclerView.Adapter<PluginsAdapter.PluginViewHolder>() {

    private val plugins = mutableListOf<PluginModel>()

    fun submitList(newList: List<PluginModel>) {
        plugins.clear()
        plugins.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginViewHolder {
        val binding = ItemPluginBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PluginViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PluginViewHolder, position: Int) {
        holder.bind(plugins[position])
    }

    override fun getItemCount(): Int = plugins.size

    inner class PluginViewHolder(private val binding: ItemPluginBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(plugin: PluginModel) {
            binding.tvPluginTitle.text = plugin.title
            binding.tvPluginDescription.text = plugin.description
            binding.tvPluginCategorySize.text = "التصنيف: ${plugin.category} • الحجم: ${plugin.sizeMb} ميجابايت"

            if (plugin.isDownloading) {
                binding.pbPluginProgress.visibility = View.VISIBLE
                binding.pbPluginProgress.progress = plugin.downloadProgress
                binding.btnInstallAction.isEnabled = false
                binding.btnInstallAction.text = "جاري التثبيت ${plugin.downloadProgress}%"
                binding.btnUninstall.visibility = View.GONE
            } else {
                binding.pbPluginProgress.visibility = View.GONE
                binding.btnInstallAction.isEnabled = true

                if (plugin.isInstalled) {
                    binding.btnInstallAction.text = "تشغيل الملحق"
                    binding.btnUninstall.visibility = View.VISIBLE
                } else {
                    binding.btnInstallAction.text = "تثبيت الملحق"
                    binding.btnUninstall.visibility = View.GONE
                }
            }

            binding.btnInstallAction.setOnClickListener {
                onInstallClick(plugin)
            }

            binding.btnUninstall.setOnClickListener {
                onUninstallClick(plugin)
            }
        }
    }
}
