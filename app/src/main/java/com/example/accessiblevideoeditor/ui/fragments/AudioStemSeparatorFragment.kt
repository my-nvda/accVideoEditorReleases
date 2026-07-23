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
import com.example.accessiblevideoeditor.databinding.FragmentAudioStemSeparatorBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager

class AudioStemSeparatorFragment : Fragment() {

    private var _binding: FragmentAudioStemSeparatorBinding? = null
    private val binding get() = _binding!!
    private var selectedAudioUri: Uri? = null

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedAudioUri = uri
            binding.tvSelectedAudio.text = "الملف المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioStemSeparatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val modelFile = CloudConfigManager.getDownloadedModelFile(requireContext(), "btnAudioStemSeparator")
        if (modelFile != null && modelFile.exists()) {
            binding.tvModelStatus.text = "حالة النموذج: نموذج Spleeter 2Stems ONNX محمل محلياً ✅ (${modelFile.length() / (1024 * 1024)}MB)"
        }

        binding.btnSelectAudio.setOnClickListener {
            selectAudioLauncher.launch("audio/*")
        }

        binding.btnProcessSeparation.setOnClickListener {
            if (selectedAudioUri == null) {
                Toast.makeText(requireContext(), "الرجاء اختيار ملف صوتي أو فيديو أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mode = if (binding.rbSeparateVocals.isChecked) "الصوت البشري (Vocals)" else "الموسيقى والآلات (Music)"
            Toast.makeText(requireContext(), "جاري فصل وعزل $mode باستخدام نموذج Spleeter ONNX...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
