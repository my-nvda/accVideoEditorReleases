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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentTickerTextBinding
import com.example.accessiblevideoeditor.media.TextRenderer
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.components.TextCustomizationHelper
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class TickerTextFragment : Fragment() {

    private var _binding: FragmentTickerTextBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var textOptions = TextRenderer.TextOptions(text = "")

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.string_70)
        }
        updateApplyButtonState()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTickerTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        TextCustomizationHelper(requireContext(), binding.textPanel) { newOptions ->
            textOptions = newOptions
            updateApplyButtonState()
        }

        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            val vUri = selectedVideoUri
            if (vUri == null) {
                Toast.makeText(requireContext(), "Please select a video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (textOptions.text.isBlank()) {
                Toast.makeText(requireContext(), "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processTickerText(vUri)
        }
    }

    private fun updateApplyButtonState() {
        binding.btnApply.isEnabled = selectedVideoUri != null && textOptions.text.isNotBlank()
    }

    private fun processTickerText(vUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_62))
                }
                
                val inputPath = FileUtils.getPathFromUri(requireContext(), vUri)
                val outputPath = requireContext().cacheDir.absolutePath + "/ticker_${System.currentTimeMillis()}.mp4"
                val pngFile = File(requireContext().cacheDir, "ticker_${System.currentTimeMillis()}.png")
                
                if (inputPath != null) {
                    TextRenderer.createTickerPng(textOptions, pngFile)
                    
                    val yExpr = when (textOptions.position) {
                        TextRenderer.TextPosition.TOP -> "H/10"
                        TextRenderer.TextPosition.CENTER -> "(H-h)/2"
                        TextRenderer.TextPosition.BOTTOM -> "H-H/10-h"
                    }
                    val command = "-y -i \"${inputPath}\" -i \"${pngFile.absolutePath}\" -filter_complex \"[0:v]scale=trunc(iw/2)*2:trunc(ih/2)*2[main];[1:v]format=rgba[img];[main][img]overlay=x='W-mod(t*150,W+w)':y='$yExpr'\" -c:v mpeg4 -q:v 2 -c:a copy \"${outputPath}\""
                    
                    val session = FFmpegKit.execute(command)
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        FileUtils.saveToGallery(requireContext(), File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_240), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val logs = session.failStackTrace ?: session.allLogsAsString ?: "Unknown Error"
                        val detailedLog = "Command:\n$command\n\nLogs:\n$logs"
                        withContext(Dispatchers.Main) {
                            ProcessingManager.showError(detailedLog)
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_241), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
