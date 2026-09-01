package com.example.accessiblevideoeditor.ui.fragments

import com.example.accessiblevideoeditor.ui.AppStrings
import android.app.Activity
import android.content.Intent
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
import com.example.accessiblevideoeditor.databinding.FragmentVideoEditorBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.media.TextRenderer
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.components.TextCustomizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoEditorFragment : Fragment() {

    private var _binding: FragmentVideoEditorBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var exoPlayer: ExoPlayer? = null
    private var textOptions = TextRenderer.TextOptions(text = "")
    private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var previewRunnable: Runnable? = null
    private var animValues: Array<String> = emptyArray()
    private var shapeValues: Array<String> = emptyArray()

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
        _binding = FragmentVideoEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = exoPlayer
        binding.playerView.controllerShowTimeoutMs = 0
        binding.playerView.showController()

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        TextCustomizationHelper(requireContext(), binding.textPanel) { newOptions ->
            textOptions = newOptions
        }

        // Setup Video-Specific Animation & Shape Spinners
        val textAnimsList = com.example.accessiblevideoeditor.ui.CloudConfigManager.getTextAnimations(requireContext())
        val animOptions = textAnimsList.map { it.second }.toTypedArray()
        animValues = textAnimsList.map { it.first }.toTypedArray()
        binding.spAnimType.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, animOptions)
        binding.spAnimType.setSelection(0)

        val shapeMasksList = com.example.accessiblevideoeditor.ui.CloudConfigManager.getShapeMasks(requireContext())
        val shapeOptions = shapeMasksList.map { it.second }.toTypedArray()
        shapeValues = shapeMasksList.map { it.first }.toTypedArray()
        binding.spShapeMask.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, shapeOptions)
        binding.spShapeMask.setSelection(0)

        binding.btnSetStartTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etStartTime.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_only), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetEndTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etEndTime.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_only), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPreviewRange.setOnClickListener {
            val player = exoPlayer
            if (player == null || selectedVideoUri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_only), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val startMs = parseTimeToMs(binding.etStartTime.text.toString())
            val endMs = parseTimeToMs(binding.etEndTime.text.toString())
            
            val duration = player.duration
            val finalEndMs = if (endMs <= startMs) {
                if (duration > 0) duration else (startMs + 5000)
            } else {
                endMs
            }
            
            previewRunnable?.let { previewHandler.removeCallbacks(it) }
            
            player.seekTo(startMs)
            player.play()
            
            val runnable = object : Runnable {
                override fun run() {
                    val p = exoPlayer
                    if (p != null && p.isPlaying) {
                        if (p.currentPosition >= finalEndMs) {
                            p.pause()
                            p.seekTo(startMs)
                            previewRunnable = null
                            return
                        }
                        previewHandler.postDelayed(this, 50)
                    }
                }
            }
            previewRunnable = runnable
            previewHandler.post(runnable)
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedVideoUri ?: return@setOnClickListener
            val startStr = binding.etStartTime.text.toString()
            val endStr = binding.etEndTime.text.toString()
            
            if (textOptions.text.isEmpty()) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_enter_text), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val selectedAnim = animValues[binding.spAnimType.selectedItemPosition]
            val selectedShape = shapeValues[binding.spShapeMask.selectedItemPosition]
            
            com.example.accessiblevideoeditor.ui.ExportQualityDialogHelper.showQualityDialog(requireContext()) {
                processVideo(uri, startStr, endStr, selectedAnim, selectedShape)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val millis = ms % 1000
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60)) % 24
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }
    
    private fun parseTimeToMs(timeStr: String): Long {
        if (timeStr.isBlank()) return 0L
        try {
            val dotParts = timeStr.split(".")
            val baseTime = dotParts[0].trim()
            val msPart = if (dotParts.size > 1) dotParts[1].trim().padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
            
            val parts = baseTime.split(":")
            val baseMs = when (parts.size) {
                1 -> (parts[0].trim().toLongOrNull() ?: 0L) * 1000L
                2 -> ((parts[0].trim().toLongOrNull() ?: 0L) * 60 + (parts[1].trim().toLongOrNull() ?: 0L)) * 1000L
                3 -> ((parts[0].trim().toLongOrNull() ?: 0L) * 3600 + (parts[1].trim().toLongOrNull() ?: 0L) * 60 + (parts[2].trim().toLongOrNull() ?: 0L)) * 1000L
                else -> 0L
            }
            return baseMs + msPart
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun parseTimeToSecondsDouble(timeStr: String): Double {
        return parseTimeToMs(timeStr) / 1000.0
    }

    private fun processVideo(uri: Uri, startStr: String, endStr: String, animationType: String, maskPreset: String) {
        SoundManager.playProcessing()
        val processMsg = com.example.accessiblevideoeditor.ui.AppStrings.get(requireContext(), R.string.string_28).replace(" %1\$s%%", "")
        ProcessingManager.startProcessing(processMsg)
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Copy video to temp file
                val tempVideo = MediaUtils.copyUriToTempFile(
                    requireContext(), uri, "temp_video_${System.currentTimeMillis()}.mp4"
                )
                
                if (tempVideo != null) {
                    // 2. Get Video Dimensions
                    val (width, height) = FFmpegProcessor.getVideoDimensions(tempVideo.absolutePath)
                    
                    // 3. Create Text Overlay PNG
                    val overlayFile = File(requireContext().cacheDir, "overlay_${System.currentTimeMillis()}.png")
                    TextRenderer.createOverlayPng(width, height, textOptions, overlayFile)
                    
                    // 4. Parse Times robustly
                    val startSecs = parseTimeToSecondsDouble(startStr)
                    val endSecs = parseTimeToSecondsDouble(endStr)
                    
                    // 5. Process with FFmpeg
                    val outputFile = File(requireContext().cacheDir, "output_video_${System.currentTimeMillis()}.mp4")
                    
                    val resultLog = FFmpegProcessor.addTextOverlay(
                        sourceVideo = tempVideo.absolutePath,
                        overlayPngPath = overlayFile.absolutePath,
                        startTimeInSeconds = startSecs,
                        endTimeInSeconds = endSecs,
                        outputPath = outputFile.absolutePath,
                        animationType = animationType,
                        maskPreset = maskPreset
                    ) { currentProgress ->
                        ProcessingManager.updateProgress(currentProgress / 100f)
                    }
                    
                    // 6. Save to Gallery
                    if (resultLog == "SUCCESS") {
                        val savedUri = MediaUtils.saveVideoToGallery(
                            requireContext(),
                            outputFile,
                            "AccessibleEditor_Video_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                        withContext(Dispatchers.Main) {
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                requireContext(),
                                savedUri,
                                "تم تعديل الفيديو وحفظه في الاستوديو بنجاح!",
                                "video/mp4"
                            )
                        }
                    } else {
                        ProcessingManager.showError(resultLog)
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

    override fun onResume() {
        super.onResume()
        ProcessingManager.sharedMediaUri?.let { uri ->
            selectedVideoUri = uri
            ProcessingManager.sharedMediaUri = null
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}

