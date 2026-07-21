package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentSimpleProcessBinding
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

class BoostVolumeFragment : Fragment() {

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
        
        val title = AppStrings.get(requireContext(), R.string.string_86)
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
                processVideo(uri)
            }
        }
    }

    private fun processVideo(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = MediaUtils.copyUriToTempFile(requireContext(), uri, "temp_boost_${System.currentTimeMillis()}.mp4")
            if (tempFile != null) {
                withContext(Dispatchers.Main) {
                    SoundManager.playProcessing()
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_111), true)
                }

                val outputFile = File(requireContext().cacheDir, "output_boosted_${System.currentTimeMillis()}.mp4")
                
                val success = FFmpegProcessor.boostVolume(
                    sourceVideo = tempFile.absolutePath,
                    multiplier = 3.0f,
                    outputPath = outputFile.absolutePath
                )

                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    if (success) {
                        SoundManager.playSuccess()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_87), Toast.LENGTH_SHORT).show()
                        val finalUri = MediaUtils.saveVideoToGallery(requireContext(), outputFile, "boosted_video.mp4", "video/mp4")
                        if (finalUri != null) {
                            HistoryManager.saveToHistory(requireContext(), com.example.accessiblevideoeditor.media.HistoryItem(uriString = finalUri.toString(), name = "boosted_video.mp4", type = "video", timestamp = System.currentTimeMillis()))
                        }
                    } else {
                        SoundManager.playError()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_89), Toast.LENGTH_SHORT).show()
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
