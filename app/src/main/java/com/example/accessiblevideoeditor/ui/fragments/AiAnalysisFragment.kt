package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentAiAnalysisBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiAnalysisFragment : Fragment() {

    private var _binding: FragmentAiAnalysisBinding? = null
    private val binding get() = _binding!!

    private var selectedImage: Uri? = null
    private var selectedVideo: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImage = uri
            selectedVideo = null
            updateAnalyzeButtonState()
        }
    }

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideo = uri
            selectedImage = null
            updateAnalyzeButtonState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        updateAnalyzeButtonState()

        binding.btnAnalyze.setOnClickListener {
            analyzeMedia()
        }
    }

    private fun updateAnalyzeButtonState() {
        binding.btnAnalyze.isEnabled = (selectedImage != null || selectedVideo != null) && SettingsManager.geminiApiKey.isNotBlank()
    }

    private fun analyzeMedia() {
        val userQuestion = binding.etQuestion.text.toString()

        viewLifecycleOwner.lifecycleScope.launch {
            ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_91), cancellable = true)
            ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
            try {
                val apiKeyToUse = SettingsManager.geminiApiKey.trim()
                val modelToUse = SettingsManager.geminiModel

                val model = GenerativeModel(
                    modelName = modelToUse,
                    apiKey = apiKeyToUse
                )
                
                val bitmaps = if (selectedImage != null) {
                    withContext(Dispatchers.IO) {
                        val inputStream = requireContext().contentResolver.openInputStream(selectedImage!!)
                        listOf(android.graphics.BitmapFactory.decodeStream(inputStream))
                    }
                } else {
                    emptyList()
                }

                val videoBytes = if (selectedVideo != null) {
                    withContext(Dispatchers.IO) {
                        requireContext().contentResolver.openInputStream(selectedVideo!!)?.use { it.readBytes() }
                    }
                } else null

                val mimeType = if (selectedVideo != null) {
                    requireContext().contentResolver.getType(selectedVideo!!) ?: "video/mp4"
                } else null

                val inputContent = content {
                    if (selectedImage != null) {
                        bitmaps.forEach { image(it) }
                    } else if (selectedVideo != null && videoBytes != null && mimeType != null) {
                        blob(mimeType, videoBytes)
                    }
                    val promptText = if (userQuestion.isNotBlank()) userQuestion else AppStrings.get(requireContext(), R.string.string_1)
                    text(promptText)
                }

                var response = ""
                try {
                    response = withContext(Dispatchers.IO) {
                        model.generateContent(inputContent).text ?: AppStrings.get(requireContext(), R.string.string_66)
                    }
                } catch (e: Exception) {
                    // Fallback
                    val fallbackModel = GenerativeModel(
                        modelName = "gemini-2.0-flash",
                        apiKey = apiKeyToUse
                    )
                    response = withContext(Dispatchers.IO) {
                        fallbackModel.generateContent(inputContent).text ?: AppStrings.get(requireContext(), R.string.string_66)
                    }
                }

                binding.tvDescription.text = response
            } catch (e: Exception) {
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("Unexpected Response")) {
                    binding.tvDescription.text = AppStrings.get(requireContext(), R.string.string_228)
                } else {
                    binding.tvDescription.text = AppStrings.get(requireContext(), R.string.string_56, errorMsg)
                }
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
