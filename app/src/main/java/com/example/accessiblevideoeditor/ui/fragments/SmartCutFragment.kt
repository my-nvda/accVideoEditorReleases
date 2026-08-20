package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentSmartCutBinding
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SmartCutProcessor
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class SmartCutFragment : Fragment() {

    private var _binding: FragmentSmartCutBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.string_70)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmartCutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val silenceThreshold = binding.etSilenceThreshold.text.toString().toIntOrNull() ?: -30
            val minSilenceDuration = binding.etMinSilenceDuration.text.toString().toFloatOrNull() ?: 0.5f
            
            processVideo(uri, silenceThreshold, minSilenceDuration)
        }
    }

    private fun processVideo(uri: Uri, silenceThreshold: Int, minSilenceDuration: Float) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempFile = MediaUtils.copyUriToTempFile(appContext, uri, "temp_video_${System.currentTimeMillis()}.mp4")
                val input = tempFile?.absolutePath
                if (input != null) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(appContext, R.string.string_42))
                    }
                    val outputPath = appContext.cacheDir.absolutePath + "/smartcut_${System.currentTimeMillis()}.mp4"
                    
                    val success = SmartCutProcessor.removeSilence(
                        context = appContext,
                        inputPath = input,
                        outputPath = outputPath,
                        thresholdDb = silenceThreshold,
                        durationSec = minSilenceDuration
                    )
                    
                    if (success) {
                        FileUtils.saveToGallery(appContext, File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(appContext, AppStrings.get(appContext, R.string.string_240), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(appContext, AppStrings.get(appContext, R.string.string_241), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
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

