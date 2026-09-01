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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentVideoTrimmerBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoTrimmerFragment : Fragment() {

    private var _binding: FragmentVideoTrimmerBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var exoPlayer: ExoPlayer? = null

    // Range List & Mode
    private val rangeList = mutableListOf<TimeRange>()
    private var trimMode = 0 // 0 = Keep, 1 = Delete
    private var playbackCheckJob: Job? = null
    private var combinedPlayRanges = listOf<TimeRange>()
    private var playRangeIndex = 0

    data class TimeRange(val startMs: Long, val endMs: Long)

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            rangeList.clear()
            updateRangesListUI()
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
        binding.playerView.controllerShowTimeoutMs = 0
        binding.playerView.showController()

        // Setup operation mode spinner
        val modeOptions = listOf(
            getString(R.string.trim_mode_keep),
            getString(R.string.trim_mode_delete)
        )
        val modeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modeOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerTrimMode.adapter = modeAdapter
        binding.spinnerTrimMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                trimMode = position
                updateRangesListUI()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnSetStartTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etStartTime.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetEndTime.setOnClickListener {
            val player = exoPlayer
            if (player != null && selectedVideoUri != null) {
                binding.etDuration.setText(formatTime(player.currentPosition))
            } else {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddRange.setOnClickListener {
            if (selectedVideoUri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val startStr = binding.etStartTime.text.toString()
            val endStr = binding.etDuration.text.toString()
            if (startStr.isBlank() || endStr.isBlank()) {
                Toast.makeText(requireContext(), "الرجاء تحديد وقت البداية والنهاية أولاً!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val startMs = parseTimeToMs(startStr)
            val endMs = parseTimeToMs(endStr)

            if (startMs >= endMs) {
                Toast.makeText(requireContext(), "وقت النهاية يجب أن يكون أكبر من البداية!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newRange = TimeRange(startMs, endMs)
            rangeList.add(newRange)
            updateRangesListUI()
            
            // Clear inputs
            binding.etStartTime.setText("")
            binding.etDuration.setText("")
            
            val announceMsg = "تمت إضافة النطاق من ${formatTime(startMs)} إلى ${formatTime(endMs)}"
            view.announceForAccessibility(announceMsg)
        }

        binding.btnPreviewCombined.setOnClickListener {
            val player = exoPlayer ?: return@setOnClickListener
            if (selectedVideoUri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPreviewPlayback()
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rangeList.isEmpty()) {
                Toast.makeText(requireContext(), "الرجاء إضافة نطاق تقطيع واحد على الأقل!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            com.example.accessiblevideoeditor.ui.ExportQualityDialogHelper.showQualityDialog(requireContext()) {
                processVideoMulti(uri)
            }
        }

        // Bind Start Time Nudge buttons
        binding.btnStartMinus1s.setOnClickListener { nudgeTime(isStart = true, offsetMs = -1000L) }
        binding.btnStartMinusPoint1s.setOnClickListener { nudgeTime(isStart = true, offsetMs = -100L) }
        binding.btnStartPlusPoint1s.setOnClickListener { nudgeTime(isStart = true, offsetMs = 100L) }
        binding.btnStartPlus1s.setOnClickListener { nudgeTime(isStart = true, offsetMs = 1000L) }

        // Bind End Time Nudge buttons
        binding.btnEndMinus1s.setOnClickListener { nudgeTime(isStart = false, offsetMs = -1000L) }
        binding.btnEndMinusPoint1s.setOnClickListener { nudgeTime(isStart = false, offsetMs = -100L) }
        binding.btnEndPlusPoint1s.setOnClickListener { nudgeTime(isStart = false, offsetMs = 100L) }
        binding.btnEndPlus1s.setOnClickListener { nudgeTime(isStart = false, offsetMs = 1000L) }
    }

    private fun getKeepRanges(totalDurationMs: Long): List<TimeRange> {
        if (trimMode == 0) {
            // Keep Mode
            return rangeList.sortedBy { it.startMs }
        } else {
            // Delete Mode: convert excluded ranges to keep ranges
            val sortedDeletes = rangeList.sortedBy { it.startMs }
            val keeps = mutableListOf<TimeRange>()
            var currentStart = 0L
            for (del in sortedDeletes) {
                if (del.startMs > currentStart) {
                    keeps.add(TimeRange(currentStart, del.startMs))
                }
                if (del.endMs > currentStart) {
                    currentStart = del.endMs
                }
            }
            if (currentStart < totalDurationMs) {
                keeps.add(TimeRange(currentStart, totalDurationMs))
            }
            return keeps
        }
    }

    private fun startPreviewPlayback() {
        playbackCheckJob?.cancel()
        val player = exoPlayer ?: return
        val totalDur = player.duration
        val targetDuration = if (totalDur > 0) totalDur else 1000000L
        val keeps = getKeepRanges(targetDuration)
        
        if (keeps.isEmpty()) {
            Toast.makeText(requireContext(), "لا توجد نطاقات صالحة للتشغيل!", Toast.LENGTH_SHORT).show()
            return
        }

        combinedPlayRanges = keeps
        playRangeIndex = 0

        player.seekTo(combinedPlayRanges[0].startMs)
        player.play()

        playbackCheckJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(50)
                val currentPos = player.currentPosition
                val currentRange = combinedPlayRanges.getOrNull(playRangeIndex)
                if (currentRange != null) {
                    if (currentPos < currentRange.startMs) {
                        player.seekTo(currentRange.startMs)
                    } else if (currentPos > currentRange.endMs) {
                        playRangeIndex++
                        if (playRangeIndex < combinedPlayRanges.size) {
                            player.seekTo(combinedPlayRanges[playRangeIndex].startMs)
                        } else {
                            player.pause()
                            playRangeIndex = 0
                            player.seekTo(combinedPlayRanges[0].startMs)
                            view?.announceForAccessibility("انتهى استعراض مقاطع الفيديو المدمجة")
                            break
                        }
                    }
                } else {
                    player.pause()
                    break
                }
            }
        }
    }

    private fun updateRangesListUI() {
        val context = context ?: return
        if (rangeList.isNotEmpty()) {
            binding.tvRangesHeader.visibility = View.VISIBLE
            binding.layoutRangesList.visibility = View.VISIBLE
        } else {
            binding.tvRangesHeader.visibility = View.GONE
            binding.layoutRangesList.visibility = View.GONE
        }

        binding.layoutRangesList.removeAllViews()

        rangeList.forEachIndexed { index, range ->
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val rangeStr = "[${formatTime(range.startMs)} - ${formatTime(range.endMs)}]"
            val tvInfo = android.widget.TextView(context).apply {
                text = "النطاق ${index + 1}: $rangeStr"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 14f
                contentDescription = "النطاق رقم ${index + 1} من ${formatTime(range.startMs)} إلى ${formatTime(range.endMs)}"
            }
            row.addView(tvInfo)

            // Play segment button
            val btnPlaySeg = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "▶️"
                contentDescription = "تشغيل النطاق رقم ${index + 1}"
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    val player = exoPlayer ?: return@setOnClickListener
                    playbackCheckJob?.cancel()
                    player.seekTo(range.startMs)
                    player.play()
                    playbackCheckJob = viewLifecycleOwner.lifecycleScope.launch {
                        while (true) {
                            delay(50)
                            val activePlayer = exoPlayer ?: break
                            val currentPos = activePlayer.currentPosition
                            if (currentPos >= range.endMs) {
                                activePlayer.pause()
                                activePlayer.seekTo(range.startMs)
                                break
                            }
                        }
                    }
                }
            }
            row.addView(btnPlaySeg)

            // Remove button
            val btnRemove = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "✕"
                contentDescription = "حذف النطاق رقم ${index + 1}"
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    rangeList.removeAt(index)
                    updateRangesListUI()
                    view?.announceForAccessibility("تم حذف النطاق رقم ${index + 1}")
                }
            }
            row.addView(btnRemove)

            binding.layoutRangesList.addView(row)
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
    
    private fun normalizeDigits(input: String): String {
        var output = input
        val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        for (i in 0..9) {
            output = output.replace(arabic[i], ('0' + i))
            output = output.replace(persian[i], ('0' + i))
        }
        return output
    }

    private fun parseTimeToMs(timeStr: String): Long {
        val cleanStr = normalizeDigits(timeStr.trim())
        if (cleanStr.isBlank()) return 0L
        try {
            val dotParts = cleanStr.split(".")
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

    private fun nudgeTime(isStart: Boolean, offsetMs: Long) {
        val player = exoPlayer ?: return
        if (selectedVideoUri == null) {
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
            return
        }

        val etField = if (isStart) binding.etStartTime else binding.etDuration
        val currentStr = etField.text.toString()
        val currentMs = if (currentStr.isBlank()) {
            if (isStart) 0L else player.duration
        } else {
            parseTimeToMs(currentStr)
        }

        var newTimeMs = currentMs + offsetMs
        val videoDuration = player.duration
        if (newTimeMs < 0) {
            newTimeMs = 0
        }
        if (videoDuration > 0 && newTimeMs > videoDuration) {
            newTimeMs = videoDuration
        }

        etField.setText(formatTime(newTimeMs))

        // Play 1-second preview from newTimeMs
        playbackCheckJob?.cancel()
        player.seekTo(newTimeMs)
        player.play()
        playbackCheckJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(1000)
            player.pause()
            player.seekTo(newTimeMs)
        }

        val announceStr = if (isStart) {
            "بداية القص: ${formatTime(newTimeMs)}"
        } else {
            "نهاية القص: ${formatTime(newTimeMs)}"
        }
        view?.announceForAccessibility(announceStr)
    }

    private fun processVideoMulti(uri: Uri) {
        val ctx = context ?: return
        SoundManager.playProcessing()
        val trimMsg = AppStrings.get(ctx, R.string.string_46).replace(" %1\$s%%", "")
        ProcessingManager.startProcessing(trimMsg)
        
        // Always query ExoPlayer properties on the Main thread!
        val playerDur = exoPlayer?.duration ?: 0L
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Copy video to temp file using captured context
                val tempVideo = MediaUtils.copyUriToTempFile(
                    ctx, uri, "temp_trim_in_${System.currentTimeMillis()}.mp4"
                )
                
                if (tempVideo != null) {
                    val totalDurationMs = if (playerDur > 0) playerDur else FFmpegProcessor.getMediaDurationMs(tempVideo.absolutePath).toLong()
                    val targetDurationMs = if (totalDurationMs > 0) totalDurationMs else 1000000L
                    val keeps = getKeepRanges(targetDurationMs)

                    if (keeps.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "لا توجد نطاقات صالحة للتصدير!", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val outputFile = File(ctx.cacheDir, "output_trim_${System.currentTimeMillis()}.mp4")
                    
                    val hasAudio = FFmpegProcessor.hasAudioTrack(tempVideo.absolutePath)
                    val success = if (keeps.size == 1) {
                        // Standard single trim
                        val startSec = keeps[0].startMs / 1000.0
                        val durSec = (keeps[0].endMs - keeps[0].startMs) / 1000.0
                        FFmpegProcessor.trimVideo(
                            sourceVideo = tempVideo.absolutePath,
                            startTimeInSeconds = String.format(java.util.Locale.US, "%.3f", startSec),
                            durationInSeconds = String.format(java.util.Locale.US, "%.3f", durSec),
                            outputPath = outputFile.absolutePath
                        )
                    } else {
                        // Multi-segment trim and concat using filter_complex
                        val filterComplex = StringBuilder()
                        val concatParts = StringBuilder()
                        keeps.forEachIndexed { i, keep ->
                            val startSec = keep.startMs / 1000.0
                            val endSec = keep.endMs / 1000.0
                            filterComplex.append("[0:v]trim=start=$startSec:end=$endSec,setpts=PTS-STARTPTS[v$i];")
                            if (hasAudio) {
                                filterComplex.append("[0:a]atrim=start=$startSec:end=$endSec,asetpts=PTS-STARTPTS[a$i];")
                                concatParts.append("[v$i][a$i]")
                            } else {
                                concatParts.append("[v$i]")
                            }
                        }
                        if (hasAudio) {
                            filterComplex.append("${concatParts.toString()}concat=n=${keeps.size}:v=1:a=1[outv][outa]")
                        } else {
                            filterComplex.append("${concatParts.toString()}concat=n=${keeps.size}:v=1:a=0[outv]")
                        }

                        val commandArgs = mutableListOf<String>()
                        commandArgs.addAll(listOf("-y", "-i", tempVideo.absolutePath))
                        commandArgs.addAll(listOf("-filter_complex", filterComplex.toString()))
                        commandArgs.addAll(listOf("-map", "[outv]"))
                        if (hasAudio) {
                            commandArgs.addAll(listOf("-map", "[outa]"))
                        }
                        commandArgs.addAll(
                            listOf(
                                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "23"
                            )
                        )
                        if (hasAudio) {
                            commandArgs.addAll(listOf("-c:a", "aac", "-b:a", "128k"))
                        }
                        commandArgs.add(outputFile.absolutePath)
                        
                        FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = totalDurationMs.toFloat())
                    }
                    
                    // Save to Gallery
                    if (success) {
                        val savedUri = MediaUtils.saveVideoToGallery(
                            ctx,
                            outputFile,
                            "AccessibleEditor_Trim_${System.currentTimeMillis()}.mp4"
                        )
                        SoundManager.playSuccess()
                        withContext(Dispatchers.Main) {
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                ctx,
                                savedUri,
                                "تم قص وتصدير الفيديو بنجاح وحفظه في الاستوديو!",
                                "video/mp4"
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, AppStrings.get(ctx, R.string.string_183), Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, AppStrings.get(ctx, R.string.string_183), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, AppStrings.get(ctx, R.string.string_73, e.message ?: ""), Toast.LENGTH_LONG).show()
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
            rangeList.clear()
            updateRangesListUI()
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        playbackCheckJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}
