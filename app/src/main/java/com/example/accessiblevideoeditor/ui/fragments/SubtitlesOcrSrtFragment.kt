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
import com.example.accessiblevideoeditor.databinding.FragmentSubtitlesOcrSrtBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubtitlesOcrSrtFragment : Fragment() {

    private var _binding: FragmentSubtitlesOcrSrtBinding? = null
    private val binding get() = _binding!!
    private var selectedVideoUri: Uri? = null
    private var selectedVideoForBurnUri: Uri? = null
    private var selectedSrtUri: Uri? = null
    private val featureId = "btnSubtitlesOcrSrt"
    private val downloadUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/ara.traineddata"

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            binding.tvSelectedVideoOcr.text = AppStrings.get(requireContext(), R.string.label_selected_video, uri.lastPathSegment ?: uri.toString())
        }
    }

    private val selectVideoForBurnLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoForBurnUri = uri
            val name = uri.lastPathSegment ?: uri.toString()
            binding.tvSelectedVideoBurn.text = AppStrings.get(requireContext(), R.string.label_selected_video_burn, name)
        }
    }

    private val selectSrtLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedSrtUri = uri
            val name = uri.lastPathSegment ?: uri.toString()
            binding.tvSelectedSrt.text = AppStrings.get(requireContext(), R.string.label_selected_srt, name)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubtitlesOcrSrtBinding.inflate(inflater, container, false)
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

        binding.btnSelectVideoForOcr.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.btnSelectVideoForBurn.setOnClickListener {
            selectVideoForBurnLauncher.launch("video/*")
        }

        binding.btnSelectSrtFile.setOnClickListener {
            selectSrtLauncher.launch("*/*")
        }

        binding.btnBurnSubtitles.setOnClickListener {
            val currentContext = context ?: return@setOnClickListener
            val videoUri = selectedVideoForBurnUri
            val srtUri = selectedSrtUri
            
            if (videoUri == null || srtUri == null) {
                Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.toast_select_video_and_srt), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(AppStrings.get(currentContext, R.string.msg_burn_start))
                
                val success = withContext(Dispatchers.IO) {
                    try {
                        val tempVideo = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, videoUri, "temp_burn_vid")
                        val tempSrt = java.io.File(currentContext.cacheDir, "temp_burn_${System.currentTimeMillis()}.srt")
                        currentContext.contentResolver.openInputStream(srtUri).use { input ->
                            tempSrt.outputStream().use { output ->
                                input?.copyTo(output)
                            }
                        }
                        
                        if (tempVideo != null && tempVideo.exists() && tempSrt.exists()) {
                            val outputPath = currentContext.cacheDir.absolutePath + "/burned_sub_${System.currentTimeMillis()}.mp4"
                            val command = arrayOf(
                                "-y",
                                "-i", tempVideo.absolutePath,
                                "-vf", "subtitles='${tempSrt.absolutePath}'",
                                "-c:v", "mpeg4", "-q:v", "3",
                                "-c:a", "copy",
                                outputPath
                            )
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                
                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                
                if (success) {
                    com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                    AlertDialog.Builder(currentContext)
                        .setTitle(AppStrings.get(currentContext, R.string.msg_dialog_success_title))
                        .setMessage(AppStrings.get(currentContext, R.string.msg_burn_success_body))
                        .setPositiveButton(AppStrings.get(currentContext, R.string.btn_ok)) { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_burn_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnExtractSubtitles.setOnClickListener {
            val currentContext = context ?: return@setOnClickListener
            val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
            if (modelFile == null || !modelFile.exists()) {
                promptDownloadModel()
                return@setOnClickListener
            }

            if (selectedVideoUri == null) {
                Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.toast_select_video_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(AppStrings.get(currentContext, R.string.msg_ocr_scan_start))
                
                val inputUri = selectedVideoUri ?: return@launch
                val ocrProcessor = com.example.accessiblevideoeditor.media.OcrProcessor()
                
                val srtContent = withContext(Dispatchers.IO) {
                    try {
                        val text1 = ocrProcessor.extractTextFromVideoFrame(currentContext, inputUri, 2)
                        val text2 = ocrProcessor.extractTextFromVideoFrame(currentContext, inputUri, 5)
                        val text3 = ocrProcessor.extractTextFromVideoFrame(currentContext, inputUri, 8)
                        
                        val clean1 = if (text1.contains("API Key is missing") || text1.contains("Error")) AppStrings.get(currentContext, R.string.srt_placeholder_1) else text1
                        val clean2 = if (text2.contains("API Key is missing") || text2.contains("Error")) AppStrings.get(currentContext, R.string.srt_placeholder_2) else text2
                        val clean3 = if (text3.contains("API Key is missing") || text3.contains("Error")) AppStrings.get(currentContext, R.string.srt_placeholder_3) else text3
                        
                        """
                            1
                            00:00:00,500 --> 00:00:03,500
                            $clean1
                            
                            2
                            00:00:04,000 --> 00:00:07,000
                            $clean2
                            
                            3
                            00:00:07,500 --> 00:00:10,500
                            $clean3
                        """.trimIndent()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        """
                            1
                            00:00:01,000 --> 00:00:05,000
                            ${AppStrings.get(currentContext, R.string.srt_error_placeholder)}
                        """.trimIndent()
                    }
                }
                
                com.example.accessiblevideoeditor.ui.ProcessingManager.stopProcessing()
                com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
                
                try {
                    val srtFile = java.io.File(currentContext.cacheDir, "subtitles_${System.currentTimeMillis()}.srt")
                    srtFile.writeText(srtContent)
                    
                    val fileUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                        currentContext,
                        "${currentContext.packageName}.provider",
                        srtFile
                    )
                    
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    AlertDialog.Builder(currentContext)
                        .setTitle(AppStrings.get(currentContext, R.string.msg_dialog_success_title))
                        .setMessage(AppStrings.get(currentContext, R.string.msg_srt_success_body))
                        .setPositiveButton(AppStrings.get(currentContext, R.string.string_172)) { d, _ ->
                            d.dismiss()
                            currentContext.startActivity(android.content.Intent.createChooser(shareIntent, AppStrings.get(currentContext, R.string.chooser_save_share_srt)))
                        }
                        .setNegativeButton(AppStrings.get(currentContext, R.string.string_207)) { d, _ -> d.dismiss() }
                        .show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_srt_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkModelStatus() {
        val currentContext = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
        if (modelFile != null && modelFile.exists()) {
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_ocr_loaded, modelFile.length() / 1024)
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_ocr_not_installed)
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle(AppStrings.get(dialogContext, R.string.dialog_download_title))
            .setMessage(AppStrings.get(dialogContext, R.string.dialog_download_message_ocr))
            .setPositiveButton(AppStrings.get(dialogContext, R.string.btn_download_now)) { dialog, _ ->
                try { dialog.dismiss() } catch (_: Exception) {}
                startDownloadingModel()
            }
            .setNegativeButton(AppStrings.get(dialogContext, R.string.btn_later)) { dialog, _ ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
