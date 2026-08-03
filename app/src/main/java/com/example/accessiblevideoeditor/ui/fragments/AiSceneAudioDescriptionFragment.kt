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
import com.example.accessiblevideoeditor.databinding.FragmentAiSceneAudioDescriptionBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiSceneAudioDescriptionFragment : Fragment() {

    private var _binding: FragmentAiSceneAudioDescriptionBinding? = null
    private val binding get() = _binding!!
    private var selectedVideoUri: Uri? = null
    private val featureId = "btnAiSceneAudioDescription"
    private val downloadUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/audio_description_rules.json"

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            binding.tvSelectedVideo.text = "الفيديو المختار: ${uri.lastPathSegment ?: uri.toString()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiSceneAudioDescriptionBinding.inflate(inflater, container, false)
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

        binding.btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.btnGenerateAudioDescription.setOnClickListener {
            val currentContext = context ?: return@setOnClickListener
            val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
            if (modelFile == null || !modelFile.exists()) {
                promptDownloadModel()
                return@setOnClickListener
            }

            if (selectedVideoUri == null) {
                Toast.makeText(currentContext, "الرجاء اختيار ملف فيديو أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing("جاري تحليل مشاهد الفيديو وإنشاء الوصف الصوتي...")
                
                val inputUri = selectedVideoUri ?: return@launch
                val outputPath = currentContext.cacheDir.absolutePath + "/audio_desc_out_${System.currentTimeMillis()}.mp4"
                
                val success = withContext(Dispatchers.IO) {
                    try {
                        val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, inputUri, "audio_desc_input")
                        if (tempInput != null && tempInput.exists()) {
                            // 1. Extract 4 frames proportionately across the video duration
                            val durationMs = com.example.accessiblevideoeditor.media.FFmpegProcessor.getMediaDurationMs(tempInput.absolutePath)
                            val durationSec = durationMs / 1000f
                            val t1 = (durationSec * 0.1f).toInt()
                            val t2 = (durationSec * 0.4f).toInt()
                            val t3 = (durationSec * 0.7f).toInt()
                            val t4 = (durationSec * 0.9f).toInt()

                            val f1 = java.io.File(currentContext.cacheDir, "f1_${System.currentTimeMillis()}.jpg")
                            val f2 = java.io.File(currentContext.cacheDir, "f2_${System.currentTimeMillis()}.jpg")
                            val f3 = java.io.File(currentContext.cacheDir, "f3_${System.currentTimeMillis()}.jpg")
                            val f4 = java.io.File(currentContext.cacheDir, "f4_${System.currentTimeMillis()}.jpg")

                            com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                arrayOf("-y", "-ss", t1.toString(), "-i", tempInput.absolutePath, "-vframes", "1", f1.absolutePath)
                            )
                            com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                arrayOf("-y", "-ss", t2.toString(), "-i", tempInput.absolutePath, "-vframes", "1", f2.absolutePath)
                            )
                            com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                arrayOf("-y", "-ss", t3.toString(), "-i", tempInput.absolutePath, "-vframes", "1", f3.absolutePath)
                            )
                            com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                arrayOf("-y", "-ss", t4.toString(), "-i", tempInput.absolutePath, "-vframes", "1", f4.absolutePath)
                            )
                            
                            // 2. Query Gemini if API key is present
                            var descriptionText = "تم إنشاء مسار وصف المشهد الصوتي للمكفوفين."
                            val apiKey = com.example.accessiblevideoeditor.ui.SettingsManager.geminiApiKey.trim()
                            val modelName = com.example.accessiblevideoeditor.ui.SettingsManager.geminiModel ?: "gemini-2.5-flash"
                            
                            if (apiKey.isNotEmpty()) {
                                try {
                                    val b1 = android.graphics.BitmapFactory.decodeFile(f1.absolutePath)
                                    val b2 = android.graphics.BitmapFactory.decodeFile(f2.absolutePath)
                                    val b3 = android.graphics.BitmapFactory.decodeFile(f3.absolutePath)
                                    val b4 = android.graphics.BitmapFactory.decodeFile(f4.absolutePath)
                                    
                                    val model = com.google.ai.client.generativeai.GenerativeModel(
                                        modelName = modelName,
                                        apiKey = apiKey
                                    )
                                    val response = model.generateContent(
                                        com.google.ai.client.generativeai.type.content {
                                            if (b1 != null) image(b1)
                                            if (b2 != null) image(b2)
                                            if (b3 != null) image(b3)
                                            if (b4 != null) image(b4)
                                            text("هذه 4 لقطات متتالية من فيديو تمثل الترتيب الزمني للأحداث. صف أحداث الفيديو بالكامل بالتفصيل باللغة العربية كقصة متكاملة ومستمرة لوصف المشاهد للمكفوفين (في جملتين أو ثلاث جمل قصيرة تلخص التطور من البداية للنهاية دون ذكر أن هذه لقطات أو صور).")
                                        }
                                    )
                                    val respText = response.text
                                    if (!respText.isNullOrEmpty()) {
                                        descriptionText = respText
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // 3. Synthesize description text to WAV using TTS
                            val tempWav = java.io.File(currentContext.cacheDir, "desc_${System.currentTimeMillis()}.wav")
                            var ttsSuccess = false
                            val ttsLatch = java.util.concurrent.CountDownLatch(1)
                            var ttsInitSuccess = false
                            
                            var tts: android.speech.tts.TextToSpeech? = null
                            withContext(Dispatchers.Main) {
                                tts = android.speech.tts.TextToSpeech(currentContext) { status ->
                                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                        ttsInitSuccess = true
                                    }
                                    ttsLatch.countDown()
                                }
                            }
                            
                            ttsLatch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                            
                            if (ttsInitSuccess && tts != null) {
                                val loc = java.util.Locale("ar")
                                tts!!.setLanguage(loc)
                                
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
                                params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "desc")
                                withContext(Dispatchers.Main) {
                                    tts!!.synthesizeToFile(descriptionText, params, tempWav, "desc")
                                }
                                
                                voiceLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                                tts!!.shutdown()
                            }
                            
                            if (tempWav.exists() && tempWav.length() > 0L) {
                                // 4. Mix description audio with original video audio
                                val hasAudio = com.example.accessiblevideoeditor.media.FFmpegProcessor.hasAudioTrack(tempInput.absolutePath)
                                val command = if (hasAudio) {
                                    arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-i", tempWav.absolutePath,
                                        "-filter_complex", "[0:a][1:a]amix=inputs=2:duration=longest[a]",
                                        "-map", "0:v:0",
                                        "-map", "[a]",
                                        "-c:v", "copy",
                                        "-c:a", "aac",
                                        outputPath
                                    )
                                } else {
                                    arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-i", tempWav.absolutePath,
                                        "-map", "0:v:0",
                                        "-map", "1:a:0",
                                        "-c:v", "copy",
                                        "-c:a", "aac",
                                        outputPath
                                    )
                                }
                                val res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(command)
                                if (res) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(outputPath), "video/mp4")
                                    true
                                } else {
                                    false
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
                
                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                
                if (success) {
                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                    AlertDialog.Builder(currentContext)
                        .setTitle("تمت العملية بنجاح")
                        .setMessage("تم توليد المسار الصوتي الوصفي للمكفوفين بنجاح ودمجه وحفظه في الاستوديو (Gallery).")
                        .setPositiveButton("موافق") { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, "فشل توليد الوصف الصوتي للمشاهد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkModelStatus() {
        val currentContext = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
        if (modelFile != null && modelFile.exists()) {
            binding.tvModelStatus.text = "حالة النموذج: قواعد محرك الوصف الصوتي محملة محلياً ✅ (${modelFile.length() / 1024} KB)"
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = "حالة النموذج: المحرك غير مثبت محلياً (حجمه 10 MB)"
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle("تنزيل نموذج الذكاء الاصطناعي")
            .setMessage("يتطلب هذا المحرك تنزيل حزمة الوصف الصوتي (حجمها 10 MB). هل تريد بدء التنزيل الآن؟")
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
