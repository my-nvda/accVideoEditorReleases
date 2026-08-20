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
import com.example.accessiblevideoeditor.databinding.FragmentSpeedControlBinding
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

class SpeedControlFragment : Fragment() {

    private var _binding: FragmentSpeedControlBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private val speeds = listOf("0.25x", "0.5x", "0.75x", "1.25x", "1.5x", "2.0x", "4.0x")
    private val speedValues = listOf(0.25f, 0.5f, 0.75f, 1.25f, 1.5f, 2.0f, 4.0f)

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
        _binding = FragmentSpeedControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speeds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSpeed.adapter = adapter
        binding.spinnerSpeed.setSelection(4) // default 1.5x

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val speedFactor = speedValues[binding.spinnerSpeed.selectedItemPosition]
            processSpeed(uri, speedFactor)
        }
    }

    private fun processSpeed(uri: Uri, factor: Float) {
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = MediaUtils.copyUriToTempFile(safeContext, uri, "speed_input_${System.currentTimeMillis()}")
                if (tempInput != null && tempInput.exists()) {
                    withContext(Dispatchers.Main) {
                        val currentContext = context ?: return@withContext
                        ProcessingManager.startProcessing("Processing speed change...")
                    }
                    val isVideo = MediaUtils.isVideoFile(safeContext, uri)
                    val ext = if (isVideo) "mp4" else "mp3"
                    val outputPath = safeContext.cacheDir.absolutePath + "/speed_out_${System.currentTimeMillis()}.$ext"

                    val audioFilter = when (factor) {
                        0.25f -> "atempo=0.5,atempo=0.5"
                        0.5f -> "atempo=0.5"
                        0.75f -> "atempo=0.75"
                        1.25f -> "atempo=1.25"
                        1.5f -> "atempo=1.5"
                        2.0f -> "atempo=2.0"
                        4.0f -> "atempo=2.0,atempo=2.0"
                        else -> "atempo=1.0"
                    }

                    val totalDuration = FFmpegProcessor.getMediaDurationMs(tempInput.absolutePath)
                    val targetDuration = if (totalDuration > 0f) totalDuration / factor else null

                    val commandArgs = mutableListOf<String>()
                    commandArgs.addAll(listOf("-y", "-i", tempInput.absolutePath))

                    if (isVideo) {
                        val hasAudio = com.example.accessiblevideoeditor.media.FFmpegProcessor.hasAudioTrack(tempInput.absolutePath)
                        val ptsFactor = 1.0f / factor
                        if (hasAudio) {
                            val filter = "[0:v]setpts=$ptsFactor*PTS[v];[0:a]$audioFilter[a]"
                            commandArgs.addAll(listOf(
                                "-filter_complex", filter,
                                "-map", "[v]", "-map", "[a]",
                                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
                                "-c:a", "aac", "-b:a", "192k",
                                outputPath
                            ))
                        } else {
                            val filter = "[0:v]setpts=$ptsFactor*PTS[v][v]"
                            commandArgs.addAll(listOf(
                                "-filter_complex", filter,
                                "-map", "[v]",
                                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
                                outputPath
                            ))
                        }
                    } else {
                        commandArgs.addAll(listOf(
                            "-af", audioFilter,
                            "-c:a", "libmp3lame", "-q:a", "2",
                            outputPath
                        ))
                    }

                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = targetDuration)
                    if (success) {
                        val mime = if (isVideo) "video/mp4" else "audio/mp3"
                        FileUtils.saveToGallery(safeContext, File(outputPath), mime)
                        withContext(Dispatchers.Main) {
                            val currentContext = context ?: return@withContext
                            Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_240), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            val currentContext = context ?: return@withContext
                            Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_241), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    ProcessingManager.showError(e.message ?: "Speed change failed")
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

