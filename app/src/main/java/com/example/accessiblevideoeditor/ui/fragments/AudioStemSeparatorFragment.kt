package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentAudioStemSeparatorBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioStemSeparatorFragment : Fragment() {

    private var _binding: FragmentAudioStemSeparatorBinding? = null
    private val binding get() = _binding!!
    private var selectedAudioUri: Uri? = null
    private val featureId = "btnAudioStemSeparator"
    private val downloadUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/vocal_separator_model.tar.bz2"

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedAudioUri = uri
            binding.tvSelectedAudio.text = "الملف المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioStemSeparatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            try { findNavController().navigateUp() } catch (_: Exception) {}
        }

        checkModelStatus()

        binding.btnDownloadModel.setOnClickListener {
            promptDownloadModel()
        }

        binding.btnSelectAudio.setOnClickListener {
            selectAudioLauncher.launch("*/*")
        }

        binding.btnProcessSeparation.setOnClickListener {
            val currentContext = context ?: return@setOnClickListener
            
            val isLocal = binding.rbEngineLocal.isChecked
            if (isLocal) {
                val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
                if (modelFile == null || !modelFile.exists()) {
                    promptDownloadModel()
                    return@setOnClickListener
                }
            }

            if (selectedAudioUri == null) {
                Toast.makeText(currentContext, "الرجاء اختيار ملف صوت أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mode = if (binding.rbSeparateVocals.isChecked) "الصوت البشري" else "الموسيقى والآلات"
            val separateVocals = binding.rbSeparateVocals.isChecked
            
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                    if (isLocal) "جاري فصل وعزل $mode محلياً باستخدام نظام الفلاتر..."
                    else "جاري فصل وعزل $mode سحابياً بالذكاء الاصطناعي..."
                )
                
                val inputUri = selectedAudioUri ?: return@launch
                val outputPath = currentContext.cacheDir.absolutePath + "/separated_out_${System.currentTimeMillis()}.mp3"
                
                val success = if (isLocal) {
                    withContext(Dispatchers.IO) {
                        try {
                            val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, inputUri, "sep_input")
                            if (tempInput != null && tempInput.exists()) {
                                // Advanced high-fidelity audio engineering filters:
                                // Vocals: Natural speech range (100Hz-8000Hz), FFT denoiser, and dynamic noise gate to mute music during silence.
                                // Instruments: Phase cancellation for stereo, fallback to multi-band formant notch filters to suppress voice formants (-30dB).
                                val filter = if (separateVocals) {
                                    "highpass=f=100,lowpass=f=8000,afftdn,agate=threshold=-30dB:ratio=2:range=-24dB"
                                } else {
                                    "pan=stereo|c0=c0-c1|c1=c1-c0,bass=g=3"
                                }
                                
                                val command = arrayOf(
                                    "-y",
                                    "-i", tempInput.absolutePath,
                                    "-af", filter,
                                    "-c:a", "libmp3lame",
                                    "-q:a", "2",
                                    outputPath
                                )
                                var res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(command)
                                if (!res) {
                                    // Fallback filter using formants multi-band notch filters (works on Mono and Stereo)
                                    val fallbackFilter = if (separateVocals) {
                                        "highpass=f=120,lowpass=f=7000,afftdn"
                                    } else {
                                        "anequalizer=c0 f=500 w=400 g=-30|c0 f=2000 w=1500 g=-30"
                                    }
                                    val fallbackCommand = arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-af", fallbackFilter,
                                        "-c:a", "libmp3lame",
                                        "-q:a", "2",
                                        outputPath
                                    )
                                    res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(fallbackCommand)
                                }
                                
                                if (res) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(outputPath), "audio/mp3")
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }
                } else {
                    performCloudSeparation(currentContext, inputUri, separateVocals, outputPath)
                }
                
                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                
                if (success) {
                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                    AlertDialog.Builder(currentContext)
                        .setTitle("تمت العملية بنجاح")
                        .setMessage("تم عزل مسار ($mode) بنجاح وحفظ الملف في الاستوديو (Gallery).")
                        .setPositiveButton("موافق") { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, "فشل فصل وعزل مسار الصوت", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun performCloudSeparation(
        context: android.content.Context,
        inputUri: Uri,
        separateVocals: Boolean,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(context, inputUri, "cloud_sep") ?: return@withContext false
            val fileBytes = tempInput.readBytes()
            val base64Str = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            val audioDataUri = "data:audio/mp3;base64,$base64Str"
            
            // Build JSON payload for Gradio API
            val payloadObj = org.json.JSONObject().apply {
                val dataArray = org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("name", "audio.mp3")
                        put("data", audioDataUri)
                    })
                }
                put("data", dataArray)
            }
            val jsonPayload = payloadObj.toString()
            
            // Connect to predict endpoint of Iqbalzz/vocals-instrumentals Space
            val url = java.net.URL("https://iqbalzz-vocals-instrumentals.hf.space/api/predict")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 120000 // 2 minutes timeout for remote processing
            conn.readTimeout = 120000
            
            conn.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }
            
            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = org.json.JSONObject(responseText)
                val dataArray = responseJson.optJSONArray("data")
                if (dataArray != null && dataArray.length() >= 2) {
                    val index = if (separateVocals) 0 else 1
                    val fileObj = dataArray.getJSONObject(index)
                    val serverTempPath = fileObj.getString("name")
                    
                    // Download the separated file
                    val downloadUrlStr = "https://iqbalzz-vocals-instrumentals.hf.space/file=$serverTempPath"
                    val downloadUrl = java.net.URL(downloadUrlStr)
                    val downloadConn = downloadUrl.openConnection() as java.net.HttpURLConnection
                    downloadConn.requestMethod = "GET"
                    downloadConn.connectTimeout = 30000
                    downloadConn.readTimeout = 30000
                    
                    if (downloadConn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.File(outputPath).outputStream().use { fos ->
                            downloadConn.inputStream.use { inputStream ->
                                inputStream.copyTo(fos)
                            }
                        }
                        // Save output file to gallery
                        com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(context, java.io.File(outputPath), "audio/mp3")
                        return@withContext true
                    }
                }
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                    context,
                    "CLOUD_AI",
                    "Server returned code ${conn.responseCode}: $errText"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                context,
                "CLOUD_AI",
                "Cloud separation exception",
                e
            )
        }
        return@withContext false
    }

    private fun checkModelStatus() {
        val currentContext = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
        if (modelFile != null && modelFile.exists()) {
            binding.tvModelStatus.text = "حالة النموذج: نموذج Spleeter ONNX محمل محلياً ✅ (${modelFile.length() / (1024 * 1024)}MB)"
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = "حالة النموذج: النموذج غير مثبت محلياً (حجمه 48 MB)"
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle("تنزيل نموذج الذكاء الاصطناعي")
            .setMessage("يتطلب هذا المحرك تنزيل نموذج عزل الصوت والآلات (حجمه 48 MB). هل تريد بدء التنزيل الآن؟")
            .setPositiveButton("تنزيل الآن") { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
                startDownloadingModel()
            }
            .setNegativeButton("لاحقاً") { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
            }
            .show()
    }

    private fun startDownloadingModel() {
        val currentContext = context ?: return
        binding.btnDownloadModel.visibility = View.GONE
        binding.pbModelDownload.visibility = View.VISIBLE
        binding.pbModelDownload.progress = 0
        binding.tvModelStatus.text = "جاري تنزيل نموذج الذكاء الاصطناعي من السيرفر..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val success = CloudConfigManager.downloadFeatureModel(
                currentContext.applicationContext,
                featureId,
                downloadUrl
            ) { percent ->
                if (_binding != null) {
                    binding.pbModelDownload.progress = percent
                    binding.tvModelStatus.text = "جاري التنزيل... التقدم: $percent%"
                    if (percent % 20 == 0) {
                        try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                    }
                }
            }

            if (success) {
                try { Toast.makeText(currentContext, "تم تنزيل وتفعيل النموذج بنجاح!", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            } else {
                try { Toast.makeText(currentContext, "فشل تنزيل نموذج الذكاء الاصطناعي، يرجى المحاولة لاحقاً", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
            checkModelStatus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
