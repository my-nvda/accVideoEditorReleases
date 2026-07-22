package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accessiblevideoeditor.databinding.FragmentPluginsBinding
import com.example.accessiblevideoeditor.plugins.PluginManager
import com.example.accessiblevideoeditor.plugins.PluginModel
import com.example.accessiblevideoeditor.plugins.PluginsAdapter
import kotlinx.coroutines.launch

class PluginsFragment : Fragment() {

    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PluginsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        adapter = PluginsAdapter(
            onInstallClick = { plugin ->
                if (plugin.isInstalled) {
                    Toast.makeText(requireContext(), "الملحق ${plugin.title} مفعل وجاهز للاستخدام!", Toast.LENGTH_SHORT).show()
                } else {
                    installPlugin(plugin)
                }
            },
            onUninstallClick = { plugin ->
                PluginManager.uninstallPlugin(requireContext(), plugin.id)
                Toast.makeText(requireContext(), "تمت إزالة الملحق ${plugin.title}", Toast.LENGTH_SHORT).show()
                loadPlugins()
            }
        )

        binding.rvPlugins.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlugins.adapter = adapter

        loadPlugins()
    }

    private fun loadPlugins() {
        binding.pbLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val list = PluginManager.fetchAvailablePlugins(requireContext())
            binding.pbLoading.visibility = View.GONE
            adapter.submitList(list)
        }
    }

    private fun installPlugin(plugin: PluginModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "جاري تنزيل الملحق ${plugin.title}...", Toast.LENGTH_SHORT).show()
            val success = PluginManager.downloadAndInstallPlugin(requireContext(), plugin) { progress ->
                val currentList = PluginManager.pluginsState.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == plugin.id }
                if (index >= 0) {
                    currentList[index] = currentList[index].copy(isDownloading = true, downloadProgress = progress)
                    adapter.submitList(currentList)
                }
            }

            if (success) {
                Toast.makeText(requireContext(), "تم تثبيت الملحق ${plugin.title} بنجاح!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "فشل تثبيت الملحق", Toast.LENGTH_SHORT).show()
            }
            loadPlugins()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
