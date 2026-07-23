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
        val safeContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            try {
                withContext(Dispatchers.Main) {
                    val currentContext = context ?: return@withContext
                    ProcessingManager.startProcessing(AppStrings.get(currentContext, R.string.string_127))
                }
                
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(safeContext.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(safeContext.contentResolver, uri)
                }
                
                val resultBitmap = TextRenderer.drawTextOnImage(bitmap, textOptions)
                val outputPath = safeContext.cacheDir.absolutePath + "/edited_image_${System.currentTimeMillis()}.jpg"
                val out = FileOutputStream(outputPath)
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
                
                val savedUri = FileUtils.saveToGallery(safeContext, File(outputPath), "image/jpeg")
                if (savedUri != null) success = true
                
                withContext(Dispatchers.Main) {
                    val currentContext = context ?: return@withContext
                    if (success) {
                        Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_182), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_183), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val currentContext = context ?: return@withContext
                    Toast.makeText(currentContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
