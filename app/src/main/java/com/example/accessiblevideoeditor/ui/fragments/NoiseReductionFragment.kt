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
import com.example.accessiblevideoeditor.databinding.FragmentNoiseReductionBinding
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
import java.util.concurrent.CancellationException

class NoiseReductionFragment : Fragment() {

    private var _binding: FragmentNoiseReductionBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = AppStrings.get(requireContext(), R.string.string_16)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoiseReductionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val noiseLabels = listOf(
            AppStrings.get(requireContext(), R.string.noise_mild),
            AppStrings.get(requireContext(), R.string.noise_medium),
            AppStrings.get(requireContext(), R.string.noise_strong)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noiseLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerNoiseLevel.adapter = adapter
        binding.spinnerNoiseLevel.setSelection(1) // default medium

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processNoiseReduction(uri, binding.spinnerNoiseLevel.selectedItemPosition)
        }
    }

    private fun processNoiseReduction(uri: Uri, levelIndex: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = MediaUtils.copyUriToTempFile(requireContext(), uri, "noise_input_${System.currentTimeMillis()}")
                if (tempInput != null && tempInput.exists()) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.title_noise_reduction))
                    }
                    val isVideo = MediaUtils.isVideoFile(requireContext(), uri)
                    val ext = if (isVideo) "mp4" else "mp3"
                    val outputPath = requireContext().cacheDir.absolutePath + "/noise_clean_${System.currentTimeMillis()}.$ext"

                    val audioFilter = when (levelIndex) {
                        0 -> "highpass=f=100,lowpass=f=4000,afftdn=nr=10:nf=-40"
                        1 -> "highpass=f=150,lowpass=f=3800,afftdn=nr=20:nf=-30"
                        2 -> "highpass=f=200,lowpass=f=3500,afftdn=nr=30:nf=-25"
                        else -> "highpass=f=150,lowpass=f=3800,afftdn=nr=20:nf=-30"
                    }

                    val duration = FFmpegProcessor.getMediaDurationMs(tempInput.absolutePath)

                    val commandArgs = mutableListOf<String>()
                    commandArgs.addAll(listOf("-y", "-i", tempInput.absolutePath))

                    if (isVideo) {
                        commandArgs.addAll(listOf(
                            "-af", audioFilter,
                            "-c:v", "copy",
                            "-c:a", "aac", "-b:a", "192k",
                            outputPath
                        ))
                    } else {
                        commandArgs.addAll(listOf(
                            "-af", audioFilter,
                            "-c:a", "libmp3lame", "-q:a", "2",
                            outputPath
                        ))
                    }

                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = if (duration > 0f) duration else null)
                    if (success) {
                        val mime = if (isVideo) "video/mp4" else "audio/mp3"
                        FileUtils.saveToGallery(requireContext(), File(outputPath), mime)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_240), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_241), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    ProcessingManager.showError(e.message ?: "Noise reduction failed")
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

