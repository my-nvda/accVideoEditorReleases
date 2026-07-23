package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentSimpleProcessBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.HistoryItem
import com.example.accessiblevideoeditor.media.HistoryManager
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExtractAudioFragment : Fragment() {

    private var _binding: FragmentSimpleProcessBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

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
        _binding = FragmentSimpleProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = AppStrings.get(requireContext(), R.string.string_126)
        binding.topAppBar.title = title
        binding.tvTitle.text = title

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectFile.setOnClickListener {
            pickerLauncher.launch("video/*")
        }

        binding.btnProcess.setOnClickListener {
            selectedUri?.let { uri ->
                showFormatDialog(uri)
            }
        }
    }

    private fun showFormatDialog(uri: Uri) {
        val formats = arrayOf("m4a", "mp3", "wav", "aac")
        AlertDialog.Builder(requireContext())
            .setTitle(AppStrings.get(requireContext(), R.string.string_114))
            .setItems(formats) { _, which ->
                processAudioExtraction(uri, formats[which])
            }
            .setNegativeButton(AppStrings.get(requireContext(), R.string.string_207), null)
            .show()
    }

    private fun processAudioExtraction(uri: Uri, format: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = MediaUtils.copyUriToTempFile(requireContext(), uri, "temp_extract_${System.currentTimeMillis()}.mp4")
            if (tempFile != null) {
                withContext(Dispatchers.Main) {
                    SoundManager.playProcessing()
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_41), true)
                }
                
                val outputFile = File(requireContext().cacheDir, "output_extracted_${System.currentTimeMillis()}.$format")
                val success = FFmpegProcessor.extractAudio(
                    sourceVideo = tempFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    format = format
                )

                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    if (success) {
                        val mimeType = when (format) {
                            "mp3" -> "audio/mpeg"
                            "wav" -> "audio/wav"
                            "aac" -> "audio/aac"
                            else -> "audio/mp4"
                        }
                        val savedUri = MediaUtils.saveAudioToGallery(
                            requireContext(), outputFile, "AccessibleEditor_Audio_${System.currentTimeMillis()}.$format", mimeType
                        )
                        if (savedUri != null) {
                            HistoryManager.saveToHistory(
                                requireContext(),
                                HistoryItem(
                                    uriString = savedUri.toString(),
                                    name = "Extracted Audio ($format)",
                                    timestamp = System.currentTimeMillis(),
                                    type = "audio"
                                )
                            )
                        }
                        SoundManager.playSuccess()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_87), Toast.LENGTH_SHORT).show()
                    } else {
                        SoundManager.playError()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_221), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

