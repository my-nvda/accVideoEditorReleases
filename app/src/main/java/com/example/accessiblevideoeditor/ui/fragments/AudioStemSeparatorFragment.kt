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
import com.example.accessiblevideoeditor.ui.ProcessingManager
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
            binding.tvSelectedAudio.text = getString(R.string.label_selected_file_path, uri.lastPathSegment ?: uri.toString())
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
        wakeUpCloudSpace()

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

            val inputUri = selectedAudioUri
            if (inputUri == null) {
                Toast.makeText(currentContext, getString(R.string.msg_please_select_audio), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val separateVocals = binding.rbSeparateVocals.isChecked
            val modeStr = if (separateVocals) getString(R.string.label_vocals_arabic) else getString(R.string.label_instruments_arabic)
            
            val mimeType = currentContext.contentResolver.getType(inputUri)
            val isVideo = mimeType?.startsWith("video/") == true 
                         || inputUri.path?.endsWith(".mp4", ignoreCase = true) == true
                         || inputUri.path?.endsWith(".mkv", ignoreCase = true) == true
                         || inputUri.path?.endsWith(".3gp", ignoreCase = true) == true

            startSeparationProcess(isVideo, isLocal, separateVocals, modeStr)
        }
    }

    private fun startSeparationProcess(isVideo: Boolean, isLocal: Boolean, separateVocals: Boolean, modeStr: String) {
        val currentContext = context ?: return
        if (isVideo) {
            val options = arrayOf("ملف صوتي (MP3)", "فيديو جديد بالصوت المعدل (MP4)")
            AlertDialog.Builder(currentContext)
                .setTitle("اختر نوع الملف الناتج")
                .setItems(options) { _, which ->
                    val exportAsVideo = (which == 1)
                    executeSeparation(isLocal, separateVocals, modeStr, exportAsVideo)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            executeSeparation(isLocal, separateVocals, modeStr, false)
        }
    }

    private fun executeSeparation(isLocal: Boolean, separateVocals: Boolean, modeStr: String, exportAsVideo: Boolean) {
        val currentContext = context ?: return
        val inputUri = selectedAudioUri ?: return
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(
                if (isLocal) getString(R.string.msg_processing_local, modeStr)
                else getString(R.string.msg_processing_cloud, modeStr)
            )
            com.example.accessiblevideoeditor.ui.ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
            
            val tempAudioOut = currentContext.cacheDir.absolutePath + "/separated_temp_${System.currentTimeMillis()}.mp3"
            var savedUri: Uri? = null
            
            val success = if (isLocal) {
                withContext(Dispatchers.IO) {
                    try {
                        val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, inputUri, "sep_input")
                        if (tempInput != null && tempInput.exists()) {
                            val channels = getAudioChannelCount(currentContext, inputUri)
                            val filter = if (separateVocals) {
                                "highpass=f=100,lowpass=f=8000,afftdn,agate=threshold=-30dB:ratio=2:range=-24dB"
                            } else {
                                if (channels > 1) {
                                    "pan=stereo|c0=c0-c1|c1=c1-c0,bass=g=3"
                                } else {
                                    "bandreject=f=1000:width_type=h:w=800,bass=g=3"
                                }
                            }
                            
                            val command = arrayOf(
                                "-y",
                                "-i", tempInput.absolutePath,
                                "-af", filter,
                                "-c:a", "libmp3lame",
                                "-q:a", "2",
                                tempAudioOut
                            )
                            var res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(command)
                            if (!res) {
                                val fallbackFilter = if (separateVocals) {
                                    "highpass=f=120,lowpass=f=7000,afftdn"
                                } else {
                                    if (channels > 1) {
                                        "anequalizer=c0 f=500 w=400 g=-30|c0 f=2000 w=1500 g=-30|c1 f=500 w=400 g=-30|c1 f=2000 w=1500 g=-30"
                                    } else {
                                        "anequalizer=c0 f=500 w=400 g=-30|c0 f=2000 w=1500 g=-30"
                                    }
                                }
                                val fallbackCommand = arrayOf(
                                    "-y",
                                    "-i", tempInput.absolutePath,
                                    "-af", fallbackFilter,
                                    "-c:a", "libmp3lame",
                                    "-q:a", "2",
                                    tempAudioOut
                                )
                                res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(fallbackCommand)
                            }
                            
                            if (res) {
                                if (exportAsVideo) {
                                    val tempVideoOut = currentContext.cacheDir.absolutePath + "/sep_video_out_${System.currentTimeMillis()}.mp4"
                                    val mergeCmd = arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-i", tempAudioOut,
                                        "-map", "0:v",
                                        "-map", "1:a",
                                        "-c:v", "copy",
                                        "-c:a", "aac",
                                        "-shortest",
                                        tempVideoOut
                                    )
                                    val mergeSuccess = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(mergeCmd)
                                    if (mergeSuccess) {
                                        savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(tempVideoOut), "video/mp4")
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(tempAudioOut), "audio/mp3")
                                    true
                                }
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
                val cloudSuccess = performCloudSeparation(currentContext, inputUri, separateVocals, tempAudioOut)
                if (cloudSuccess) {
                    if (exportAsVideo) {
                        withContext(Dispatchers.IO) {
                            try {
                                val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, inputUri, "sep_input")
                                if (tempInput != null && tempInput.exists()) {
                                    val tempVideoOut = currentContext.cacheDir.absolutePath + "/sep_video_out_${System.currentTimeMillis()}.mp4"
                                    val mergeCmd = arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-i", tempAudioOut,
                                        "-map", "0:v",
                                        "-map", "1:a",
                                        "-c:v", "copy",
                                        "-c:a", "aac",
                                        "-shortest",
                                        tempVideoOut
                                    )
                                    val mergeSuccess = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(mergeCmd)
                                    if (mergeSuccess) {
                                        savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(tempVideoOut), "video/mp4")
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
                        savedUri = com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(tempAudioOut), "audio/mp3")
                        true
                    }
                } else {
                    false
                }
            }
            
            com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
            
            if (success) {
                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                    currentContext,
                    savedUri,
                    getString(R.string.msg_success_dialog_body, modeStr),
                    if (exportAsVideo) "video/mp4" else "audio/mp3"
                )
            } else {
                Toast.makeText(currentContext, getString(R.string.msg_failed_separation), Toast.LENGTH_SHORT).show()
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
            uploadConn.connectTimeout = 180000
            uploadConn.readTimeout = 180000
            
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
            predictConn.connectTimeout = 600000 // 10 minutes timeout for remote processing
            predictConn.readTimeout = 600000
            
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
                    downloadConn.connectTimeout = 120000
                    downloadConn.readTimeout = 120000
                    
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
            val dynamicUrl = com.example.accessiblevideoeditor.ui.CloudConfigManager.getAiModelDownloadInfo(currentContext, featureId).first
            val success = CloudConfigManager.downloadFeatureModel(
                currentContext.applicationContext,
                featureId,
                dynamicUrl
            ) { percent ->
                if (_binding != null) {
                    binding.pbModelDownload.progress = percent
                    binding.tvModelStatus.text = getString(R.string.msg_download_progress, percent)
                    if (percent % 5 == 0) {
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

    private fun getAudioChannelCount(context: android.content.Context, uri: Uri): Int {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val channelsStr = retriever.extractMetadata(33)
            return channelsStr?.toIntOrNull() ?: 2
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return 2
    }

    private fun wakeUpCloudSpace() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://iqbalzz-vocals-instrumentals.hf.space/")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.responseCode // Just trigger connection
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        ProcessingManager.sharedMediaUri?.let { uri ->
            selectedAudioUri = uri
            ProcessingManager.sharedMediaUri = null
            binding.tvSelectedAudio.text = getString(R.string.label_selected_file_path, uri.lastPathSegment ?: uri.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
