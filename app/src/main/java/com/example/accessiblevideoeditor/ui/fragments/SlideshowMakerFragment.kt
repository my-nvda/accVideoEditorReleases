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
import com.example.accessiblevideoeditor.databinding.FragmentSlideshowMakerBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SlideshowMakerFragment : Fragment() {

    private var _binding: FragmentSlideshowMakerBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUris: List<Uri> = emptyList()
    private var selectedAudioUri: Uri? = null

    private val imagesPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImageUris = uris
        if (uris.isNotEmpty()) {
            binding.tvSelectedImagesCount.visibility = View.VISIBLE
            binding.tvSelectedImagesCount.text = AppStrings.get(requireContext(), R.string.string_8, uris.size)
        } else {
            binding.tvSelectedImagesCount.visibility = View.GONE
        }
        updateApplyButtonState()
    }

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedAudioUri = uri
        if (uri != null) {
            binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_85)
        } else {
            binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_99)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowMakerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectImages.setOnClickListener {
            imagesPickerLauncher.launch("image/*")
        }

        binding.cbAddAudio.setOnCheckedChangeListener { _, isChecked ->
            binding.btnSelectAudio.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                selectedAudioUri = null
                binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_99)
            }
        }

        binding.btnSelectAudio.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }

        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            processSlideshow()
        }
    }

    private fun updateApplyButtonState() {
        binding.btnApply.isEnabled = selectedImageUris.size > 1
    }

    private fun processSlideshow() {
        val durationStr = binding.etDuration.text.toString()
        val duration = durationStr.toIntOrNull() ?: 3

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_111), true)
                }

                val imagePaths = selectedImageUris.mapIndexedNotNull { index, uri ->
                    MediaUtils.copyUriToTempFile(requireContext(), uri, "img_${System.currentTimeMillis()}_$index.jpg")?.absolutePath
                }

                var audioPath: String? = null
                val audioUri = selectedAudioUri
                if (audioUri != null) {
                    audioPath = MediaUtils.copyUriToTempFile(requireContext(), audioUri, "audio_${System.currentTimeMillis()}.mp3")?.absolutePath
                }

                if (imagePaths.isNotEmpty()) {
                    val outputPath = requireContext().cacheDir.absolutePath + "/slideshow_${System.currentTimeMillis()}.mp4"
                    val success = FFmpegProcessor.createSlideshow(imagePaths, audioPath, duration, outputPath)
                    
                    if (success) {
                        FileUtils.saveToGallery(requireContext(), File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        ProcessingManager.showError(e.message ?: "Unknown error occurred")
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.stopProcessing()
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

