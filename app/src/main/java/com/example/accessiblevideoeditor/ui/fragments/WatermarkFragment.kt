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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentWatermarkBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.TextRenderer
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.components.TextCustomizationHelper
import com.example.accessiblevideoeditor.utils.FileUtils
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class WatermarkFragment : Fragment() {

    private var _binding: FragmentWatermarkBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var selectedImageUri: Uri? = null
    private var isTextMode = false
    private var textOptions = TextRenderer.TextOptions(text = "")
    private var selectedPosition = ""
    private var isSelectedMediaVideo = false

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            val mimeType = requireContext().contentResolver.getType(uri)
            isSelectedMediaVideo = mimeType?.startsWith("video/") == true
            
            val segment = uri.lastPathSegment
            if (isSelectedMediaVideo) {
                val fallback = AppStrings.get(requireContext(), R.string.label_video_fallback)
                binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.label_video_prefix, segment ?: fallback)
            } else {
                val fallback = AppStrings.get(requireContext(), R.string.label_image_fallback)
                binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.label_image_prefix, segment ?: fallback)
            }
        }
        updateApplyButtonState()
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            binding.btnSelectImage.text = AppStrings.get(requireContext(), R.string.string_14)
        }
        updateApplyButtonState()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectVideo.text = "اختر فيديو أو صورة"
        binding.btnSelectVideo.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isTextMode = tab?.position == 1
                binding.btnSelectImage.visibility = if (isTextMode) View.GONE else View.VISIBLE
                binding.textPanel.root.visibility = if (isTextMode) View.VISIBLE else View.GONE
                updateApplyButtonState()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        TextCustomizationHelper(requireContext(), binding.textPanel) { newOptions ->
            textOptions = newOptions
            updateApplyButtonState()
        }

        setupPositionSpinner()
        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            val vUri = selectedVideoUri
            if (vUri == null) {
                Toast.makeText(requireContext(), "الرجاء اختيار ملف للتعديل أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processWatermark(vUri)
        }
    }

    private fun setupPositionSpinner() {
        val positions = listOf(
            AppStrings.get(requireContext(), R.string.string_121), // Top Right
            AppStrings.get(requireContext(), R.string.string_126), // Top Left
            AppStrings.get(requireContext(), R.string.string_119), // Bottom Right
            AppStrings.get(requireContext(), R.string.string_120)  // Bottom Left
        )
        selectedPosition = positions[0]
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, positions)
        binding.spPosition.adapter = adapter
        binding.spPosition.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPosition = positions[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateApplyButtonState() {
        val hasVideo = selectedVideoUri != null
        val hasWatermark = if (isTextMode) textOptions.text.isNotBlank() else selectedImageUri != null
        binding.btnApply.isEnabled = hasVideo && hasWatermark
    }

    private fun processWatermark(vUri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputMedia = FileUtils.getPathFromUri(requireContext(), vUri)
                val isVideo = isSelectedMediaVideo
                val outputExt = if (isVideo) ".mp4" else ".jpg"
                val outputPath = requireContext().cacheDir.absolutePath + "/watermark_${System.currentTimeMillis()}$outputExt"
                
                var inputImage: String? = null
                if (isTextMode) {
                    val textImgPath = requireContext().cacheDir.absolutePath + "/text_wm_${System.currentTimeMillis()}.png"
                    if (TextRenderer.createTickerPng(textOptions, File(textImgPath))) {
                        inputImage = textImgPath
                    }
                } else {
                    val wUri = selectedImageUri
                    if (wUri != null) {
                        inputImage = FileUtils.getPathFromUri(requireContext(), wUri)
                    }
                }
                
                if (inputMedia != null && inputImage != null) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_74))
                    }
                    
                    val overlayStr = when (selectedPosition) {
                        AppStrings.get(requireContext(), R.string.string_126) -> "10:10" // Top Left
                        AppStrings.get(requireContext(), R.string.string_121) -> "W-w-10:10" // Top Right
                        AppStrings.get(requireContext(), R.string.string_120) -> "10:H-h-10" // Bottom Left
                        AppStrings.get(requireContext(), R.string.string_119) -> "W-w-10:H-h-10" // Bottom Right
                        else -> "10:10"
                    }
                    
                    val commandArgs = if (isVideo) {
                        arrayOf(
                            "-y", "-i", inputMedia, "-i", inputImage, 
                            "-filter_complex", "[0:v][1:v]overlay=$overlayStr", 
                            "-c:v", "mpeg4", "-q:v", "2", "-c:a", "copy", outputPath
                        )
                    } else {
                        arrayOf(
                            "-y", "-i", inputMedia, "-i", inputImage, 
                            "-filter_complex", "[0:v][1:v]overlay=$overlayStr", 
                            outputPath
                        )
                    }
                    
                    val success = FFmpegProcessor.executeWithProgress(commandArgs, inputMedia)
                    if (success) {
                        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                        FileUtils.saveToGallery(requireContext(), File(outputPath), mimeType)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to prepare watermark", Toast.LENGTH_SHORT).show()
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

