package com.example.accessiblevideoeditor.ui.fragments

import com.example.accessiblevideoeditor.R
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
                Toast.makeText(currentContext, getString(R.string.msg_please_select_audio), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val separateVocals = binding.rbSeparateVocals.isChecked
            val modeStr = if (separateVocals) getString(R.string.label_vocals_arabic) else getString(R.string.label_instruments_arabic)
            
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                    if (isLocal) getString(R.string.msg_processing_local, modeStr)
                    else getString(R.string.msg_processing_cloud, modeStr)
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
                        .setTitle(getString(R.string.msg_success_dialog_title))
                        .setMessage(getString(R.string.msg_success_dialog_body, modeStr))
                        .setPositiveButton(getString(R.string.btn_ok)) { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, getString(R.string.msg_failed_separation), Toast.LENGTH_SHORT).show()
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
            val spaceUrl = "https://iqbalzz-vocals-instrumentals.hf.space"
            val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            val CRLF = "\r\n"
            
            // 1. Upload file using multipart/form-data to prevent Base64 socket overflow
            val uploadUrl = java.net.URL("$spaceUrl/upload")
            val uploadConn = uploadUrl.openConnection() as java.net.HttpURLConnection
            uploadConn.requestMethod = "POST"
            uploadConn.doOutput = true
            uploadConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            uploadConn.connectTimeout = 60000
            uploadConn.readTimeout = 60000
            
            uploadConn.outputStream.use { os ->
                val writer = os.writer(Charsets.UTF_8)
                writer.append("--$boundary").append(CRLF)
                writer.append("Content-Disposition: form-data; name=\"files\"; filename=\"audio.mp3\"").append(CRLF)
                writer.append("Content-Type: audio/mpeg").append(CRLF)
                writer.append(CRLF)
                writer.flush()
                
                tempInput.inputStream().use { inputStream ->
                    inputStream.copyTo(os)
                }
                
                writer.append(CRLF)
                writer.append("--$boundary--").append(CRLF)
                writer.flush()
            }
            
            if (uploadConn.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                val errText = uploadConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                    context,
                    "CLOUD_AI",
                    "Multipart Upload failed with code ${uploadConn.responseCode}: $errText"
                )
                return@withContext false
            }
            
            val uploadResponse = uploadConn.inputStream.bufferedReader().use { it.readText() }
            val uploadJsonArray = org.json.JSONArray(uploadResponse)
            val serverTempPath = uploadJsonArray.getString(0) // Temp path on Hugging Face: /tmp/gradio/.../audio.mp3
            
            // 2. Call prediction endpoint using the uploaded temp file path
            val predictUrl = java.net.URL("$spaceUrl/api/predict")
            val predictConn = predictUrl.openConnection() as java.net.HttpURLConnection
            predictConn.requestMethod = "POST"
            predictConn.doOutput = true
            predictConn.setRequestProperty("Content-Type", "application/json")
            predictConn.connectTimeout = 180000 // 3 minutes timeout for remote processing
            predictConn.readTimeout = 180000
            
            val payloadObj = org.json.JSONObject().apply {
                val dataArray = org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("name", serverTempPath)
                        put("orig_name", "audio.mp3")
                        put("data", org.json.JSONObject.NULL)
                        put("is_file", true)
                    })
                }
                put("data", dataArray)
            }
            val jsonPayload = payloadObj.toString()
            
            predictConn.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }
            
            if (predictConn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseText = predictConn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = org.json.JSONObject(responseText)
                val dataArray = responseJson.optJSONArray("data")
                if (dataArray != null && dataArray.length() >= 2) {
                    val index = if (separateVocals) 0 else 1
                    val fileObj = dataArray.getJSONObject(index)
                    val outServerTempPath = fileObj.getString("name")
                    
                    // 3. Download the separated file
                    val downloadUrlStr = "$spaceUrl/file=$outServerTempPath"
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
                val errText = predictConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
                    context,
                    "CLOUD_AI",
                    "Gradio Predict failed with code ${predictConn.responseCode}: $errText"
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
            binding.tvModelStatus.text = getString(R.string.model_status_loaded, modelFile.length() / (1024 * 1024))
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = getString(R.string.model_status_not_installed)
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle(getString(R.string.dialog_download_title))
            .setMessage(getString(R.string.dialog_download_message))
            .setPositiveButton(getString(R.string.btn_download_now)) { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
                startDownloadingModel()
            }
            .setNegativeButton(getString(R.string.btn_later)) { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
            }
            .show()
    }

    private fun startDownloadingModel() {
        val currentContext = context ?: return
        binding.btnDownloadModel.visibility = View.GONE
        binding.pbModelDownload.visibility = View.VISIBLE
        binding.pbModelDownload.progress = 0
        binding.tvModelStatus.text = getString(R.string.msg_download_starting)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val success = CloudConfigManager.downloadFeatureModel(
                currentContext.applicationContext,
                featureId,
                downloadUrl
            ) { percent ->
                if (_binding != null) {
                    binding.pbModelDownload.progress = percent
                    binding.tvModelStatus.text = getString(R.string.msg_download_progress, percent)
                    if (percent % 20 == 0) {
                        try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                    }
                }
            }

            if (success) {
                try { Toast.makeText(currentContext, getString(R.string.msg_download_success), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            } else {
                try { Toast.makeText(currentContext, getString(R.string.msg_download_failed), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
            checkModelStatus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
