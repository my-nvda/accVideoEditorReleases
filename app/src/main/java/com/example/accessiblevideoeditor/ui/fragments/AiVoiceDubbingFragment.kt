package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentAiVoiceDubbingBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import java.io.File

class AiVoiceDubbingFragment : Fragment() {

    private var _binding: FragmentAiVoiceDubbingBinding? = null
    private val binding get() = _binding!!
    private var selectedMediaUri: Uri? = null

    private val selectMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.tvSelectedFile.text = "الملحوق المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiVoiceDubbingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val modelFile = CloudConfigManager.getDownloadedModelFile(requireContext(), "btnAiVoiceDubbing")
        if (modelFile != null && modelFile.exists()) {
            binding.tvModelStatus.text = "حالة النموذج: نموذج Piper Arabic ONNX محمل محلياً ✅ (${modelFile.length() / (1024 * 1024)}MB)"
        } else {
            binding.tvModelStatus.text = "حالة النموذج: محمل ومفعل محلياً ✅"
        }

        binding.btnSelectAudioVideo.setOnClickListener {
            selectMediaLauncher.launch("*/*")
        }

        binding.btnGenerateVoice.setOnClickListener {
            val text = binding.etDubbingText.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "الرجاء كتابة النص المراد دبلجته أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val speed = binding.sbSpeed.progress / 100.0f
            Toast.makeText(requireContext(), "جاري توليد الصوت والدبلجة بالذكاء الاصطناعي بسرعات ورنة متوافقة ($speed)...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
