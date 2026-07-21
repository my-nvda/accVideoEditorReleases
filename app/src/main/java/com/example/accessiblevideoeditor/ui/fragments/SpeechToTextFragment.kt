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
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentSpeechToTextBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeechToTextFragment : Fragment() {

    private var _binding: FragmentSpeechToTextBinding? = null
    private val binding get() = _binding!!

    private var selectedMediaUri: Uri? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_16)
            binding.btnTranscribe.isEnabled = true
            binding.tilTranscribedText.visibility = View.GONE
            binding.btnCopyText.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeechToTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectAudio.setOnClickListener {
            mediaPickerLauncher.launch("audio/*")
        }

        binding.btnTranscribe.setOnClickListener {
            transcribeAudio()
        }

        binding.btnCopyText.setOnClickListener {
            val text = binding.etTranscribedText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Transcribed Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_141), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun transcribeAudio() {
        val uri = selectedMediaUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val processMsg = AppStrings.get(requireContext(), R.string.string_111)
            ProcessingManager.startProcessing(processMsg, cancellable = true)
            ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
            
            var transcribedText = ""
            try {
                val apiKey = SettingsManager.geminiApiKey
                if (apiKey.isBlank()) {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_3)
                } else {
                    val model = GenerativeModel(
                        modelName = "gemini-2.5-flash",
                        apiKey = apiKey
                    )
                    val bytes = withContext(Dispatchers.IO) {
                        val inputStream = requireContext().contentResolver.openInputStream(uri)
                        inputStream?.readBytes() ?: ByteArray(0)
                    }
                    val mimeType = requireContext().contentResolver.getType(uri) ?: "audio/mpeg"
                    val inputContent = content {
                        blob(mimeType, bytes)
                        text(AppStrings.get(requireContext(), R.string.string_2))
                    }
                    transcribedText = withContext(Dispatchers.IO) {
                        model.generateContent(inputContent).text ?: AppStrings.get(requireContext(), R.string.string_71)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("Unexpected Response")) {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_228)
                } else {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_73, errorMsg)
                }
            } finally {
                ProcessingManager.stopProcessing()
                binding.etTranscribedText.setText(transcribedText)
                binding.tilTranscribedText.visibility = View.VISIBLE
                binding.btnCopyText.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
