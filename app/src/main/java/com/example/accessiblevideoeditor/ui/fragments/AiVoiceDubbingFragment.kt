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
import com.example.accessiblevideoeditor.databinding.FragmentAiVoiceDubbingBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiVoiceDubbingFragment : Fragment() {

    private var _binding: FragmentAiVoiceDubbingBinding? = null
    private val binding get() = _binding!!
    private var selectedMediaUri: Uri? = null
    private val featureId = "btnAiVoiceDubbing"
    private val downloadUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/voice_dubbing.onnx"

    private val selectMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.tvSelectedFile.text = "الملحوق المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiVoiceDubbingBinding.inflate(inflater, container, false)
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

        binding.btnSelectAudioVideo.setOnClickListener {
            selectMediaLauncher.launch("*/*")
        }

        binding.btnGenerateVoice.setOnClickListener {
            val currentContext = context ?: return@setOnClickListener
            val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
            if (modelFile == null || !modelFile.exists()) {
                promptDownloadModel()
                return@setOnClickListener
            }

            val text = binding.etDubbingText.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Toast.makeText(currentContext, "الرجاء كتابة النص المراد دبلجته أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing("جاري توليد الصوت والدبلجة بالذكاء الاصطناعي...")
                
                val tempWav = java.io.File(currentContext.cacheDir, "dubbing_${System.currentTimeMillis()}.wav")
                var ttsSuccess = false
                
                // 1. Try Online Google Translate TTS API
                val success = withContext(Dispatchers.IO) {
                    try {
                        val chunks = splitText(text, 150)
                        val tempFiles = mutableListOf<java.io.File>()
                        var allDownloaded = true
                        
                        for ((index, chunk) in chunks.withIndex()) {
                            val chunkFile = java.io.File(currentContext.cacheDir, "chunk_${index}_${System.currentTimeMillis()}.mp3")
                            val encodedText = java.net.URLEncoder.encode(chunk, "UTF-8")
                            val url = java.net.URL("https://translate.google.com/translate_tts?ie=UTF-8&tl=ar&client=tw-ob&q=$encodedText")
                            val connection = url.openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                            connection.connectTimeout = 8000
                            connection.readTimeout = 8000
                            
                            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                connection.inputStream.use { input ->
                                    chunkFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                tempFiles.add(chunkFile)
                            } else {
                                allDownloaded = false
                                break
                            }
                        }
                        
                        if (allDownloaded && tempFiles.isNotEmpty()) {
                            if (tempFiles.size == 1) {
                                tempFiles[0].renameTo(tempWav)
                            } else {
                                val concatArg = "concat:" + tempFiles.joinToString("|") { it.absolutePath }
                                com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                    arrayOf("-y", "-i", concatArg, "-c", "copy", tempWav.absolutePath)
                                )
                            }
                            tempWav.exists() && tempWav.length() > 0L
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                
                if (success) {
                    ttsSuccess = true
                } else {
                    // 2. Offline Fallback: Local Google TTS with Wavenet prioritizing
                    withContext(Dispatchers.IO) {
                        try {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            var tts: android.speech.tts.TextToSpeech? = null
                            withContext(Dispatchers.Main) {
                                tts = android.speech.tts.TextToSpeech(currentContext) { status ->
                                    latch.countDown()
                                }
                            }
                            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                            
                            if (tts != null) {
                                val arVoices = tts!!.voices?.filter { it.locale.language == "ar" }
                                val targetVoice = arVoices?.find { it.name.contains("wavenet", ignoreCase = true) }
                                    ?: arVoices?.find { it.name.contains("local", ignoreCase = true).not() }
                                    ?: arVoices?.firstOrNull()
                                    
                                if (targetVoice != null) {
                                    tts!!.voice = targetVoice
                                } else {
                                    tts!!.setLanguage(java.util.Locale("ar"))
                                }
                                
                                val voiceLatch = java.util.concurrent.CountDownLatch(1)
                                tts!!.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) {
                                        ttsSuccess = true
                                        voiceLatch.countDown()
                                    }
                                    override fun onError(utteranceId: String?) {
                                        voiceLatch.countDown()
                                    }
                                })
                                
                                val params = Bundle()
                                params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "dubbing")
                                withContext(Dispatchers.Main) {
                                    tts!!.synthesizeToFile(text, params, tempWav, "dubbing")
                                }
                                
                                voiceLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                                tts!!.shutdown()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                if (!ttsSuccess || !tempWav.exists() || tempWav.length() == 0L) {
                    com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_dubbing_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val mediaUri = selectedMediaUri
                val outputSaved = withContext(Dispatchers.IO) {
                    try {
                        if (mediaUri != null) {
                            val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, mediaUri, "dub_video_in")
                            if (tempVideo != null && tempVideo.exists()) {
                                val outputPath = currentContext.cacheDir.absolutePath + "/dubbed_out_${System.currentTimeMillis()}.mp4"
                                // Replace video audio with our new dubbed voice wav
                                val command = arrayOf(
                                    "-y",
                                    "-i", tempVideo.absolutePath,
                                    "-i", tempWav.absolutePath,
                                    "-map", "0:v:0",
                                    "-map", "1:a:0",
                                    "-c:v", "copy",
                                    "-c:a", "aac",
                                    "-shortest",
                                    outputPath
                                )
                                val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(command)
                                if (success) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(outputPath), "video/mp4")
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        } else {
                            // Save TTS directly as audio file
                            com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, tempWav, "audio/wav")
                            true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                
                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                
                if (outputSaved) {
                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                    AlertDialog.Builder(currentContext)
                        .setTitle(AppStrings.get(currentContext, R.string.msg_dialog_success_title))
                        .setMessage(AppStrings.get(currentContext, R.string.msg_dubbing_success))
                        .setPositiveButton(AppStrings.get(currentContext, R.string.btn_ok)) { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_183), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkModelStatus() {
        val currentContext = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
        if (modelFile != null && modelFile.exists()) {
            val sizeMb = (modelFile.length() / (1024 * 1024)).toInt()
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_piper_loaded, sizeMb)
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_piper_not_installed)
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle(AppStrings.get(currentActivity, R.string.dialog_download_title))
            .setMessage(AppStrings.get(currentActivity, R.string.dialog_download_message_piper))
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
        val currentContext = context ?: return
        binding.btnDownloadModel.visibility = View.GONE
        binding.pbModelDownload.visibility = View.VISIBLE
        binding.pbModelDownload.progress = 0
        binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.msg_download_starting)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val success = CloudConfigManager.downloadFeatureModel(
                currentContext.applicationContext,
                featureId,
                downloadUrl
            ) { percent ->
                if (_binding != null) {
                    binding.pbModelDownload.progress = percent
                    binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.msg_download_progress, percent)
                    if (percent % 5 == 0) {
                        try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                    }
                }
            }

            if (success) {
                try { Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_download_success), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            } else {
                try { Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_download_failed), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
            checkModelStatus()
        }
    }

    private fun splitText(text: String, limit: Int = 150): List<String> {
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            if (current.length + word.length + 1 > limit) {
                val str = current.toString().trim()
                if (str.isNotEmpty()) chunks.add(str)
                current = StringBuilder()
            }
            current.append(word).append(" ")
        }
        val str = current.toString().trim()
        if (str.isNotEmpty()) {
            chunks.add(str)
        }
        return chunks
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
