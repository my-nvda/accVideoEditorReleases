package com.example.accessiblevideoeditor.ui.fragments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentCreateBlankImageBinding
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
import java.io.FileOutputStream
import java.util.concurrent.CancellationException

class CreateBlankImageFragment : Fragment() {

    private var _binding: FragmentCreateBlankImageBinding? = null
    private val binding get() = _binding!!

    private var textOptions = TextRenderer.TextOptions(text = "")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBlankImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        TextCustomizationHelper(requireContext(), binding.textPanel) { newOptions ->
            textOptions = newOptions
            updateApplyButtonState()
        }

        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            if (textOptions.text.isBlank()) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_enter_text), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processImage()
        }
    }
    
    private fun updateApplyButtonState() {
        binding.btnApply.isEnabled = textOptions.text.isNotBlank()
    }

    private fun processImage() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_270))
                }
                
                val width = 1080
                val height = 1920
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.BLACK)

                val resultBitmap = TextRenderer.drawTextOnImage(bitmap, textOptions)
                val outputPath = requireContext().cacheDir.absolutePath + "/created_image_${System.currentTimeMillis()}.jpg"
                val out = FileOutputStream(outputPath)
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()

                val savedUri = FileUtils.saveToGallery(requireContext(), File(outputPath), "image/jpeg")
                if (savedUri != null) success = true
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_182), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_73, e.message ?: ""), Toast.LENGTH_SHORT).show()
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

