package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentFastConverterBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.HistoryManager
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FastConverterFragment : Fragment() {

    private var _binding: FragmentFastConverterBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private val formats = arrayOf("MP4", "MKV", "AVI", "GIF")

    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            binding.btnSelectFile.text = AppStrings.get(requireContext(), R.string.string_88)
            binding.btnProcess.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFastConverterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectFile.setOnClickListener {
            pickerLauncher.launch("video/*")
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, formats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spFormat.adapter = adapter

        binding.btnProcess.setOnClickListener {
            selectedUri?.let { uri ->
                val format = binding.spFormat.selectedItem.toString()
                processVideo(uri, format)
            }
        }
    }

    private fun processVideo(uri: Uri, format: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = MediaUtils.copyUriToTempFile(requireContext(), uri, "temp_convert_${System.currentTimeMillis()}.${format.lowercase()}")
            if (tempFile != null) {
                withContext(Dispatchers.Main) {
                    SoundManager.playProcessing()
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_111), true)
                }

                val outputFile = File(requireContext().cacheDir, "output_converted_${System.currentTimeMillis()}.${format.lowercase()}")
                
                val commandArgs = arrayOf("-y", "-i", tempFile.absolutePath, "-c", "copy", outputFile.absolutePath)
                val success = FFmpegProcessor.executeWithProgress(commandArgs, tempFile.absolutePath)

                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    if (success) {
                        SoundManager.playSuccess()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_SHORT).show()
                        val mimeType = when (format.uppercase()) {
                            "MP4" -> "video/mp4"
                            "MKV" -> "video/x-matroska"
                            "AVI" -> "video/x-msvideo"
                            "GIF" -> "image/gif"
                            else -> "video/mp4"
                        }
                        val isImage = format.uppercase() == "GIF"
                        val finalUri = if (isImage) {
                            MediaUtils.saveImageToGallery(requireContext(), outputFile, "converted_video.gif", mimeType)
                        } else {
                            MediaUtils.saveVideoToGallery(requireContext(), outputFile, "converted_video.${format.lowercase()}", mimeType)
                        }
                        if (finalUri != null) {
                            HistoryManager.saveToHistory(requireContext(), com.example.accessiblevideoeditor.media.HistoryItem(uriString = finalUri.toString(), name = "converted_video.${format.lowercase()}", type = if (isImage) "image" else "video", timestamp = System.currentTimeMillis()))
                        }
                    } else {
                        SoundManager.playError()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    SoundManager.playError()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

