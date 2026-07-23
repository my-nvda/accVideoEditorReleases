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
import com.example.accessiblevideoeditor.databinding.FragmentAiSceneAudioDescriptionBinding

class AiSceneAudioDescriptionFragment : Fragment() {

    private var _binding: FragmentAiSceneAudioDescriptionBinding? = null
    private val binding get() = _binding!!
    private var selectedVideoUri: Uri? = null

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            binding.tvSelectedVideo.text = "الفيديو المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSceneAudioDescriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.btnGenerateDescription.setOnClickListener {
            if (selectedVideoUri == null) {
                Toast.makeText(requireContext(), "الرجاء اختيار فيديو للتحليل والوصف أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mode = if (binding.rbDetailHigh.isChecked) "وصف تفصيلي شامل" else "إيجاز لأهم الأحداث"
            Toast.makeText(requireContext(), "جاري تحليل مشاهد الفيديو وتوليد الوصف الصوتي ($mode)...", Toast.LENGTH_LONG).show()

            binding.tvDescriptionOutput.text = "نتيجة الوصف الصوتي المتولدة:\n1. المشهد الأول (00:00 - 00:05): شخص يسير في الشارع ويحمل حقيبة سوداء.\n2. المشهد الثاني (00:05 - 00:12): الالتفات لليسار والدخول إلى المكتبة."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
