package com.example.accessiblevideoeditor.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentAudioStudioBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class AudioStudioFragment : Fragment() {

    private var _binding: FragmentAudioStudioBinding? = null
    private val binding get() = _binding!!

    private var selectedMediaUri: Uri? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isRecordingPaused = false
    private var recordingTime = 0L // in seconds
    private var activeRecordingPath: String? = null

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isRecordingPaused) {
                recordingTime++
                updateTimerUI()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedMediaUri = uri
        if (uri != null) {
            binding.btnSelectMedia.text = AppStrings.get(requireContext(), R.string.string_88)
        }
        updateActionButtons()
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startRecordingAudio()
        } else {
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioStudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Apply dynamic translations and set explicit content descriptions for accessibility
        binding.rbMono.text = AppStrings.get(requireContext(), R.string.string_rec_mono)
        binding.rbStereo.text = AppStrings.get(requireContext(), R.string.string_rec_stereo)
        binding.rgChannels.contentDescription = AppStrings.get(requireContext(), R.string.string_rec_channels)
        
        binding.rbM4A.text = AppStrings.get(requireContext(), R.string.string_format_m4a)
        binding.rbWAV.text = AppStrings.get(requireContext(), R.string.string_format_wav)
        binding.rbMP3.text = AppStrings.get(requireContext(), R.string.string_format_mp3)
        binding.rgFormat.contentDescription = AppStrings.get(requireContext(), R.string.string_rec_format)

        binding.btnStartRecording.text = AppStrings.get(requireContext(), R.string.string_rec_start)
        binding.btnCancelRecording.text = AppStrings.get(requireContext(), R.string.string_rec_cancel)
        binding.btnStopRecording.text = AppStrings.get(requireContext(), R.string.string_rec_stop)
        binding.btnPauseResume.text = AppStrings.get(requireContext(), R.string.string_rec_pause)

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnExtractAudio.setOnClickListener {
            val uri = selectedMediaUri ?: return@setOnClickListener
            extractAudio(uri)
        }

        binding.btnBassBoost.setOnClickListener {
            val uri = selectedMediaUri ?: return@setOnClickListener
            applyBassBoost(uri)
        }
        
        updateActionButtons()

        binding.btnStartRecording.setOnClickListener {
            val permissionCheck = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                startRecordingAudio()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        
        binding.btnPauseResume.setOnClickListener {
            if (isRecordingPaused) resumeRecordingAudio() else pauseRecordingAudio()
        }
        
        binding.btnStopRecording.setOnClickListener {
            stopRecordingAudio()
        }
        
        binding.btnCancelRecording.setOnClickListener {
            cancelRecordingAudio()
        }
    }

    private fun updateActionButtons() {
        val hasMedia = selectedMediaUri != null
        binding.btnExtractAudio.isEnabled = hasMedia
        binding.btnBassBoost.isEnabled = hasMedia
    }
    
    private fun updateRecordingUI() {
        if (isRecording) {
            binding.llConfigState.visibility = View.GONE
            binding.llRecordingState.visibility = View.VISIBLE
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.text = if (isRecordingPaused) {
                    AppStrings.get(requireContext(), R.string.string_rec_resume)
                } else {
                    AppStrings.get(requireContext(), R.string.string_rec_pause)
                }
            } else {
                binding.btnPauseResume.visibility = View.GONE
            }
            updateTimerUI()
        } else {
            binding.llConfigState.visibility = View.VISIBLE
            binding.llRecordingState.visibility = View.GONE
        }
    }

    private fun updateTimerUI() {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        
        val statusText = if (isRecordingPaused) {
            AppStrings.get(requireContext(), R.string.string_rec_paused_status, formattedTime)
        } else {
            AppStrings.get(requireContext(), R.string.string_rec_recording_status, formattedTime)
        }
        
        binding.tvRecordingStatus.text = statusText
    }

    private fun startRecordingAudio() {
        try {
            val isStereo = binding.rbStereo.isChecked
            val audioChannels = if (isStereo) 2 else 1
            val tempFile = File(requireContext().cacheDir, "recording_temp_${System.currentTimeMillis()}.m4a")
            activeRecordingPath = tempFile.absolutePath

            val recorderInstance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder = recorderInstance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(audioChannels)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            isRecordingPaused = false
            recordingTime = 0L
            SoundManager.playSuccess()
            updateRecordingUI()
            timerHandler.post(timerRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_error_start_record, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseRecordingAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                isRecordingPaused = true
                updateRecordingUI()
                timerHandler.removeCallbacks(timerRunnable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecordingAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                isRecordingPaused = false
                updateRecordingUI()
                timerHandler.post(timerRunnable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecordingAudio() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            isRecordingPaused = false
            timerHandler.removeCallbacks(timerRunnable)
            updateRecordingUI()

            val inputPath = activeRecordingPath
            if (inputPath != null) {
                processRecordedAudio(inputPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_error_stop_record), Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelRecordingAudio() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        isRecordingPaused = false
        recordingTime = 0L
        timerHandler.removeCallbacks(timerRunnable)
        updateRecordingUI()
        
        activeRecordingPath?.let { File(it).delete() }
        activeRecordingPath = null
        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_record_cancelled), Toast.LENGTH_SHORT).show()
    }
    
    private fun processRecordedAudio(inputPath: String) {
        val outputFormat = when {
            binding.rbWAV.isChecked -> "wav"
            binding.rbMP3.isChecked -> "mp3"
            else -> "m4a"
        }
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_57))
                }

                val finalFileName = "Recorded_Audio_${System.currentTimeMillis()}.$outputFormat"
                val finalOutputPath = File(requireContext().cacheDir, finalFileName).absolutePath

                val success = if (outputFormat == "m4a") {
                    File(inputPath).renameTo(File(finalOutputPath))
                } else {
                    val cmd = if (outputFormat == "wav") {
                        arrayOf("-y", "-i", inputPath, "-c:a", "pcm_s16le", finalOutputPath)
                    } else {
                        arrayOf("-y", "-i", inputPath, "-c:a", "libmp3lame", "-q:a", "2", finalOutputPath)
                    }
                    FFmpegProcessor.executeWithProgress(cmd, inputPath)
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        val mime = when (outputFormat) {
                            "wav" -> "audio/x-wav"
                            "mp3" -> "audio/mpeg"
                            else -> "audio/mp4"
                        }
                        val savedUri = FileUtils.saveToGallery(requireContext(), File(finalOutputPath), mime)
                        if (savedUri != null) {
                            SoundManager.playSuccess()
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                requireContext(),
                                savedUri,
                                AppStrings.get(requireContext(), R.string.string_182),
                                mime
                            )
                            selectedMediaUri = savedUri
                            updateActionButtons()
                        }
                    } else {
                        SoundManager.playError()
                        Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_SHORT).show()
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

    private fun extractAudio(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_15))
                }
                val inputPath = FileUtils.getPathFromUri(requireContext(), uri)
                val outputPath = requireContext().cacheDir.absolutePath + "/extracted_audio_${System.currentTimeMillis()}.mp3"
                if (inputPath != null) {
                    val success = FFmpegProcessor.extractAudio(inputPath, outputPath, "mp3")
                    withContext(Dispatchers.Main) {
                        if (success) {
                            val savedUri = FileUtils.saveToGallery(requireContext(), File(outputPath), "audio/mpeg")
                            if (savedUri != null) {
                                SoundManager.playSuccess()
                                com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                    requireContext(),
                                    savedUri,
                                    AppStrings.get(requireContext(), R.string.string_182),
                                    "audio/mpeg"
                                )
                            } else {
                                SoundManager.playError()
                                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            SoundManager.playError()
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
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

    private fun applyBassBoost(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_39))
                }
                val inputPath = FileUtils.getPathFromUri(requireContext(), uri)
                val outputPath = requireContext().cacheDir.absolutePath + "/bass_boosted_${System.currentTimeMillis()}.mp3"
                if (inputPath != null) {
                    val success = FFmpegProcessor.applyAudioStudioEffects(inputPath, "bass_boost", outputPath)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            val savedUri = FileUtils.saveToGallery(requireContext(), File(outputPath), "audio/mpeg")
                            if (savedUri != null) {
                                SoundManager.playSuccess()
                                com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                    requireContext(),
                                    savedUri,
                                    AppStrings.get(requireContext(), R.string.string_182),
                                    "audio/mpeg"
                                )
                            } else {
                                SoundManager.playError()
                                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
                            }
                        } else {
                            SoundManager.playError()
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_183), Toast.LENGTH_LONG).show()
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
        timerHandler.removeCallbacks(timerRunnable)
        _binding = null
    }
}

