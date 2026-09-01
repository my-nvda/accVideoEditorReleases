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
import com.example.accessiblevideoeditor.databinding.FragmentSmartCutBinding
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.SmartCutProcessor
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class SmartCutFragment : Fragment() {

    private var _binding: FragmentSmartCutBinding? = null
    private val binding get() = _binding!!

    private var selectedVideoUri: Uri? = null
    private var exoPlayer: ExoPlayer? = null

    private var currentReport: SmartCutProcessor.SilenceReport? = null
    private var playbackCheckJob: Job? = null
    private var combinedPlayRanges = listOf<TimeRange>()
    private var playRangeIndex = 0

    data class TimeRange(val startMs: Long, val endMs: Long)

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedVideoUri = uri
        if (uri != null) {
            binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.string_70)
            binding.playerCard.visibility = View.VISIBLE
            binding.tvAnalysisReport.visibility = View.GONE
            currentReport = null
            
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
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

        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = exoPlayer
        binding.playerView.controllerShowTimeoutMs = 0
        binding.playerView.showController()

        binding.btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnAnalyze.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val threshold = binding.etSilenceThreshold.text.toString().toIntOrNull() ?: -30
            val minDuration = binding.etMinSilenceDuration.text.toString().toFloatOrNull() ?: 0.5f
            
            runPreAnalysis(uri, threshold, minDuration)
        }

        binding.btnPreview.setOnClickListener {
            if (selectedVideoUri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val report = currentReport
            if (report == null) {
                // Run quick analysis first
                val threshold = binding.etSilenceThreshold.text.toString().toIntOrNull() ?: -30
                val minDuration = binding.etMinSilenceDuration.text.toString().toFloatOrNull() ?: 0.5f
                runPreAnalysis(selectedVideoUri!!, threshold, minDuration) {
                    startPreviewPlayback()
                }
            } else {
                startPreviewPlayback()
            }
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val silenceThreshold = binding.etSilenceThreshold.text.toString().toIntOrNull() ?: -30
            val minSilenceDuration = binding.etMinSilenceDuration.text.toString().toFloatOrNull() ?: 0.5f
            val fastCut = binding.switchFastCut.isChecked
            
            processVideo(uri, silenceThreshold, minSilenceDuration, fastCut)
        }
    }

    private fun runPreAnalysis(
        uri: Uri,
        threshold: Int,
        minDuration: Float,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = requireContext().applicationContext
        ProcessingManager.startProcessing("جاري فحص مقاطع الصمت...")
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempFile = MediaUtils.copyUriToTempFile(appContext, uri, "temp_analyze_${System.currentTimeMillis()}.mp4")
                val input = tempFile?.absolutePath
                if (input != null) {
                    val report = SmartCutProcessor.detectSilenceReport(appContext, input, threshold, minDuration)
                    withContext(Dispatchers.Main) {
                        currentReport = report
                        if (report != null) {
                            val total = String.format(java.util.Locale.US, "%.2f", report.totalDuration)
                            val silVal = String.format(java.util.Locale.US, "%.2f", report.totalSilenceDuration)
                            val savedVal = String.format(java.util.Locale.US, "%.2f", report.timeSaved)
                            val numSegments = report.silenceSegments.size
                            
                            val reportText = "إجمالي مدة الفيديو: $total ثانية\n" +
                                    "تم العثور على $numSegments فترات صمت.\n" +
                                    "المدة الإجمالية للصمت: $silVal ثانية\n" +
                                    "الوقت المتوقع توفيره: $savedVal ثانية"
                            
                            binding.tvAnalysisReport.text = reportText
                            binding.tvAnalysisReport.visibility = View.VISIBLE
                            binding.tvAnalysisReport.requestFocus()
                            view?.announceForAccessibility(reportText)
                            
                            onComplete?.invoke()
                        } else {
                            Toast.makeText(appContext, "فشل فحص الصمت للفيديو المحدد!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                }
            }
        }
    }

    private fun startPreviewPlayback() {
        playbackCheckJob?.cancel()
        val player = exoPlayer ?: return
        val report = currentReport ?: return
        
        val keeps = report.keepSegments.map {
            TimeRange((it.first * 1000).toLong(), (it.second * 1000).toLong())
        }

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
                            view?.announceForAccessibility("انتهى استعراض مقطع الفيديو الخالي من الصمت")
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

    private fun processVideo(uri: Uri, silenceThreshold: Int, minSilenceDuration: Float, fastCut: Boolean) {
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
                        durationSec = minSilenceDuration,
                        fastCut = fastCut
                    )
                    
                    if (success) {
                        val savedUri = FileUtils.saveToGallery(appContext, File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            if (isAdded && context != null) {
                                com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                    requireContext(),
                                    savedUri,
                                    AppStrings.get(requireContext(), R.string.string_240),
                                    "video/mp4"
                                )
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

    override fun onResume() {
        super.onResume()
        ProcessingManager.sharedMediaUri?.let { uri ->
            selectedVideoUri = uri
            ProcessingManager.sharedMediaUri = null
            binding.btnSelectVideo.text = AppStrings.get(requireContext(), R.string.string_70)
            binding.playerCard.visibility = View.VISIBLE
            binding.tvAnalysisReport.visibility = View.GONE
            currentReport = null
            
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
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
