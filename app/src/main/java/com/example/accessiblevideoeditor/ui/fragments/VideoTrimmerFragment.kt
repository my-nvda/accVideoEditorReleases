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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentVideoTrimmerBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoTrimmerFragment : Fragment() {

    private var _binding: FragmentVideoTrimmerBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var exoPlayer: ExoPlayer? = null

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoTrimmerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = exoPlayer

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnSetStartTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etStartTime.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetEndTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etDuration.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null) {
                Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val startStr = binding.etStartTime.text.toString()
            val endStr = binding.etDuration.text.toString()
            processVideo(uri, startStr, endStr)
        }
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60)) % 24
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
    
    private fun parseTimeToSeconds(timeStr: String): Int {
        if (timeStr.isBlank()) return 0
        val parts = timeStr.split(":")
        return when (parts.size) {
            1 -> parts[0].trim().toIntOrNull() ?: 0
            2 -> (parts[0].trim().toIntOrNull() ?: 0) * 60 + (parts[1].trim().toIntOrNull() ?: 0)
            3 -> (parts[0].trim().toIntOrNull() ?: 0) * 3600 + (parts[1].trim().toIntOrNull() ?: 0) * 60 + (parts[2].trim().toIntOrNull() ?: 0)
            else -> 0
        }
    }

    private fun processVideo(uri: Uri, startStr: String, endStr: String) {
        SoundManager.playProcessing()
        val trimMsg = com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_46).replace(" %1\$s%%", "")
        ProcessingManager.startProcessing(trimMsg)
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Copy video to temp file
                val tempVideo = MediaUtils.copyUriToTempFile(
                    requireContext(), uri, "temp_trim_in_${System.currentTimeMillis()}.mp4"
                )
                
                if (tempVideo != null) {
                    // 2. Process with FFmpeg
                    val outputFile = File(requireContext().cacheDir, "output_trim_${System.currentTimeMillis()}.mp4")
                    
                    val startSecs = parseTimeToSeconds(startStr)
                    val endSecs = parseTimeToSeconds(endStr)
                    val durationSecs = if (endSecs > startSecs) (endSecs - startSecs) else 1
                    
                    val success = FFmpegProcessor.trimVideo(
                        sourceVideo = tempVideo.absolutePath,
                        startTimeInSeconds = startSecs.toString(),
                        durationInSeconds = durationSecs.toString(),
                        outputPath = outputFile.absolutePath
                    )
                    
                    // 3. Save to Gallery
                    if (success) {
                        MediaUtils.saveVideoToGallery(
                            requireContext(),
                            outputFile,
                            "AccessibleEditor_Trim_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_73, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}

