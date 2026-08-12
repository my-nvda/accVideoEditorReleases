package com.example.accessiblevideoeditor.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.accessiblevideoeditor.databinding.FragmentAiSceneInspectorBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSceneInspectorFragment : Fragment() {

    private var _binding: FragmentAiSceneInspectorBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = AppStrings.get(requireContext(), R.string.string_16)
            binding.btnAnalyze.isEnabled = true
            binding.tilResultText.visibility = View.GONE
            binding.btnCopyResult.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSceneInspectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectVideo.setOnClickListener {
            mediaPickerLauncher.launch("video/*")
        }

        binding.btnAnalyze.setOnClickListener {
            analyzeVideoScenes()
        }

        binding.btnCopyResult.setOnClickListener {
            val text = binding.etResultText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Scene Analysis", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_141), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun analyzeVideoScenes() {
        val uri = selectedUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.msg_scene_inspector_start), cancellable = true)
            ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])

            var resultText = ""
            try {
                val apiKey = SettingsManager.geminiApiKey
                val currentContext = context
                if (apiKey.isBlank()) {
                    resultText = if (currentContext != null) AppStrings.get(currentContext, R.string.string_3) else "Gemini API key is missing."
                } else if (currentContext != null) {
                    val userModel = SettingsManager.geminiModel
                    val model = GenerativeModel(
                        modelName = if (userModel.isNotBlank()) userModel else "gemini-2.5-flash",
                        apiKey = apiKey
                    )
                    val bytes = withContext(Dispatchers.IO) {
                        val inputStream = currentContext.contentResolver.openInputStream(uri)
                        inputStream?.readBytes() ?: ByteArray(0)
                    }
                    val mimeType = currentContext.contentResolver.getType(uri) ?: "video/mp4"

                    val promptText = AppStrings.get(currentContext, R.string.prompt_scene_inspector)

                    val inputContent = content {
                        blob(mimeType, bytes)
                        text(promptText)
                    }

                    resultText = withContext(Dispatchers.IO) {
                        model.generateContent(inputContent).text ?: AppStrings.get(currentContext, R.string.msg_scene_no_analysis)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                resultText = AppStrings.get(requireContext(), R.string.msg_scene_error, e.message.orEmpty())
            } finally {
                ProcessingManager.stopProcessing()
                binding.etResultText.setText(resultText)
                binding.tilResultText.visibility = View.VISIBLE
                binding.btnCopyResult.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
