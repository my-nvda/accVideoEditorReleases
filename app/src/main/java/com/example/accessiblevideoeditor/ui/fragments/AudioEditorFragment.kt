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
import com.example.accessiblevideoeditor.databinding.FragmentAudioEditorBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioEditorFragment : Fragment() {

    private var _binding: FragmentAudioEditorBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var selectedAudioUri: Uri? = null

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.string_70)
        }
        updateButtonStates()
    }

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedAudioUri = uri
        if (uri != null) {
            binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_85)
        }
        updateButtonStates()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioEditorBinding.inflate(inflater, container, false)
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
        
        binding.btnSelectAudio.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }

        updateButtonStates()

        binding.btnRemoveAudio.setOnClickListener {
            val videoUri = selectedVideoUri ?: return@setOnClickListener
            removeAudio(videoUri)
        }
        
        binding.btnReplaceAudio.setOnClickListener {
            val videoUri = selectedVideoUri ?: return@setOnClickListener
            val audioUri = selectedAudioUri ?: return@setOnClickListener
            replaceAudio(videoUri, audioUri)
        }
        
        binding.btnMixAudio.setOnClickListener {
            val videoUri = selectedVideoUri ?: return@setOnClickListener
            val audioUri = selectedAudioUri ?: return@setOnClickListener
            mixAudio(videoUri, audioUri)
        }
    }
    
    private fun updateButtonStates() {
        val hasVideo = selectedVideoUri != null
        val hasAudio = selectedAudioUri != null
        
        binding.btnRemoveAudio.isEnabled = hasVideo
        binding.btnReplaceAudio.isEnabled = hasVideo && hasAudio
        binding.btnMixAudio.isEnabled = hasVideo && hasAudio
    }

    private fun removeAudio(videoUri: Uri) {
        SoundManager.playProcessing()
        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_25))
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempVideo = MediaUtils.copyUriToTempFile(
                    requireContext(), videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4"
                )
                if (tempVideo != null) {
                    val outputFile = File(requireContext().cacheDir, "output_no_audio_${System.currentTimeMillis()}.mp4")
                    val success = FFmpegProcessor.removeAudio(
                        sourceVideo = tempVideo.absolutePath,
                        outputPath = outputFile.absolutePath
                    )
                    if (success) {
                        MediaUtils.saveVideoToGallery(
                            requireContext(), outputFile, "AccessibleEditor_NoAudio_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_73, e.message ?: ""), Toast.LENGTH_LONG).show() }
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }
    
    private fun replaceAudio(videoUri: Uri, audioUri: Uri) {
        SoundManager.playProcessing()
        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_7))
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempVideo = MediaUtils.copyUriToTempFile(requireContext(), videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4")
                val tempAudio = MediaUtils.copyUriToTempFile(requireContext(), audioUri, "temp_audio_only_${System.currentTimeMillis()}.mp3")
                
                if (tempVideo != null && tempAudio != null) {
                    val outputFile = File(requireContext().cacheDir, "output_merged_audio_${System.currentTimeMillis()}.mp4")
                    val success = FFmpegProcessor.replaceAudio(
                        sourceVideo = tempVideo.absolutePath,
                        newAudio = tempAudio.absolutePath,
                        outputPath = outputFile.absolutePath
                    )
                    if (success) {
                        MediaUtils.saveVideoToGallery(
                            requireContext(), outputFile, "AccessibleEditor_MergedAudio_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                    } else {
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_73, e.message ?: ""), Toast.LENGTH_LONG).show() }
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }
    
    private fun mixAudio(videoUri: Uri, audioUri: Uri) {
        SoundManager.playProcessing()
        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_9))
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempVideo = MediaUtils.copyUriToTempFile(requireContext(), videoUri, "temp_video_audio_${System.currentTimeMillis()}.mp4")
                val tempAudio = MediaUtils.copyUriToTempFile(requireContext(), audioUri, "temp_audio_only_${System.currentTimeMillis()}.mp3")
                
                if (tempVideo != null && tempAudio != null) {
                    val outputFile = File(requireContext().cacheDir, "output_mixed_audio_${System.currentTimeMillis()}.mp4")
                    val success = FFmpegProcessor.mixAudio(
                        sourceVideo = tempVideo.absolutePath,
                        newAudio = tempAudio.absolutePath,
                        outputPath = outputFile.absolutePath
                    )
                    if (success) {
                        MediaUtils.saveVideoToGallery(
                            requireContext(), outputFile, "AccessibleEditor_MixedAudio_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                    } else {
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_73, e.message ?: ""), Toast.LENGTH_LONG).show() }
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

