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
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentCinematicLutShadersBinding

class CinematicLutShadersFragment : Fragment() {

    private var _binding: FragmentCinematicLutShadersBinding? = null
    private val binding get() = _binding!!
    private var selectedMediaUri: Uri? = null

    private val selectMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.tvSelectedMedia.text = "الملف المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCinematicLutShadersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val luts = arrayOf(
            "سينمائي برتقالي وأزرق (Teal & Orange)",
            "كلاسيكي دافئ (Vintage Sepia)",
            "نيون ليلي (Cyberpunk Neon)",
            "درامي دافئ (Dramatic Warm)"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, luts)
        binding.spLutPreset.adapter = adapter

        binding.btnSelectVideoImage.setOnClickListener {
            selectMediaLauncher.launch("video/*")
        }

        binding.btnApplyLut.setOnClickListener {
            if (selectedMediaUri == null) {
                Toast.makeText(requireContext(), "الرجاء اختيار ملف فيديو أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val preset = binding.spLutPreset.selectedItem.toString()
            Toast.makeText(requireContext(), "جاري تطبيق الفلتر السينمائي ($preset) بحسابات 3D LUT...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
