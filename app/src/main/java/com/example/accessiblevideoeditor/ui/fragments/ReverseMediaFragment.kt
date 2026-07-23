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
import com.example.accessiblevideoeditor.databinding.FragmentReverseMediaBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class ReverseMediaFragment : Fragment() {

    private var _binding: FragmentReverseMediaBinding? = null
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
        _binding = FragmentReverseMediaBinding.inflate(inflater, container, false)
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
                Toast.makeText(requireContext(), "Please select a video first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val reverseVideo = binding.cbReverseVideo.isChecked
            val reverseAudio = binding.cbReverseAudio.isChecked
            
            processVideo(uri, reverseVideo, reverseAudio)
        }
    }

    private fun processVideo(uri: Uri, reverseVideo: Boolean, reverseAudio: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val input = FileUtils.getPathFromUri(requireContext(), uri)
                if (input != null) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_68))
                    }
                    val outputPath = requireContext().cacheDir.absolutePath + "/reverse_${System.currentTimeMillis()}.mp4"
                    
                    val commandArgs = mutableListOf("-y", "-i", input)
                    if (reverseVideo) {
                        commandArgs.add("-vf")
                        commandArgs.add("reverse")
                    }
                    if (reverseAudio) {
                        commandArgs.add("-af")
                        commandArgs.add("areverse")
                    }
                    commandArgs.add(outputPath)
                    
                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), input)
                    
                    if (success) {
                        FileUtils.saveToGallery(requireContext(), File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_222), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_223), Toast.LENGTH_LONG).show()
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

