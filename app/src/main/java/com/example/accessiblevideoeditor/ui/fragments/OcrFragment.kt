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
import com.example.accessiblevideoeditor.databinding.FragmentOcrBinding
import com.example.accessiblevideoeditor.media.OcrProcessor
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.launch

class OcrFragment : Fragment() {

    private var _binding: FragmentOcrBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private val ocrProcessor by lazy { OcrProcessor() }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.btnSelectImage.text = AppStrings.get(requireContext(), R.string.string_11)
            binding.btnExtractText.isEnabled = true
            binding.tilExtractedText.visibility = View.GONE
            binding.btnCopyText.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOcrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnExtractText.setOnClickListener {
            extractText()
        }

        binding.btnCopyText.setOnClickListener {
            val text = binding.etExtractedText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Extracted Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_141), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractText() {
        val uri = selectedImageUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_111), cancellable = true)
            ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
            try {
                val extractedText = ocrProcessor.extractTextFromImage(requireContext(), uri)
                binding.etExtractedText.setText(extractedText)
                binding.tilExtractedText.visibility = View.VISIBLE
                binding.btnCopyText.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
