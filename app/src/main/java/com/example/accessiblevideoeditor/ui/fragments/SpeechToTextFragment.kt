package com.example.accessiblevideoeditor.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
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
import com.example.accessiblevideoeditor.databinding.FragmentSpeechToTextBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeechToTextFragment : Fragment() {

    private var _binding: FragmentSpeechToTextBinding? = null
    private val binding get() = _binding!!

    private var selectedMediaUri: Uri? = null

    private val mediaPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.btnSelectAudio.text = AppStrings.get(requireContext(), R.string.string_16)
            binding.btnTranscribe.isEnabled = true
            binding.tilTranscribedText.visibility = View.GONE
            binding.btnCopyText.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeechToTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectAudio.setOnClickListener {
            mediaPickerLauncher.launch("*/*")
        }

        binding.btnTranscribe.setOnClickListener {
            transcribeAudio()
        }

        binding.btnCopyText.setOnClickListener {
            val text = binding.etTranscribedText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Transcribed Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_141), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBurnSubtitles.setOnClickListener {
            val text = binding.etTranscribedText.text.toString()
            val uri = selectedMediaUri
            if (text.isNotEmpty() && uri != null) {
                burnSubtitlesToVideo(uri, text)
            }
        }
    }

    private fun transcribeAudio() {
        val uri = selectedMediaUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val processMsg = AppStrings.get(requireContext(), R.string.string_111)
            ProcessingManager.startProcessing(processMsg, cancellable = true)
            ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
            
            var transcribedText = ""
            try {
                val apiKey = SettingsManager.geminiApiKey
                if (apiKey.isBlank()) {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_3)
                } else {
                    val model = GenerativeModel(
                        modelName = "gemini-2.5-flash",
                        apiKey = apiKey
                    )
                    val bytes = withContext(Dispatchers.IO) {
                        val inputStream = requireContext().contentResolver.openInputStream(uri)
                        inputStream?.readBytes() ?: ByteArray(0)
                    }
                    val mimeType = requireContext().contentResolver.getType(uri) ?: "audio/mpeg"
                    val inputContent = content {
                        blob(mimeType, bytes)
                        text(AppStrings.get(requireContext(), R.string.string_2))
                    }
                    transcribedText = withContext(Dispatchers.IO) {
                        model.generateContent(inputContent).text ?: AppStrings.get(requireContext(), R.string.string_71)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("503") || errorMsg.contains("high demand") || errorMsg.contains("Unexpected Response")) {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_228)
                } else {
                    transcribedText = AppStrings.get(requireContext(), R.string.string_73, errorMsg)
                }
            } finally {
                ProcessingManager.stopProcessing()
                binding.etTranscribedText.setText(transcribedText)
                binding.tilTranscribedText.visibility = View.VISIBLE
                binding.btnCopyText.visibility = View.VISIBLE
                binding.btnBurnSubtitles.visibility = View.VISIBLE
            }
        }
    }

    private fun burnSubtitlesToVideo(uri: Uri, text: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(requireContext(), uri, "sub_input_${System.currentTimeMillis()}")
                if (tempInput != null && tempInput.exists()) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.btn_burn_subtitles))
                    }
                    val isVideo = com.example.accessiblevideoeditor.media.MediaUtils.isVideoFile(requireContext(), uri)
                    val durationMs = com.example.accessiblevideoeditor.media.FFmpegProcessor.getMediaDurationMs(tempInput.absolutePath)
                    val durSec = if (durationMs > 0f) durationMs / 1000f else 10f

                    val cleanText = text.replace("'", "").replace("\"", "").replace("\n", " ")
                    val srtFile = java.io.File(requireContext().cacheDir, "temp_sub_${System.currentTimeMillis()}.srt")
                    srtFile.writeText("1\n00:00:00,000 --> 00:00:${String.format("%02d", durSec.toInt())},000\n$cleanText\n")

                    val outputPath = requireContext().cacheDir.absolutePath + "/subbed_video_${System.currentTimeMillis()}.mp4"

                    val commandArgs = mutableListOf<String>()
                    commandArgs.add("-y")

                    if (isVideo) {
                        commandArgs.addAll(listOf(
                            "-i", tempInput.absolutePath,
                            "-vf", "drawtext=text='$cleanText':fontcolor=white:fontsize=24:box=1:boxcolor=black@0.6:boxborderw=5:x=(w-text_w)/2:y=h-th-40",
                            "-c:a", "copy",
                            outputPath
                        ))
                    } else {
                        commandArgs.addAll(listOf(
                            "-f", "lavfi", "-i", "color=c=black:s=1280x720:d=$durSec",
                            "-i", tempInput.absolutePath,
                            "-vf", "drawtext=text='$cleanText':fontcolor=white:fontsize=28:box=1:boxcolor=black@0.6:boxborderw=5:x=(w-text_w)/2:y=(h-th)/2",
                            "-c:v", "mpeg4", "-q:v", "2",
                            "-c:a", "aac", "-b:a", "192k",
                            outputPath
                        ))
                    }

                    val success = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = if (durationMs > 0f) durationMs else null)
                    if (success) {
                        com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(requireContext(), java.io.File(outputPath), "video/mp4")
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    ProcessingManager.showError(e.message ?: "Subtitle burn-in failed")
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.stopProcessing()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
