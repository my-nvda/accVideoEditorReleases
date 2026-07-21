package com.example.accessiblevideoeditor.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentHistoryBinding
import com.example.accessiblevideoeditor.media.HistoryItem
import com.example.accessiblevideoeditor.media.HistoryManager
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var historyItems: List<HistoryItem> = emptyList()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter(
            requireContext(),
            historyItems,
            onItemClick = { item ->
                openMedia(item)
            },
            onShareClick = { item ->
                shareMedia(item)
            },
            onRenameClick = { item ->
                showRenameDialog(item)
            },
            onDeleteClick = { item ->
                showDeleteDialog(item)
            }
        )
        binding.rvHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        historyItems = HistoryManager.loadHistory(requireContext())
        adapter.updateItems(historyItems)
        
        if (historyItems.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
        }
    }

    private fun openMedia(item: HistoryItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mimeType = when (item.type) {
                    "video" -> "video/*"
                    "audio" -> "audio/*"
                    else -> "image/*"
                }
                setDataAndType(Uri.parse(item.uriString), mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMedia(item: HistoryItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (item.type) {
                    "video" -> "video/*"
                    "audio" -> "audio/*"
                    else -> "image/*"
                }
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, AppStrings.get(requireContext(), R.string.string_172)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showRenameDialog(item: HistoryItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.name)
            setHint(AppStrings.get(requireContext(), R.string.string_174))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(AppStrings.get(requireContext(), R.string.string_174))
            .setView(editText)
            .setPositiveButton(AppStrings.get(requireContext(), R.string.string_174)) { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    val list = historyItems.map { 
                        if (it == item) it.copy(name = newName) else it
                    }
                    HistoryManager.saveFullHistory(requireContext(), list)
                    loadHistory()
                    SoundManager.playSuccess()
                    Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(AppStrings.get(requireContext(), R.string.string_207), null)
            .show()
    }

    private fun showDeleteDialog(item: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(AppStrings.get(requireContext(), R.string.string_204))
            .setMessage(AppStrings.get(requireContext(), R.string.string_205))
            .setPositiveButton(AppStrings.get(requireContext(), R.string.string_206)) { _, _ ->
                val list = historyItems.toMutableList()
                list.remove(item)
                HistoryManager.saveFullHistory(requireContext(), list)
                loadHistory()
                try {
                    val uri = Uri.parse(item.uriString)
                    requireContext().contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                SoundManager.playSuccess()
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(AppStrings.get(requireContext(), R.string.string_207), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
