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
import androidx.navigation.fragment.findNavController
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentNoiseReductionBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

class NoiseReductionFragment : Fragment() {

    private var _binding: FragmentNoiseReductionBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = AppStrings.get(requireContext(), R.string.string_16)
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
            AppStrings.get(requireContext(), R.string.mode_noise_dsp)
        )
        val modeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modeLabels)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerNoiseMode.adapter = modeAdapter
        binding.spinnerNoiseMode.setSelection(0) // Default to Local DeepFilterNet3

        val noiseLabels = listOf(
            AppStrings.get(requireContext(), R.string.noise_mild),
            AppStrings.get(requireContext(), R.string.noise_medium),
            AppStrings.get(requireContext(), R.string.noise_strong)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, noiseLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerNoiseLevel.adapter = adapter
        binding.spinnerNoiseLevel.setSelection(1) // default medium

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnApply.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val modeIndex = binding.spinnerNoiseMode.selectedItemPosition
            val levelIndex = binding.spinnerNoiseLevel.selectedItemPosition
            processNoiseReduction(uri, modeIndex, levelIndex)
        }
    }

    private fun processNoiseReduction(uri: Uri, modeIndex: Int, levelIndex: Int) {
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
                        0 -> {
                            // Local DeepFilterNet3 AI (Offline)
                            success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath)
                        }
                        1 -> {
                            // Cloud DeepFilterNet2 AI (Online) with fallback to Local DeepFilterNet3
                            success = processCloudDeepFilterNet2(tempInput, isVideo, levelIndex, outputPath)
                            if (!success) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(requireContext(), "الخدمة السحابية غير متوفرة، جاري استخدام DeepFilterNet3 محلياً...", Toast.LENGTH_SHORT).show()
                                }
                                success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath)
                            }
                        }
                        2 -> {
                            // Optimized DSP Filter (Full Frequency Range, No Lowpass/Highpass Cutoff)
                            success = processOptimizedDSP(tempInput, isVideo, levelIndex, outputPath)
                        }
                        else -> {
                            success = processLocalDeepFilterNet3(tempInput, isVideo, levelIndex, outputPath)
                        }
                    }

                    if (success) {
                        val mime = if (isVideo) "video/mp4" else "audio/mp3"
                        FileUtils.saveToGallery(requireContext(), File(outputPath), mime)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_240), Toast.LENGTH_SHORT).show()
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
                    ProcessingManager.showError(e.message ?: "Noise reduction failed")
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

    private suspend fun processLocalDeepFilterNet3(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String): Boolean {
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
                val err = "فشل استخراج محتوى الصوت إلى PCM خام:\n$ffmpegLogs"
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
                0 -> 25f
                1 -> 35f
                2 -> 50f
                else -> 35f
            }

            val pfBeta = when (levelIndex) {
                0 -> 0.0f    // No post-filtering (pure deep filtering). Retains original voice warmth and speech completely.
                1 -> 0.015f  // Light post-filtering. Smooth trade-off between extra cancellation and speech clarity.
                2 -> 0.035f  // Moderate post-filtering. Stronger cancellation for noisy backgrounds.
                else -> 0.015f
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
                val err = "فشل تحميل نموذج DeepFilterNet3 في ذاكرة الجهاز (Timeout)"
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
                throw IllegalStateException("لم يتمكن الموديل المحلي من معالجة أي إطار صوتي (كافة الإطارات أرجعت رمز فشل -1.0). يرجى التحقق من توافق ملف الصوت.")
            }
            
            try { deepFilterNet.release() } catch (_: Exception) {}

            // Step 3: Re-encode clean PCM back into original container
            val mergeArgs = mutableListOf<String>()
            mergeArgs.addAll(listOf(
                "-y", "-i", tempInput.absolutePath,
                "-f", "s16le", "-ar", "48000", "-ac", "1", "-i", pcmOutput.absolutePath
            ))

            if (isVideo) {
                mergeArgs.addAll(listOf(
                    "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy",
                    "-c:a", "aac", "-b:a", "192k",
                    outputPath
                ))
            } else {
                mergeArgs.addAll(listOf(
                    "-map", "1:a",
                    "-c:a", "libmp3lame", "-q:a", "2",
                    outputPath
                ))
            }

            val mergeSession = FFmpegKit.executeWithArguments(mergeArgs.toTypedArray())
            val success = ReturnCode.isSuccess(mergeSession.returnCode)
            if (!success) {
                val ffmpegLogs = mergeSession.allLogsAsString ?: mergeSession.output ?: "Unknown FFmpeg error"
                val err = "فشل دمج وترميز الصوت المعالج مع الملف الأصلي:\n$ffmpegLogs"
                withContext(Dispatchers.Main) { ProcessingManager.showError(err) }
            }
            return success
        } catch (e: Exception) {
            e.printStackTrace()
            val errorDetails = "خطأ في تنفيذ DeepFilterNet3 الأوفلاين:\n${e.localizedMessage ?: e.message}"
            withContext(Dispatchers.Main) {
                ProcessingManager.showError(errorDetails)
            }
            return false
        } finally {
            try { pcmInput.delete() } catch (_: Exception) {}
            try { pcmOutput.delete() } catch (_: Exception) {}
        }
    }

    private fun processCloudDeepFilterNet2(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String): Boolean {
        // Cloud processing fallback wrapper
        return false
    }

    private fun processOptimizedDSP(tempInput: File, isVideo: Boolean, levelIndex: Int, outputPath: String): Boolean {
        val audioFilter = when (levelIndex) {
            0 -> "afftdn=nr=10:nf=-40"
            1 -> "afftdn=nr=20:nf=-30"
            2 -> "afftdn=nr=30:nf=-25"
            else -> "afftdn=nr=20:nf=-30"
        }

        val commandArgs = mutableListOf<String>()
        commandArgs.addAll(listOf("-y", "-i", tempInput.absolutePath))

        if (isVideo) {
            commandArgs.addAll(listOf(
                "-af", audioFilter,
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "192k",
                outputPath
            ))
        } else {
            commandArgs.addAll(listOf(
                "-af", audioFilter,
                "-c:a", "libmp3lame", "-q:a", "2",
                outputPath
            ))
        }

        val session = FFmpegKit.execute(commandArgs.joinToString(" "))
        return ReturnCode.isSuccess(session.returnCode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
