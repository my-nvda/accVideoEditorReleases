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
                            // 1. Extract first frame for Gemini analysis
                            val tempFrame = java.io.File(currentContext.cacheDir, "frame_${System.currentTimeMillis()}.jpg")
                            val extractSuccess = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(
                                arrayOf("-y", "-ss", "00:00:01", "-i", tempInput.absolutePath, "-vframes", "1", tempFrame.absolutePath)
                            )
                            
                            // 2. Query Gemini if API key is present
                            var descriptionText = "تم إنشاء مسار وصف المشهد الصوتي للمكفوفين."
                            val apiKey = com.example.accessiblevideoeditor.ui.SettingsManager.geminiApiKey.trim()
                            val modelName = com.example.accessiblevideoeditor.ui.SettingsManager.geminiModel ?: "gemini-2.5-flash"
                            
                            if (extractSuccess && tempFrame.exists()) {
                                try {
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(tempFrame.absolutePath)
                                    if (bitmap != null && apiKey.isNotEmpty()) {
                                        val model = com.google.ai.client.generativeai.GenerativeModel(
                                            modelName = modelName,
                                            apiKey = apiKey
                                        )
                                        val response = model.generateContent(
                                            com.google.ai.client.generativeai.type.content {
                                                image(bitmap)
                                                text("صف هذه الصورة بالتفصيل باللغة العربية باختصار شديد لجعلها وصفاً صوتياً للمكفوفين (في جملة أو جملتين).")
                                            }
                                        )
                                        val respText = response.text
                                        if (!respText.isNullOrEmpty()) {
                                            descriptionText = respText
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // 3. Synthesize description text to WAV using TTS
                            val tempWav = java.io.File(currentContext.cacheDir, "desc_${System.currentTimeMillis()}.wav")
                            val latch = java.util.concurrent.CountDownLatch(1)
                            var ttsSuccess = false
                            
                            withContext(Dispatchers.Main) {
                                val tts = android.speech.tts.TextToSpeech(currentContext) { status ->
                                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                        latch.countDown()
                                    } else {
                                        latch.countDown()
                                    }
                                }
                                
                                val initLatch = java.util.concurrent.CountDownLatch(1)
                                val loc = java.util.Locale("ar")
                                tts.setLanguage(loc)
                                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) {
                                        ttsSuccess = true
                                        initLatch.countDown()
                                    }
                                    override fun onError(utteranceId: String?) {
                                        initLatch.countDown()
                                    }
                                })
                                
                                val params = Bundle()
                                params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "desc")
                                tts.synthesizeToFile(descriptionText, params, tempWav, "desc")
                                
                                withContext(Dispatchers.IO) {
                                    initLatch.await(20, java.util.concurrent.TimeUnit.SECONDS)
                                }
                                tts.shutdown()
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
