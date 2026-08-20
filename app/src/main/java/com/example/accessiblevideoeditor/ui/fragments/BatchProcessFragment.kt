package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentBatchProcessBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchProcessFragment : Fragment() {

    private var _binding: FragmentBatchProcessBinding? = null
    private val binding get() = _binding!!

    private var selectedUris: List<Uri> = emptyList()
    private val operations = listOf(R.string.string_95, R.string.string_22, R.string.string_94)
    private var selectedOperationId: Int = operations[0]

    private val multipleMediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            updateUIForSelection()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatchProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectMedia.setOnClickListener {
            multipleMediaPickerLauncher.launch("video/*")
        }

        setupSpinner()

        binding.btnProcess.setOnClickListener {
            processBatch()
        }
    }

    private fun setupSpinner() {
        val operationNames = operations.map { AppStrings.get(requireContext(), it) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, operationNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOperation.adapter = adapter

        binding.spinnerOperation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOperationId = operations[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUIForSelection() {
        binding.tvSelectedCount.text = AppStrings.get(requireContext(), R.string.string_6, selectedUris.size.toString())
        binding.tvSelectedCount.visibility = View.VISIBLE
        binding.tvOperationLabel.visibility = View.VISIBLE
        binding.spinnerOperation.visibility = View.VISIBLE
        binding.btnProcess.isEnabled = true
    }

    private fun processBatch() {
        if (selectedUris.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_32), cancellable = true)
                    ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                }
                var successCount = 0
                selectedUris.forEachIndexed { index, uri ->
                    val tempFile = MediaUtils.copyUriToTempFile(requireContext(), uri, "batch_temp_${System.currentTimeMillis()}_${index}.mp4")
                    if (tempFile != null) {
                        val inputPath = tempFile.absolutePath
                        val isAudioExtraction = (selectedOperationId == R.string.string_22)
                        val ext = if (isAudioExtraction) "mp3" else "mp4"
                        val outputPath = requireContext().cacheDir.absolutePath + "/batch_${System.currentTimeMillis()}_${index}.$ext"
                        
                        val success = if (isAudioExtraction) {
                            FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                        } else if (selectedOperationId == R.string.string_95) {
                            val commandArgs = arrayOf("-y", "-i", inputPath, "-map", "0:v", "-map", "0:a?", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18", "-c:a", "aac", outputPath)
                            FFmpegProcessor.executeWithProgress(commandArgs, inputPath)
                        } else {
                            FFmpegProcessor.compressVideo(inputPath, outputPath)
                        }

                        if (success) {
                            val mimeType = if (isAudioExtraction) "audio/mpeg" else "video/mp4"
                            val savedUri = FileUtils.saveToGallery(requireContext(), File(outputPath), mimeType)
                            if (savedUri != null) {
                                successCount++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (successCount > 0) {
                        SoundManager.playSuccess()
                        Toast.makeText(requireContext(), "${AppStrings.get(requireContext(), R.string.string_182)} ($successCount/${selectedUris.size})", Toast.LENGTH_LONG).show()
                    } else {
                        SoundManager.playError()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
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
