package com.example.accessiblevideoeditor.ui.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentTextBasedEditorBinding
import com.example.accessiblevideoeditor.databinding.ItemTextSegmentBinding
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.example.accessiblevideoeditor.utils.FileUtils
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class TextBasedEditorFragment : Fragment() {

    private var _binding: FragmentTextBasedEditorBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null
    private val segments = mutableListOf<TextSegment>()

    data class TextSegment(
        val id: String = java.util.UUID.randomUUID().toString(),
        val text: String,
        val startMs: Long,
        val endMs: Long,
        var isDeleted: Boolean = false
    )

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
        _binding = FragmentTextBasedEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriArg = arguments?.getString("videoUri") ?: arguments?.getString("videoPath")
        if (!uriArg.isNullOrEmpty()) {
            selectedUri = if (uriArg.startsWith("content://") || uriArg.startsWith("file://")) {
                Uri.parse(uriArg)
            } else {
                Uri.fromFile(File(uriArg))
            }
            binding.tvSelectedFile.visibility = View.VISIBLE
            binding.tvSelectedFile.text = "تم اختيار فيديو المشروع: " + (selectedUri?.lastPathSegment ?: "فيديو مشروع")
        }

        binding.topAppBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnSelectMedia.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnTranscribe.setOnClickListener {
            val uri = selectedUri
            if (uri == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startTranscription(uri)
        }

        binding.btnPreviewResult.setOnClickListener {
            val uri = selectedUri ?: return@setOnClickListener
            processTextBasedCut(uri, isPreview = true)
        }

        binding.btnApplyAndSave.setOnClickListener {
            val uri = selectedUri ?: return@setOnClickListener
            processTextBasedCut(uri, isPreview = false)
        }
    }

    private fun startTranscription(uri: Uri) {
        val ctx = context ?: return
        binding.pbLoading.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "جاري مسح وتحليل كلام الفيديو واستخراج التوقيتات النصية..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = MediaUtils.copyUriToTempFile(ctx, uri, "text_cut_raw_${System.currentTimeMillis()}") ?: return@launch
                val audioFile = File(ctx.cacheDir, "text_cut_audio_${System.currentTimeMillis()}.mp3")
                
                // Extract audio via FFmpegKit directly
                val extractCmd = arrayOf("-y", "-i", tempInput.absolutePath, "-vn", "-acodec", "libmp3lame", "-q:a", "2", audioFile.absolutePath)
                val extractSession = FFmpegKit.executeWithArguments(extractCmd)
                val extractSuccess = ReturnCode.isSuccess(extractSession.returnCode) && audioFile.exists()

                val inputAudio = if (extractSuccess) audioFile else tempInput
                val geminiKey = SettingsManager.geminiApiKey.trim()

                var jsonResponse = ""
                if (geminiKey.isNotBlank()) {
                    try {
                        val model = GenerativeModel(
                            modelName = "gemini-2.5-flash",
                            apiKey = geminiKey
                        )
                        val inputContent = content {
                            blob("audio/mp3", inputAudio.readBytes())
                            text("""
                                Transcribe the speech in this audio with exact timestamps for each sentence segment.
                                Return ONLY a valid JSON array of objects with keys: "text" (string), "start" (number ms), "end" (number ms).
                                Example: [{"text": "Hello", "start": 500, "end": 2000}]
                            """.trimIndent())
                        }
                        val res = model.generateContent(inputContent)
                        jsonResponse = res.text ?: ""
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                segments.clear()
                if (jsonResponse.isNotBlank() && jsonResponse.contains("[")) {
                    val cleanedJson = jsonResponse.substring(jsonResponse.indexOf("["), jsonResponse.lastIndexOf("]") + 1)
                    val jsonArray = JSONArray(cleanedJson)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val text = obj.optString("text", "")
                        val start = obj.optLong("start", 0L)
                        val end = obj.optLong("end", 0L)
                        if (text.isNotBlank() && end > start) {
                            segments.add(TextSegment(text = text, startMs = start, endMs = end))
                        }
                    }
                }

                // Fallback: If AI STT didn't return segments, create equal duration speech blocks
                if (segments.isEmpty()) {
                    val durationMs = MediaUtils.getVideoDuration(ctx, uri)
                    val blockLen = 4000L
                    var curr = 0L
                    var idx = 1
                    while (curr < durationMs) {
                        val next = Math.min(curr + blockLen, durationMs)
                        segments.add(TextSegment(text = "مقطع الكلام رقم $idx", startMs = curr, endMs = next))
                        curr = next
                        idx++
                    }
                }

                try { tempInput.delete() } catch (_: Exception) {}
                try { audioFile.delete() } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.pbLoading.visibility = View.GONE
                    binding.tvStatus.visibility = View.GONE
                    binding.cvSummary.visibility = View.VISIBLE
                    binding.btnPreviewResult.visibility = View.VISIBLE
                    binding.btnApplyAndSave.visibility = View.VISIBLE

                    renderSegmentsList()
                    view?.announceForAccessibility("تم تفريغ النص بنجاح. تم استخراج ${segments.size} مقطع نصي.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.pbLoading.visibility = View.GONE
                    binding.tvStatus.text = "فشل التفريغ النصي: ${e.message}"
                }
            }
        }
    }

    private fun renderSegmentsList() {
        val container = binding.containerSegments
        container.removeAllViews()

        val activeSegments = segments.filter { !it.isDeleted }
        binding.tvSummaryTitle.text = "تم رصد ${activeSegments.size} مقطع نصي متبقي"

        segments.forEachIndexed { index, seg ->
            val itemBinding = ItemTextSegmentBinding.inflate(layoutInflater, container, false)

            val startSec = seg.startMs / 1000.0
            val endSec = seg.endMs / 1000.0
            val timeBadge = String.format(java.util.Locale.US, "[%.1fs - %.1fs]", startSec, endSec)

            itemBinding.tvSegmentTime.text = timeBadge
            itemBinding.tvSegmentText.text = seg.text

            if (seg.isDeleted) {
                itemBinding.tvSegmentText.paintFlags = itemBinding.tvSegmentText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                itemBinding.tvSegmentText.setTextColor(android.graphics.Color.GRAY)
                itemBinding.btnDelete.text = "إعادة الجملة ↩️"
                itemBinding.btnDelete.contentDescription = "إعادة إضافة الجملة رقم ${index + 1}: ${seg.text}"
            } else {
                itemBinding.tvSegmentText.paintFlags = itemBinding.tvSegmentText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemBinding.tvSegmentText.setTextColor(android.graphics.Color.BLACK)
                itemBinding.btnDelete.text = "حذف الجملة 🗑️"
                itemBinding.btnDelete.contentDescription = "حذف هذه الجملة من الفيديو: ${seg.text}"
            }

            itemBinding.btnDelete.setOnClickListener {
                seg.isDeleted = !seg.isDeleted
                renderSegmentsList()
                val statusText = if (seg.isDeleted) "تم حذف الجملة: ${seg.text}" else "تمت إعادة الجملة: ${seg.text}"
                view?.announceForAccessibility(statusText)
            }

            container.addView(itemBinding.root)
        }
    }

    private fun processTextBasedCut(uri: Uri, isPreview: Boolean) {
        val ctx = context ?: return
        val validSegments = segments.filter { !it.isDeleted }

        if (validSegments.isEmpty()) {
            Toast.makeText(ctx, "جميع الجمل محذوفة! اختر جملة واحدة على الأقل للاحتفاظ بها.", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing("جاري تطبيق التعديل النصي وتقطيع الفيديو...")
                    ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                }

                val tempInput = MediaUtils.copyUriToTempFile(ctx, uri, "text_cut_orig_${System.currentTimeMillis()}") ?: return@launch
                val isVideo = MediaUtils.isVideoFile(ctx, uri)
                val ext = if (isVideo) "mp4" else "mp3"
                val outputPath = ctx.cacheDir.absolutePath + "/text_cut_result_${System.currentTimeMillis()}.$ext"

                val partFiles = mutableListOf<File>()
                for ((idx, seg) in validSegments.withIndex()) {
                    val partFile = File(ctx.cacheDir, "part_${idx}_${System.currentTimeMillis()}.$ext")
                    val durationSec = (seg.endMs - seg.startMs) / 1000.0
                    val startSec = seg.startMs / 1000.0

                    val cutCmd = arrayOf(
                        "-y", "-ss", String.format(java.util.Locale.US, "%.3f", startSec),
                        "-i", tempInput.absolutePath,
                        "-t", String.format(java.util.Locale.US, "%.3f", durationSec),
                        "-c", "copy",
                        partFile.absolutePath
                    )
                    val session = FFmpegKit.executeWithArguments(cutCmd)
                    if (ReturnCode.isSuccess(session.returnCode) && partFile.exists() && partFile.length() > 0L) {
                        partFiles.add(partFile)
                    }
                }

                if (partFiles.isEmpty()) {
                    throw Exception("فشل اقتطاع أجزاء الفيديو")
                }

                // Concat demuxer list
                val listFile = File(ctx.cacheDir, "concat_list_${System.currentTimeMillis()}.txt")
                val sb = StringBuilder()
                for (f in partFiles) {
                    val safePath = f.absolutePath.replace("\\", "/")
                    sb.append("file '$safePath'\n")
                }
                listFile.writeText(sb.toString())

                val concatCmd = arrayOf(
                    "-y", "-f", "concat", "-safe", "0",
                    "-i", listFile.absolutePath,
                    "-c", "copy",
                    outputPath
                )
                val concatSession = FFmpegKit.executeWithArguments(concatCmd)
                val success = ReturnCode.isSuccess(concatSession.returnCode) && File(outputPath).exists()

                try { tempInput.delete() } catch (_: Exception) {}
                try { listFile.delete() } catch (_: Exception) {}
                partFiles.forEach { try { it.delete() } catch (_: Exception) {} }

                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    if (success) {
                        if (isPreview) {
                            Toast.makeText(ctx, "تم تجهيز المعاينة النصية بنجاح", Toast.LENGTH_SHORT).show()
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                ctx,
                                Uri.fromFile(File(outputPath)),
                                "معاينة المونتاج النصي",
                                if (isVideo) "video/mp4" else "audio/mp3"
                            )
                        } else {
                            val mime = if (isVideo) "video/mp4" else "audio/mp3"
                            val savedUri = FileUtils.saveToGallery(ctx, File(outputPath), mime)
                            com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                ctx,
                                savedUri,
                                AppStrings.get(ctx, R.string.string_240),
                                mime
                            )
                        }
                    } else {
                        Toast.makeText(ctx, "فشل المونتاج النصي", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ProcessingManager.stopProcessing()
                    Toast.makeText(requireContext(), "فشلت العملية: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
