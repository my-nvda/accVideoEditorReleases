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
    
    private var currentSlideIndex = 0
    private var previewMediaPlayer: android.media.MediaPlayer? = null
    private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopPreviewRunnable = Runnable {
        stopAudioPreview()
    }

    private val imagesPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImageUris = uris
        if (uris.isNotEmpty()) {
            binding.tvSelectedImagesCount.visibility = View.VISIBLE
            binding.tvSelectedImagesCount.text = AppStrings.get(requireContext(), R.string.string_8, uris.size)
            currentSlideIndex = 0
            binding.layoutPreviewContainer.visibility = View.VISIBLE
            updatePreviewUI()
        } else {
            binding.tvSelectedImagesCount.visibility = View.GONE
            binding.layoutPreviewContainer.visibility = View.GONE
            stopAudioPreview()
        }
        updateApplyButtonState()
    }

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedAudioUri = uri
        stopAudioPreview()
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
                stopAudioPreview()
                binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_99)
            }
        }

        binding.btnSelectAudio.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }

        binding.btnPrevSlide.setOnClickListener {
            if (currentSlideIndex > 0) {
                currentSlideIndex--
                updatePreviewUI()
            }
        }

        binding.btnNextSlide.setOnClickListener {
            if (currentSlideIndex < selectedImageUris.size - 1) {
                currentSlideIndex++
                updatePreviewUI()
            }
        }

        binding.btnPreviewAudio.setOnClickListener {
            if (previewMediaPlayer?.isPlaying == true) {
                stopAudioPreview()
            } else {
                playAudioPreview()
            }
        }

        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            processSlideshow()
        }
    }

    private fun updatePreviewUI() {
        if (selectedImageUris.isNotEmpty() && currentSlideIndex in selectedImageUris.indices) {
            stopAudioPreview()
            binding.ivSlidePreview.setImageURI(selectedImageUris[currentSlideIndex])
            binding.tvSlideIndexIndicator.text = "${currentSlideIndex + 1} / ${selectedImageUris.size}"
            binding.btnPrevSlide.isEnabled = currentSlideIndex > 0
            binding.btnNextSlide.isEnabled = currentSlideIndex < selectedImageUris.size - 1
            
            // Set content description for screen readers
            binding.ivSlidePreview.contentDescription = AppStrings.get(requireContext(), R.string.cd_slide_preview, currentSlideIndex + 1, selectedImageUris.size)
        }
    }

    private fun playAudioPreview() {
        stopAudioPreview()
        
        val audioUri = selectedAudioUri
        if (audioUri == null) {
            Toast.makeText(context, AppStrings.get(requireContext(), R.string.toast_select_audio_for_preview), Toast.LENGTH_SHORT).show()
            return
        }

        val durationStr = binding.etDuration.text.toString()
        val duration = durationStr.toIntOrNull() ?: 3
        val startMs = currentSlideIndex * duration * 1000

        try {
            previewMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(requireContext(), audioUri)
                prepare()
                seekTo(startMs)
                start()
            }
            binding.btnPreviewAudio.text = AppStrings.get(requireContext(), R.string.btn_stop_preview)
            previewHandler.postDelayed(stopPreviewRunnable, duration * 1000L)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, AppStrings.get(requireContext(), R.string.toast_audio_preview_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioPreview() {
        previewHandler.removeCallbacks(stopPreviewRunnable)
        try {
            previewMediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (_: Exception) {}
        previewMediaPlayer = null
        if (_binding != null) {
            binding.btnPreviewAudio.text = AppStrings.get(requireContext(), R.string.btn_preview_current_slide)
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
        stopAudioPreview()
        super.onDestroyView()
        _binding = null
    }
}

