package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentNoiseReductionBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.updater.BeepUtils
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class NoiseReductionFragment : Fragment() {

    private var _binding: FragmentNoiseReductionBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

    // CleanUNet FP16 model constants
    private val cleanUNetFeatureId = "cleanunet_fp16"
    private val cleanUNetDownloadUrl = "https://media.githubusercontent.com/media/my-nvda/accVideoEditorReleases/main/models/cleanunet_fp16.tar.bz2"

    // Mode index constants
    private val MODE_DFN3 = 0
    private val MODE_CLOUD = 1
    private val MODE_DSP = 2
    private val MODE_CLEANUNET = 3

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = AppStrings.get(requireContext(), R.string.string_16)
            startAudioAnalysis(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoiseReductionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val modeLabels = listOf(
            AppStrings.get(requireContext(), R.string.mode_noise_dfn3_offline),
            AppStrings.get(requireContext(), R.string.mode_noise_cloud_online),
            AppStrings.get(requireContext(), R.string.mode_noise_dsp),
            AppStrings.get(requireContext(), R.string.mode_noise_cleanunet)
        )
        val modeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modeLabels)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerNoiseMode.adapter = modeAdapter
        binding.spinnerNoiseMode.setSelection(0) // Default to Local DeepFilterNet3

        // Setup Dolby settings menu item in Toolbar
        val settingsItem = binding.topAppBar.menu.add(0, 101, 0, "إعدادات رمز دولبي")
        settingsItem.setIcon(android.R.drawable.ic_menu_preferences)
        settingsItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
        binding.topAppBar.setOnMenuItemClickListener { item ->
            if (item.itemId == 101) {
                showDolbyTokenDialog()
                true
            } else {
                false
            }
        }

        binding.spinnerNoiseMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == MODE_CLEANUNET) {
                    binding.cvModelDownload.visibility = View.VISIBLE
                    checkCleanUNetModelStatus()
                } else {
                    binding.cvModelDownload.visibility = View.GONE
                }
                
                // Multi-Band is only supported in DSP mode (MODE_DSP)
                if (position == MODE_DSP) {
                    binding.switchMultiBand.isEnabled = true
                } else {
                    binding.switchMultiBand.isEnabled = false
                    binding.switchMultiBand.isChecked = false
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val noiseLabels = listOf(
            AppStrings.get(requireContext(), R.string.noise_mild),
            AppStrings.get(requireContext(), R.string.noise_medium),
            AppStrings.get(requireContext(), R.string.noise_strong)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noiseLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerNoiseLevel.adapter = adapter
        binding.spinnerNoiseLevel.setSelection(1) // default medium

        // Setup Presets Spinner
        val presetLabels = listOf(
            "تخصيص يدوي",
            "بودكاست احترافي (عزل متوسط + لمعان + دفء)",
            "تصوير خارجي ورياح (عزل قوي + تقليل الصدى)",
            "تنقية المحاضرات والدروس (عزل متوسط + وضوح)"
        )
        val presetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presetLabels)
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPresets.adapter = presetAdapter
        binding.spinnerPresets.setSelection(0) // Default: Manual

        binding.spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) return
                when (position) {
                    1 -> { // Podcast
                        binding.spinnerNoiseLevel.setSelection(1) // Medium
                        binding.switchEqBoost.isChecked = true
                        binding.switchHarmonicExciter.isChecked = true
                        binding.switchDeReverb.isChecked = true
                    }
                    2 -> { // Outdoor / Wind
                        binding.spinnerNoiseLevel.setSelection(2) // Strong
                        binding.switchEqBoost.isChecked = false
                        binding.switchHarmonicExciter.isChecked = false
                        binding.switchDeReverb.isChecked = true
                    }
                    3 -> { // Lecture Clarity
                        binding.spinnerNoiseLevel.setSelection(1) // Medium
                        binding.switchEqBoost.isChecked = true
                        binding.switchHarmonicExciter.isChecked = false
                        binding.switchDeReverb.isChecked = false
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val presetResetListener = View.OnClickListener {
            binding.spinnerPresets.setSelection(0)
        }
        binding.switchEqBoost.setOnClickListener(presetResetListener)
        binding.switchHarmonicExciter.setOnClickListener(presetResetListener)
        binding.switchDeReverb.setOnClickListener(presetResetListener)

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnDownloadModel.setOnClickListener {
            promptDownloadModel()
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val modeIndex = binding.spinnerNoiseMode.selectedItemPosition
            val levelIndex = binding.spinnerNoiseLevel.selectedItemPosition
            val isEqBoost = binding.switchEqBoost.isChecked
            val isMultiBand = binding.switchMultiBand.isChecked
            val isHarmonicExciter = binding.switchHarmonicExciter.isChecked
            val isDeReverb = binding.switchDeReverb.isChecked
            // If CleanUNet mode selected, check model exists first
            if (modeIndex == MODE_CLEANUNET) {
                val ctx = requireContext()
                val modelFile = CloudConfigManager.getDownloadedModelFile(ctx, cleanUNetFeatureId)
                if (modelFile == null || !modelFile.exists()) {
                    promptDownloadModel()
                    return@setOnClickListener
                }
            }
            processNoiseReduction(uri, modeIndex, levelIndex, isEqBoost, isMultiBand, isHarmonicExciter, isDeReverb)
        }

        binding.btnPreview.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val modeIndex = binding.spinnerNoiseMode.selectedItemPosition
            val levelIndex = binding.spinnerNoiseLevel.selectedItemPosition
            val isEqBoost = binding.switchEqBoost.isChecked
            val isMultiBand = binding.switchMultiBand.isChecked
            val isHarmonicExciter = binding.switchHarmonicExciter.isChecked
            val isDeReverb = binding.switchDeReverb.isChecked

            if (modeIndex == MODE_CLEANUNET) {
                val ctx = requireContext()
                val modelFile = CloudConfigManager.getDownloadedModelFile(ctx, cleanUNetFeatureId)
                if (modelFile == null || !modelFile.exists()) {
                    promptDownloadModel()
                    return@setOnClickListener
                }
            }
            processQuickPreview(uri, modeIndex, levelIndex, isEqBoost, isMultiBand, isHarmonicExciter, isDeReverb)
        }
    }

    // ----- CleanUNet Model Management -----

    private fun checkCleanUNetModelStatus() {
        val ctx = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(ctx, cleanUNetFeatureId)
        if (modelFile != null && modelFile.exists()) {
            val sizeMb = (modelFile.length() / (1024 * 1024)).toInt()
            binding.tvModelStatus.text = AppStrings.get(ctx, R.string.model_status_cleanunet_loaded, sizeMb)
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = AppStrings.get(ctx, R.string.model_status_cleanunet_not_installed)
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle(AppStrings.get(currentActivity, R.string.dialog_download_title))
            .setMessage(AppStrings.get(currentActivity, R.string.dialog_download_message_cleanunet))
            .setPositiveButton(AppStrings.get(currentActivity, R.string.btn_download_now)) { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
                startDownloadingModel()
            }
            .setNegativeButton(AppStrings.get(currentActivity, R.string.btn_later)) { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
            }
            .show()
    }

    private fun startDownloadingModel() {
        val ctx = context ?: return
        binding.btnDownloadModel.visibility = View.GONE
        binding.pbModelDownload.visibility = View.VISIBLE
        binding.pbModelDownload.progress = 0
        binding.tvModelStatus.text = AppStrings.get(ctx, R.string.msg_download_starting)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val dynamicUrl = com.example.accessiblevideoeditor.ui.CloudConfigManager.getAiModelDownloadInfo(ctx, cleanUNetFeatureId).first
            val success = CloudConfigManager.downloadFeatureModel(
                ctx.applicationContext,
                cleanUNetFeatureId,
                dynamicUrl
            ) { percent ->
                if (_binding != null) {
                    binding.pbModelDownload.progress = percent
                    binding.tvModelStatus.text = AppStrings.get(ctx, R.string.msg_download_progress, percent)
                    if (percent % 5 == 0) {
                        try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                    }
                }
            }
            if (_binding != null) {
                if (success) {
                    try { Toast.makeText(ctx, AppStrings.get(ctx, R.string.msg_download_success), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                } else {
                    try { Toast.makeText(ctx, AppStrings.get(ctx, R.string.msg_download_failed), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                }
                checkCleanUNetModelStatus()
            }
        }
    }

    private fun processNoiseReduction(uri: Uri, modeIndex: Int, levelIndex: Int, isEqBoost: Boolean, isMultiBand: Boolean, isHarmonicExciter: Boolean, isDeReverb: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = MediaUtils.copyUriToTempFile(requireContext(), uri, "noise_input_${System.currentTimeMillis()}")
                if (tempInput != null && tempInput.exists()) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.title_noise_reduction), cancellable = true)
                        ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                    }
                    val isVideo = MediaUtils.isVideoFile(requireContext(), uri)
                    val ext = if (isVideo) "mp4" else "mp3"
                    val outputPath = requireContext().cacheDir.absolutePath + "/noise_clean_${System.currentTimeMillis()}.$ext"

                    var success = false

                    when (modeIndex) {
                        MODE_DFN3 -> {
                            // Local DeepFilterNet3 AI (Offline)
                            success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath, isEqBoost, isHarmonicExciter, isDeReverb)
                        }
                        MODE_CLOUD -> {
                            // Cloud DeepFilterNet2 AI (Online) with fallback to Local DeepFilterNet3
                            success = processCloudDeepFilterNet2(tempInput, isVideo, levelIndex, outputPath, isDeReverb)
                            if (!success) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.msg_noise_cloud_fallback), Toast.LENGTH_SHORT).show()
                                }
                                success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath, isEqBoost, isHarmonicExciter, isDeReverb)
                            }
                        }
                        MODE_DSP -> {
                            // Optimized DSP Filter (Full Frequency Range, No Lowpass/Highpass Cutoff)
                            success = processOptimizedDSP(tempInput, isVideo, levelIndex, outputPath, isEqBoost, isMultiBand, isHarmonicExciter, isDeReverb)
                        }
                        MODE_CLEANUNET -> {
                            // CleanUNet FP16 - Studio Quality AI Denoising
                            val modelFile = CloudConfigManager.getDownloadedModelFile(requireContext(), cleanUNetFeatureId)
                            var cleanUnetSuccess = false
                            if (modelFile != null && modelFile.exists()) {
                                try {
                                    cleanUnetSuccess = processCleanUNet(tempInput, isVideo, levelIndex, outputPath, modelFile, isEqBoost, isHarmonicExciter, isDeReverb)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            if (cleanUnetSuccess) {
                                success = true
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "نموذج CleanUNet غير متوافق أو غير مثبت، تم الانتقال تلقائياً إلى DeepFilterNet3", Toast.LENGTH_LONG).show()
                                }
                                success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath, isEqBoost, isHarmonicExciter, isDeReverb)
                            }
                        }
                        else -> {
                            success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath, isEqBoost, isHarmonicExciter, isDeReverb)
                        }
                    }

                    if (success) {
                        val mime = if (isVideo) "video/mp4" else "audio/mp3"
                        val savedUri = FileUtils.saveToGallery(requireContext(), File(outputPath), mime)
                        withContext(Dispatchers.Main) {
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                requireContext(),
                                savedUri,
                                AppStrings.get(requireContext(), R.string.string_240),
                                mime
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_241), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    ProcessingManager.showError(e.message ?: AppStrings.get(requireContext(), R.string.msg_noise_failed_fallback))
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

    private suspend fun processLocalDeepFilterNet3(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String, isEqBoost: Boolean, isHarmonicExciter: Boolean, isDeReverb: Boolean): Boolean {
        val pcmInput = File(requireContext().cacheDir, "dfn3_in_${System.currentTimeMillis()}.pcm")
        val pcmOutput = File(requireContext().cacheDir, "dfn3_out_${System.currentTimeMillis()}.pcm")

        try {
            // Step 1: Extract & Resample Audio to 48kHz 16-bit PCM Mono
            val extractCmd = arrayOf(
                "-y", "-i", tempInput.absolutePath,
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le",
                "-ar", "48000", "-ac", "1",
                pcmInput.absolutePath
            )
            val extractSession = FFmpegKit.executeWithArguments(extractCmd)
            if (!ReturnCode.isSuccess(extractSession.returnCode) || !pcmInput.exists() || pcmInput.length() == 0L) {
                val ffmpegLogs = extractSession.allLogsAsString ?: extractSession.output ?: "Unknown FFmpeg error"
                val err = AppStrings.get(requireContext(), R.string.msg_noise_pcm_failed, ffmpegLogs)
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
                return false
            }

            // Step 2: Custom Model Loader to safely locate deep_filter_mobile_model raw resource
            val customModelLoader = object : com.kaleyra.noise_filter.model_loader.DeepFilterModelLoader {
                override suspend fun load(context: android.content.Context): ByteArray {
                    val packageName = context.packageName
                    var resId = context.resources.getIdentifier("deep_filter_mobile_model", "raw", packageName)
                    if (resId == 0) {
                        resId = com.kaleyra.noise_filter.R.raw.deep_filter_mobile_model
                    }
                    val isStream = context.resources.openRawResource(resId)
                    return isStream.use { it.readBytes() }
                }
            }

            val attLimit = when (levelIndex) {
                0 -> 15f     // Gentle noise reduction
                1 -> 30f     // Standard noise reduction
                2 -> 60f     // Maximum noise reduction
                else -> 30f
            }

            val pfBeta = when (levelIndex) {
                0 -> 0.0f    // No post-filtering
                1 -> 0.02f   // Light post-filtering
                2 -> 0.08f   // Strong post-filtering (suppresses deep artifacts)
                else -> 0.02f
            }

            // Initialize NativeDeepFilterNet with custom loader
            val deepFilterNet = com.rikorose.deepfilternet.NativeDeepFilterNet(
                context = requireContext(),
                attenuationLimit = attLimit,
                modelLoader = customModelLoader
            )

            // Wait for asynchronous native model loading via callback latch
            val latch = java.util.concurrent.CountDownLatch(1)
            deepFilterNet.onModelLoaded {
                latch.countDown()
            }

            val loadedOk = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            if (!loadedOk || deepFilterNet.frameLength == null) {
                val err = AppStrings.get(requireContext(), R.string.msg_noise_model_timeout)
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
                return false
            }

            // Set the custom post-filter beta
            deepFilterNet.setPostFilterBeta(pfBeta)

            val frameLength = deepFilterNet.frameLength!!.toInt()
            val bufferSizeBytes = frameLength

            val byteBuffer = ByteBuffer.allocateDirect(bufferSizeBytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            val fis = java.io.FileInputStream(pcmInput)
            val fos = java.io.FileOutputStream(pcmOutput)
            val buffer = ByteArray(bufferSizeBytes)

            var bytesRead: Int
            var totalFrames = 0
            var failedFrames = 0

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                if (!kotlin.coroutines.coroutineContext.isActive) {
                    throw kotlinx.coroutines.CancellationException("Cancelled by user")
                }
                byteBuffer.clear()
                byteBuffer.put(buffer, 0, bytesRead)
                if (bytesRead < bufferSizeBytes) {
                    for (i in bytesRead until bufferSizeBytes) {
                        byteBuffer.put(0.toByte())
                    }
                }
                byteBuffer.flip()

                val score = deepFilterNet.processFrame(byteBuffer)
                totalFrames++
                if (score < 0f) {
                    failedFrames++
                }

                val outputByteArray = ByteArray(byteBuffer.remaining())
                byteBuffer.get(outputByteArray)
                fos.write(outputByteArray, 0, bytesRead)
            }

            fis.close()
            fos.close()
            
            if (totalFrames > 0 && failedFrames == totalFrames) {
                throw IllegalStateException(AppStrings.get(requireContext(), R.string.msg_noise_frames_failed))
            }
            
            try { deepFilterNet.release() } catch (_: Exception) {}

            // Step 3: Re-encode clean PCM back into original container
            val mergeArgs = mutableListOf<String>()
            mergeArgs.addAll(listOf(
                "-y", "-i", tempInput.absolutePath,
                "-f", "s16le", "-ar", "48000", "-ac", "1", "-i", pcmOutput.absolutePath
            ))

            // Build post-processing audio filter chain
            val afParts = mutableListOf<String>()
            if (isEqBoost) {
                afParts.add("bass=f=150:g=1.5,treble=f=6000:g=1.5")
            }
            if (isHarmonicExciter) {
                afParts.add("aexciter=freq=8000:amount=0.2:drive=1.5:level_out=0.8")
            }
            if (isDeReverb) {
                afParts.add("highpass=f=120,agate=threshold=-32dB:ratio=2:range=-15dB:attack=20:release=150")
            }

            if (isVideo) {
                mergeArgs.addAll(listOf(
                    "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy"
                ))
                if (afParts.isNotEmpty()) {
                    mergeArgs.addAll(listOf("-af", afParts.joinToString(",")))
                }
                mergeArgs.addAll(listOf(
                    "-c:a", "aac", "-b:a", "192k",
                    outputPath
                ))
            } else {
                mergeArgs.addAll(listOf("-map", "1:a"))
                if (afParts.isNotEmpty()) {
                    mergeArgs.addAll(listOf("-af", afParts.joinToString(",")))
                }
                mergeArgs.addAll(listOf(
                    "-c:a", "libmp3lame", "-q:a", "2",
                    outputPath
                ))
            }

            val mergeSession = FFmpegKit.executeWithArguments(mergeArgs.toTypedArray())
            val success = ReturnCode.isSuccess(mergeSession.returnCode)
            if (!success) {
                val ffmpegLogs = mergeSession.allLogsAsString ?: mergeSession.output ?: "Unknown FFmpeg error"
                val err = AppStrings.get(requireContext(), R.string.msg_noise_merge_failed, ffmpegLogs)
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
            }
            return success
        } catch (e: Exception) {
            e.printStackTrace()
            val errorDetails = AppStrings.get(requireContext(), R.string.msg_noise_df3_error, (e.localizedMessage ?: e.message).orEmpty())
            com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                requireContext(),
                "NoiseReduction",
                "DeepFilterNet3 execution error: $errorDetails",
                e
            )
            withContext(Dispatchers.Main) {
                ProcessingManager.showError(errorDetails)
            }
            return false
        } finally {
            try { pcmInput.delete() } catch (_: Exception) {}
            try { pcmOutput.delete() } catch (_: Exception) {}
        }
    }

    private suspend fun processCloudDeepFilterNet2(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String, isDeReverb: Boolean): Boolean {
        val context = context ?: return false
        val token = com.example.accessiblevideoeditor.ui.CloudConfigManager.getDolbyToken(context)
        if (token.isEmpty() || token == "YOUR_DOLBY_TOKEN_HERE") {
            android.util.Log.e("DolbyEnhance", "Dolby.io API token is empty or default developer token")
            return false
        }

        try {
            // Step 1: Request Dolby input upload URL
            val uploadInfoJson = dolbyApiRequest("https://api.dolby.com/media/input", "POST", token, "{\"url\": \"dlb://in/input.wav\"}")
                ?: return false
            val uploadUrl = org.json.JSONObject(uploadInfoJson).optString("url", "")
            if (uploadUrl.isEmpty()) return false

            // Step 2: Upload local file to Dolby storage
            val uploadSuccess = uploadFileToUrl(uploadUrl, tempInput)
            if (!uploadSuccess) return false

            // Step 3: Trigger Dolby Media Enhance job
            val dolbyAmount = when (levelIndex) {
                0 -> "low"
                1 -> "medium"
                2 -> "high"
                else -> "medium"
            }
            val enhanceObject = org.json.JSONObject().apply {
                put("noise", org.json.JSONObject().apply {
                    put("reduction", org.json.JSONObject().apply {
                        put("enable", true)
                        put("amount", dolbyAmount)
                    })
                })
                if (isDeReverb) {
                    put("speech", org.json.JSONObject().apply {
                        put("isolation", org.json.JSONObject().apply {
                            put("enable", true)
                        })
                    })
                }
            }
            val jsonBody = org.json.JSONObject().apply {
                put("input", "dlb://in/input.wav")
                put("output", "dlb://out/output.wav")
                put("enhance", enhanceObject)
            }.toString()

            val enhanceJobJson = dolbyApiRequest(
                "https://api.dolby.com/media/enhance",
                "POST",
                token,
                jsonBody
            ) ?: return false
            val jobId = org.json.JSONObject(enhanceJobJson).optString("job_id", "")
            if (jobId.isEmpty()) return false

            // Step 4: Poll Dolby Enhance job status
            var jobDone = false
            var success = false
            var attempts = 0
            while (!jobDone && attempts < 40) {
                kotlinx.coroutines.delay(3000)
                attempts++
                val statusJson = dolbyApiRequest("https://api.dolby.com/media/enhance?job_id=$jobId", "GET", token, null)
                    ?: return false
                val statusObj = org.json.JSONObject(statusJson)
                val status = statusObj.optString("status", "")
                if (status == "Success") {
                    jobDone = true
                    success = true
                } else if (status == "Failed") {
                    jobDone = true
                    success = false
                }
            }
            if (!success) return false

            // Step 5: Request Dolby output download URL
            val downloadInfoJson = dolbyApiRequest("https://api.dolby.com/media/output", "POST", token, "{\"url\": \"dlb://out/output.wav\"}")
                ?: return false
            val downloadUrl = org.json.JSONObject(downloadInfoJson).optString("url", "")
            if (downloadUrl.isEmpty()) return false

            // Step 6: Download the enhanced file to outputPath
            return downloadFileFromUrl(downloadUrl, File(outputPath))
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun dolbyApiRequest(urlStr: String, method: String, token: String, body: String?): String? {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    val input = body.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
            }

            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK || responseCode == java.net.HttpURLConnection.HTTP_CREATED) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val errMsg = errorStream.bufferedReader().use { it.readText() }
                    android.util.Log.e("DolbyApi", "API Error: $responseCode - $errMsg")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun uploadFileToUrl(urlStr: String, file: File): Boolean {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Content-Length", file.length().toString())

            java.io.FileInputStream(file).use { fis ->
                connection.outputStream.use { os ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                }
            }
            return connection.responseCode == java.net.HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return false
    }

    private fun downloadFileFromUrl(urlStr: String, targetFile: File): Boolean {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                if (targetFile.exists()) targetFile.delete()
                connection.inputStream.use { inputStream ->
                    java.io.FileOutputStream(targetFile).use { fos ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
                return targetFile.exists() && targetFile.length() > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return false
    }

    private fun processOptimizedDSP(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String, isEqBoost: Boolean, isMultiBand: Boolean, isHarmonicExciter: Boolean, isDeReverb: Boolean): Boolean {
        val audioFilter = if (isMultiBand) {
            when (levelIndex) {
                0 -> "acrossover=split='200 3000'[low][mid][high]; [low]afftdn=nr=15:nf=-35[l]; [mid]afftdn=nr=5:nf=-45[m]; [high]afftdn=nr=10:nf=-40[h]; [l][m][h]amix=inputs=3:normalize=0"
                1 -> "acrossover=split='200 3000'[low][mid][high]; [low]afftdn=nr=25:nf=-30[l]; [mid]afftdn=nr=10:nf=-40[m]; [high]afftdn=nr=20:nf=-35[h]; [l][m][h]amix=inputs=3:normalize=0"
                2 -> "acrossover=split='200 3000'[low][mid][high]; [low]afftdn=nr=35:nf=-25[l]; [mid]afftdn=nr=15:nf=-35[m]; [high]afftdn=nr=30:nf=-30[h]; [l][m][h]amix=inputs=3:normalize=0"
                else -> "acrossover=split='200 3000'[low][mid][high]; [low]afftdn=nr=25:nf=-30[l]; [mid]afftdn=nr=10:nf=-40[m]; [high]afftdn=nr=20:nf=-35[h]; [l][m][h]amix=inputs=3:normalize=0"
            }
        } else {
            when (levelIndex) {
                0 -> "highpass=f=80,lowpass=f=12000,afftdn=nr=10:nf=-40,agate=threshold=-35dB:ratio=1.5:range=-12dB"
                1 -> "highpass=f=80,lowpass=f=12000,afftdn=nr=20:nf=-30,agate=threshold=-30dB:ratio=2:range=-20dB"
                2 -> "highpass=f=80,lowpass=f=12000,afftdn=nr=30:nf=-25,agate=threshold=-25dB:ratio=3:range=-24dB"
                else -> "highpass=f=80,lowpass=f=12000,afftdn=nr=20:nf=-30,agate=threshold=-30dB:ratio=2:range=-20dB"
            }
        }

        var finalFilter = audioFilter
        if (isEqBoost) {
            finalFilter = "$finalFilter,bass=f=150:g=1.5,treble=f=6000:g=1.5"
        }
        if (isHarmonicExciter) {
            finalFilter = "$finalFilter,aexciter=freq=8000:amount=0.2:drive=1.5:level_out=0.8"
        }
        if (isDeReverb) {
            finalFilter = "$finalFilter,highpass=f=120,agate=threshold=-32dB:ratio=2:range=-15dB:attack=20:release=150"
        }

        val commandArgs = mutableListOf<String>()
        commandArgs.addAll(listOf("-y", "-i", tempInput.absolutePath))

        if (isVideo) {
            commandArgs.addAll(listOf(
                "-af", finalFilter,
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "192k",
                outputPath
            ))
        } else {
            commandArgs.addAll(listOf(
                "-af", finalFilter,
                "-c:a", "libmp3lame", "-q:a", "2",
                outputPath
            ))
        }

        val session = FFmpegKit.executeWithArguments(commandArgs.toTypedArray())
        return ReturnCode.isSuccess(session.returnCode)
    }

    /**
     * CleanUNet FP16 - Studio Quality AI Speech Enhancement
     *
     * CleanUNet uses a U-Net architecture operating directly on waveforms (raw audio).
     * Input: Float32 waveform tensor of shape [1, 1, frameLen] at 16kHz mono
     * Output: Float32 denoised waveform tensor of shape [1, 1, frameLen]
     *
     * Processing pipeline:
     *   1. Extract 16kHz mono PCM from input via FFmpeg
     *   2. Run ONNX Runtime inference frame-by-frame (chunk_length = 16000 * 5 = 80000 samples)
     *   3. Re-encode the denoised PCM back into the original container
     */
    private suspend fun processCleanUNet(
        tempInput: File,
        isVideo: Boolean,
        levelIndex: Int,
        outputPath: String,
        modelFile: File,
        isEqBoost: Boolean,
        isHarmonicExciter: Boolean,
        isDeReverb: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val pcmInput = File(requireContext().cacheDir, "cu_in_${System.currentTimeMillis()}.pcm")
        val pcmOutput = File(requireContext().cacheDir, "cu_out_${System.currentTimeMillis()}.pcm")

        try {
            // Step 1: Extract and resample audio to 16kHz 32-bit float PCM Mono
            // CleanUNet operates on 16kHz mono waveforms
            val extractCmd = arrayOf(
                "-y", "-i", tempInput.absolutePath,
                "-vn", "-f", "f32le", "-acodec", "pcm_f32le",
                "-ar", "16000", "-ac", "1",
                pcmInput.absolutePath
            )
            val extractSession = FFmpegKit.executeWithArguments(extractCmd)
            if (!ReturnCode.isSuccess(extractSession.returnCode) || !pcmInput.exists() || pcmInput.length() == 0L) {
                val logs = extractSession.allLogsAsString ?: "Unknown FFmpeg error"
                val err = AppStrings.get(requireContext(), R.string.msg_noise_pcm_failed, logs)
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
                return@withContext false
            }

            // Step 2: Run CleanUNet ONNX inference
            // Use chunk size of 5 seconds at 16kHz = 80000 float32 samples = 320000 bytes
            val chunkSizeSamples = 80000
            val chunkSizeBytes = chunkSizeSamples * 4 // float32 = 4 bytes per sample

            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            val opts = ai.onnxruntime.OrtSession.SessionOptions()
            var session: ai.onnxruntime.OrtSession? = null
            var fis: FileInputStream? = null
            var fos: FileOutputStream? = null
            try {
                session = env.createSession(modelFile.absolutePath, opts)

                var isInvalidModel = false
                try {
                    val inputInfo = session.inputInfo
                    val firstNode = inputInfo.values.firstOrNull()
                    if (firstNode != null) {
                        val valInfo = firstNode.info
                        if (valInfo is ai.onnxruntime.TensorInfo) {
                            if (valInfo.type == ai.onnxruntime.OnnxJavaType.INT64) {
                                isInvalidModel = true
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (isInvalidModel) {
                    throw IllegalArgumentException("The downloaded model file is invalid (expected CleanUNet, but found a TTS model instead).")
                }

                fis = FileInputStream(pcmInput)
                fos = FileOutputStream(pcmOutput)
                val readBuffer = ByteArray(chunkSizeBytes)

                var bytesRead: Int
                while (fis.read(readBuffer).also { bytesRead = it } != -1) {
                    if (!kotlin.coroutines.coroutineContext.isActive) {
                        throw kotlinx.coroutines.CancellationException("Cancelled by user")
                    }
                    // Determine actual samples read
                    val samplesRead = bytesRead / 4

                    // Build float array (pad with zeros if last chunk is smaller)
                    val inputFloats = FloatArray(chunkSizeSamples)
                    val bb = ByteBuffer.wrap(readBuffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                    val floatBuf = bb.asFloatBuffer()
                    floatBuf.get(inputFloats, 0, samplesRead)

                    // Create ORT tensor: shape [1, 1, chunkSizeSamples]
                    val inputTensor = ai.onnxruntime.OnnxTensor.createTensor(
                        env,
                        java.nio.FloatBuffer.wrap(inputFloats),
                        longArrayOf(1L, 1L, chunkSizeSamples.toLong())
                    )

                    try {
                        // Run inference
                        val inputName = session.inputNames.iterator().next()
                        val results = session.run(mapOf(inputName to inputTensor))
                        try {
                            val outputTensorValue = results[0] as ai.onnxruntime.OnnxTensor
                            val outputBuffer = outputTensorValue.floatBuffer
                            val outputArray = FloatArray(chunkSizeSamples)
                            outputBuffer.get(outputArray)

                            // Apply level-based post-processing blend
                            val blendFactor = when (levelIndex) {
                                0 -> 0.50f   // Mild (50% clean, 50% warm original voice)
                                1 -> 0.75f   // Medium (75% clean, 25% original voice)
                                2 -> 1.00f   // Strong (100% clean, full AI noise cancellation)
                                else -> 0.75f
                            }

                            // Convert output floats back to bytes (only samplesRead samples, not the padded zeros)
                            val outBb = ByteBuffer.allocate(samplesRead * 4).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until samplesRead) {
                                val cleanSample = outputArray[i]
                                val origSample = inputFloats[i]
                                val blended = origSample + (cleanSample - origSample) * blendFactor
                                outBb.putFloat(blended.coerceIn(-1.0f, 1.0f))
                            }
                            fos.write(outBb.array())
                        } finally {
                            results.close()
                        }
                    } finally {
                        inputTensor.close()
                    }
                }
            } finally {
                try { fis?.close() } catch (_: Exception) {}
                try { fos?.close() } catch (_: Exception) {}
                try { session?.close() } catch (_: Exception) {}
                try { opts.close() } catch (_: Exception) {}
            }

            // Step 3: Re-encode denoised PCM back into original container
            // CleanUNet outputs 16kHz f32le PCM, re-encode to 16kHz AAC or MP3
            val mergeArgs = mutableListOf<String>()
            mergeArgs.addAll(listOf(
                "-y", "-i", tempInput.absolutePath,
                "-f", "f32le", "-ar", "16000", "-ac", "1", "-i", pcmOutput.absolutePath
            ))

            // Build post-processing audio filter chain
            val afParts = mutableListOf<String>()
            if (isEqBoost) {
                afParts.add("bass=f=150:g=1.5,treble=f=6000:g=1.5")
            }
            if (isHarmonicExciter) {
                afParts.add("aexciter=freq=8000:amount=0.2:drive=1.5:level_out=0.8")
            }
            if (isDeReverb) {
                afParts.add("highpass=f=120,agate=threshold=-32dB:ratio=2:range=-15dB:attack=20:release=150")
            }

            if (isVideo) {
                mergeArgs.addAll(listOf(
                    "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy"
                ))
                if (afParts.isNotEmpty()) {
                    mergeArgs.addAll(listOf("-af", afParts.joinToString(",")))
                }
                mergeArgs.addAll(listOf(
                    "-c:a", "aac", "-b:a", "192k",
                    "-ar", "44100",
                    outputPath
                ))
            } else {
                mergeArgs.addAll(listOf("-map", "1:a"))
                if (afParts.isNotEmpty()) {
                    mergeArgs.addAll(listOf("-af", afParts.joinToString(",")))
                }
                mergeArgs.addAll(listOf(
                    "-c:a", "libmp3lame", "-q:a", "2",
                    "-ar", "44100",
                    outputPath
                ))
            }

            val mergeSession = FFmpegKit.executeWithArguments(mergeArgs.toTypedArray())
            val success = ReturnCode.isSuccess(mergeSession.returnCode)
            if (!success) {
                val logs = mergeSession.allLogsAsString ?: "Unknown FFmpeg error"
                val err = AppStrings.get(requireContext(), R.string.msg_noise_merge_failed, logs)
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            val errorDetails = AppStrings.get(requireContext(), R.string.msg_noise_df3_error, (e.localizedMessage ?: e.message).orEmpty())
            com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                requireContext(),
                "NoiseReduction",
                "CleanUNet execution error: $errorDetails",
                e
            )
            false
        } finally {
            try { pcmInput.delete() } catch (_: Exception) {}
            try { pcmOutput.delete() } catch (_: Exception) {}
        }
    }

    private fun processQuickPreview(uri: Uri, modeIndex: Int, levelIndex: Int, isEqBoost: Boolean, isMultiBand: Boolean, isHarmonicExciter: Boolean, isDeReverb: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing("جاري توليد معاينة سريعة 20 ثانية...")
                    ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                }

                val ctx = requireContext()
                val totalDurationMs = MediaUtils.getVideoDuration(ctx, uri)
                val startMs = if (totalDurationMs > 25000L) (totalDurationMs / 2L) - 10000L else 0L
                val startSec = startMs / 1000.0

                val tempInput = MediaUtils.copyUriToTempFile(ctx, uri, "preview_raw_${System.currentTimeMillis()}") ?: return@launch
                val isVideo = MediaUtils.isVideoFile(ctx, uri)
                val ext = if (isVideo) "mp4" else "mp3"

                val sliceOriginal = File(ctx.cacheDir, "prev_orig_${System.currentTimeMillis()}.$ext")
                val sliceProcessed = File(ctx.cacheDir, "prev_proc_${System.currentTimeMillis()}.$ext")

                val cutOriginalCmd = arrayOf(
                    "-y", "-ss", String.format(java.util.Locale.US, "%.3f", startSec),
                    "-i", tempInput.absolutePath, "-t", "20.000",
                    "-c:v", "copy", "-c:a", "copy",
                    sliceOriginal.absolutePath
                )
                val cutSession = FFmpegKit.executeWithArguments(cutOriginalCmd)
                if (!ReturnCode.isSuccess(cutSession.returnCode) || !sliceOriginal.exists() || sliceOriginal.length() == 0L) {
                    throw Exception("فشل اقتطاع المقطع الأصلي للمعاينة")
                }

                var success = false
                when (modeIndex) {
                    MODE_DFN3 -> {
                        success = processLocalDeepFilterNet3(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, isEqBoost, isHarmonicExciter, isDeReverb)
                    }
                    MODE_CLOUD -> {
                        success = processCloudDeepFilterNet2(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, isDeReverb)
                        if (!success) {
                            success = processLocalDeepFilterNet3(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, isEqBoost, isHarmonicExciter, isDeReverb)
                        }
                    }
                    MODE_DSP -> {
                        success = processOptimizedDSP(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, isEqBoost, isMultiBand, isHarmonicExciter, isDeReverb)
                    }
                    MODE_CLEANUNET -> {
                        val modelFile = CloudConfigManager.getDownloadedModelFile(ctx, cleanUNetFeatureId)
                        if (modelFile != null && modelFile.exists()) {
                            success = processCleanUNet(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, modelFile, isEqBoost, isHarmonicExciter, isDeReverb)
                        }
                        if (!success) {
                            success = processLocalDeepFilterNet3(sliceOriginal, isVideo, levelIndex, sliceProcessed.absolutePath, isEqBoost, isHarmonicExciter, isDeReverb)
                        }
                    }
                }

                if (success && sliceProcessed.exists() && sliceProcessed.length() > 0L) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.stopProcessing()
                        showPreviewABDialog(sliceOriginal, sliceProcessed, isVideo)
                    }
                } else {
                    throw Exception("فشل تصفية مقطع المعاينة")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    Toast.makeText(requireContext(), "فشلت المعاينة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPreviewABDialog(originalFile: File, processedFile: File, isVideo: Boolean) {
        val context = requireContext()
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("مقارنة المعاينة (20 ثانية)")
            .setMessage("اختر المقطع للاستماع والمقارنة بين الأصلي والمصفى:")
            .setCancelable(true)
            .create()

        val dp16 = 16.toDp(context)
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, dp16)
        }

        var player: ExoPlayer? = ExoPlayer.Builder(context).build()

        var playerView: androidx.media3.ui.PlayerView? = null
        if (isVideo) {
            playerView = androidx.media3.ui.PlayerView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    200.toDp(context)
                ).apply {
                    setMargins(0, 0, 0, 16.toDp(context))
                }
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            playerView.player = player
            rootLayout.addView(playerView)
        }

        val btnPlayClean = com.google.android.material.button.MaterialButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8.toDp(context))
            }
            text = "تشغيل الصوت المصفى (بعد المعالجة)"
            contentDescription = "تشغيل الصوت المصفى بعد المعالجة وعزل الضوضاء"
            setOnClickListener {
                player?.stop()
                val mediaItem = MediaItem.fromUri(Uri.fromFile(processedFile))
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            }
        }
        rootLayout.addView(btnPlayClean)

        val btnPlayOriginal = com.google.android.material.button.MaterialButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16.toDp(context))
            }
            text = "تشغيل الصوت الأصلي (قبل المعالجة)"
            contentDescription = "تشغيل الصوت الأصلي قبل المعالجة والضوضاء"
            setOnClickListener {
                player?.stop()
                val mediaItem = MediaItem.fromUri(Uri.fromFile(originalFile))
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.play()
            }
        }
        rootLayout.addView(btnPlayOriginal)

        dialog.setButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE, "موافق (إغلاق المعاينة)") { d, _ ->
            player?.stop()
            player?.release()
            player = null
            d.dismiss()
        }

        dialog.setOnDismissListener {
            player?.stop()
            player?.release()
            player = null
        }

        dialog.setView(rootLayout)
        dialog.show()
    }

    private fun showDolbyTokenDialog() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("AccessibleVideoEditorPrefs", android.content.Context.MODE_PRIVATE)
        val currentToken = prefs.getString("dolby_token_input", "") ?: ""

        val input = android.widget.EditText(ctx).apply {
            setText(currentToken)
            hint = "أدخل رمز Dolby.io API Token هنا"
            setPadding(16.toDp(ctx), 16.toDp(ctx), 16.toDp(ctx), 16.toDp(ctx))
        }

        AlertDialog.Builder(ctx)
            .setTitle("إعدادات رمز Dolby السحابي")
            .setMessage("الرجاء لصق رمز Dolby.io Client Access Token الخاص بك لتفعيل خدمة المعالجة السحابية:")
            .setView(input)
            .setPositiveButton("حفظ") { dialog, _ ->
                val enteredToken = input.text.toString().trim()
                prefs.edit().putString("dolby_token_input", enteredToken).apply()
                Toast.makeText(ctx, "تم حفظ الرمز بنجاح!", Toast.LENGTH_SHORT).show()
                view?.announceForAccessibility("تم حفظ رمز دولبي بنجاح")
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startAudioAnalysis(uri: Uri) {
        val ctx = context ?: return
        binding.cvAiRecommendation.visibility = View.VISIBLE
        binding.tvAiRecommendationText.text = "جاري فحص طبيعة الصوت وتحليل الضوضاء..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = MediaUtils.copyUriToTempFile(ctx, uri, "analysis_${System.currentTimeMillis()}")
                if (tempInput == null || !tempInput.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.cvAiRecommendation.visibility = View.GONE
                    }
                    return@launch
                }

                val cmd = arrayOf(
                    "-y", "-i", tempInput.absolutePath,
                    "-t", "5.000",
                    "-af", "astats=metadata=1",
                    "-f", "null", "-"
                )
                val session = FFmpegKit.executeWithArguments(cmd)
                val logs = session.allLogsAsString ?: ""

                // Parse Noise Floor
                var noiseFloor = -100f
                val lines = logs.split("\n")
                for (line in lines) {
                    if (line.contains("Noise floor:")) {
                        val parts = line.split("Noise floor:")
                        if (parts.size > 1) {
                            val floorVal = parts[1].trim().replace("dB", "").replace("dBFS", "").toFloatOrNull()
                            if (floorVal != null && floorVal > noiseFloor) {
                                noiseFloor = floorVal
                            }
                        }
                    }
                }

                // If noiseFloor is still -100, try to parse from "RMS level dB"
                if (noiseFloor == -100f) {
                    for (line in lines) {
                        if (line.contains("RMS level dB:") || line.contains("RMS level:")) {
                            val parts = line.split(":")
                            if (parts.size > 1) {
                                val rmsVal = parts[1].trim().replace("dB", "").replace("dBFS", "").replace("Overall", "").trim().toFloatOrNull()
                                if (rmsVal != null) {
                                    noiseFloor = rmsVal - 15f
                                }
                            }
                        }
                    }
                }

                try { tempInput.delete() } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext

                    val recommendation: String
                    val speakAnnounce: String

                    if (noiseFloor > -35f) {
                        recommendation = "ضوضاء شديدة ومزعجة جداً (حوالي ${String.format(java.util.Locale.US, "%.1f", noiseFloor)} dB).\nتوصية: استخدام خيار CleanUNet المحلي (مستوى قوي) مع إلغاء صدى الغرفة."
                        speakAnnounce = "توصية الذكاء الاصطناعي: تم رصد ضوضاء شديدة جداً. نوصي باستخدام خيار كليين يو نت بمستوى قوي مع إلغاء صدى الغرفة."
                    } else if (noiseFloor > -50f) {
                        recommendation = "ضوضاء متوسطة مستمرة (حوالي ${String.format(java.util.Locale.US, "%.1f", noiseFloor)} dB).\nتوصية: استخدام خيار Dolby السحابي أو CleanUNet بمستوى متوسط مع إلغاء صدى الغرفة."
                        speakAnnounce = "توصية الذكاء الاصطناعي: تم رصد ضوضاء متوسطة. نوصي باستخدام خيار الاستوديو السحابي دولبي أو كليين يو نت بمستوى متوسط مع إلغاء صدى الغرفة."
                    } else {
                        recommendation = "ضوضاء خفيفة أو صوت نقي نسبياً (حوالي ${String.format(java.util.Locale.US, "%.1f", noiseFloor)} dB).\nتوصية: استخدام خيار DSP المحلي بمستوى خفيف مع تفعيل تعزيز نبرة وجودة الصوت."
                        speakAnnounce = "توصية الذكاء الاصطناعي: الصوت نقي نسبياً. نوصي باستخدام محرك دي إس بي بمستوى خفيف مع تعزيز النبرة والجودة."
                    }

                    binding.tvAiRecommendationText.text = recommendation
                    view?.announceForAccessibility(speakAnnounce)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.cvAiRecommendation.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun Int.toDp(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        ProcessingManager.sharedMediaUri?.let { uri ->
            selectedUri = uri
            ProcessingManager.sharedMediaUri = null
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = AppStrings.get(requireContext(), R.string.string_16)
            startAudioAnalysis(uri)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
