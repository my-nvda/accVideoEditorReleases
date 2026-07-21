package com.example.accessiblevideoeditor.ui.fragments

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentImageEditorBinding
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

class ImageEditorFragment : Fragment() {

    private var _binding: FragmentImageEditorBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var textOptions = TextRenderer.TextOptions(text = "")

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) {
            binding.btnSelectImage.text = AppStrings.get(requireContext(), R.string.string_108)
        }
        updateApplyButtonState()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        TextCustomizationHelper(requireContext(), binding.textPanel) { newOptions ->
            textOptions = newOptions
            updateApplyButtonState()
        }

        updateApplyButtonState()

        binding.btnApply.setOnClickListener {
            val uri = selectedImageUri ?: return@setOnClickListener
            if (textOptions.text.isBlank()) {
                Toast.makeText(requireContext(), "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processImage(uri)
        }
    }
    
    private fun updateApplyButtonState() {
        binding.btnApply.isEnabled = selectedImageUri != null && textOptions.text.isNotBlank()
    }

    private fun processImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_127))
                }
                
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                }
                
                val resultBitmap = TextRenderer.drawTextOnImage(bitmap, textOptions)
                val outputPath = requireContext().cacheDir.absolutePath + "/edited_image_${System.currentTimeMillis()}.jpg"
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
