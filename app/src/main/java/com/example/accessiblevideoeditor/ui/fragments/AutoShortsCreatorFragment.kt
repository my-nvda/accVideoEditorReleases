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
import com.example.accessiblevideoeditor.databinding.FragmentAutoShortsCreatorBinding

class AutoShortsCreatorFragment : Fragment() {

    private var _binding: FragmentAutoShortsCreatorBinding? = null
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
        _binding = FragmentAutoShortsCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val options = arrayOf(
            "قالب Shorts العمودي (9:16)",
            "قالب TikTok المزودج (9:16)",
            "قالب Instagram Reels السينمائي"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        binding.spTemplateRatio.adapter = adapter

        binding.btnSelectMedia.setOnClickListener {
            selectMediaLauncher.launch("video/*")
        }

        binding.btnCreateShorts.setOnClickListener {
            if (selectedMediaUri == null) {
                Toast.makeText(requireContext(), "الرجاء اختيار ملف ميديا أو فيديو أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val template = binding.spTemplateRatio.selectedItem.toString()
            Toast.makeText(requireContext(), "جاري توليد وإنشاء مقطع Shorts تلقائياً باستخدام $template...", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
