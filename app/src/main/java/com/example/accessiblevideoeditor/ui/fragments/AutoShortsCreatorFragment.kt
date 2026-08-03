package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentAutoShortsCreatorBinding
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoShortsCreatorFragment : Fragment() {

    private var _binding: FragmentAutoShortsCreatorBinding? = null
    private val binding get() = _binding!!
    private var selectedVideoUri: Uri? = null
    private val featureId = "btnAutoShortsCreator"
    private val downloadUrl = "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/shorts_templates.json"

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
        _binding = FragmentAutoShortsCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            try { findNavController().navigateUp() } catch (_: Exception) {}
        }

        checkModelStatus()

        val presets = arrayOf(
            "تيك توك / ترويض (15 ثانية)",
            "حالات واتساب (30 ثانية)",
            "شورتس يوتيوب (60 ثانية)",
            "ريلز إنستغرام (90 ثانية)",
            "تخصيص مدة معينة..."
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDurationPreset.adapter = adapter
        
        binding.spinnerDurationPreset.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 4) {
                    binding.tilCustomDuration.visibility = View.VISIBLE
                } else {
                    binding.tilCustomDuration.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnDownloadModel.setOnClickListener {
            promptDownloadModel()
        }

        binding.btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.btnGenerateShorts.setOnClickListener {
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

            val selectedPosition = binding.spinnerDurationPreset.selectedItemPosition
            val splitDurationSec = when (selectedPosition) {
                0 -> 15
                1 -> 30
                2 -> 60
                3 -> 90
                else -> {
                    val input = binding.etCustomDuration.text?.toString()?.toIntOrNull() ?: 15
                    if (input <= 0) 15 else input
                }
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing("جاري تحويل الفيديو إلى مقاطع قصيرة 9:16...")
                
                val inputUri = selectedVideoUri ?: return@launch
                
                val success = withContext(Dispatchers.IO) {
                    try {
                        val tempInput = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToTempFile(currentContext, inputUri, "shorts_input")
                        if (tempInput != null && tempInput.exists()) {
                            val durationMs = com.example.accessiblevideoeditor.media.FFmpegProcessor.getMediaDurationMs(tempInput.absolutePath)
                            val durationSec = if (durationMs > 0) (durationMs / 1000.0) else splitDurationSec.toDouble()
                            val segmentsCount = Math.max(1, Math.ceil(durationSec / splitDurationSec.toDouble()).toInt())
                            
                            val hasAudio = com.example.accessiblevideoeditor.media.FFmpegProcessor.hasAudioTrack(tempInput.absolutePath)
                            var allSaved = true
                            
                            for (i in 0 until segmentsCount) {
                                val startSec = i * splitDurationSec
                                val outputPathSegment = currentContext.cacheDir.absolutePath + "/shorts_out_${i}_${System.currentTimeMillis()}.mp4"
                                
                                val command = if (hasAudio) {
                                    arrayOf(
                                        "-y",
                                        "-ss", startSec.toString(),
                                        "-t", splitDurationSec.toString(),
                                        "-i", tempInput.absolutePath,
                                        "-vf", "crop='floor(min(iw\\,ih*9/16)/2)*2':'floor(min(ih\\,iw*16/9)/2)*2'",
                                        "-c:v", "mpeg4",
                                        "-q:v", "2",
                                        "-c:a", "aac",
                                        outputPathSegment
                                    )
                                } else {
                                    arrayOf(
                                        "-y",
                                        "-ss", startSec.toString(),
                                        "-t", splitDurationSec.toString(),
                                        "-i", tempInput.absolutePath,
                                        "-vf", "crop='floor(min(iw\\,ih*9/16)/2)*2':'floor(min(ih\\,iw*16/9)/2)*2'",
                                        "-c:v", "mpeg4",
                                        "-q:v", "2",
                                        outputPathSegment
                                    )
                                }
                                
                                withContext(Dispatchers.Main) {
                                    com.example.accessiblevideoeditor.ui.ProcessingManager.startProcessing(AppStrings.get(currentContext, R.string.msg_shorts_processing, i + 1, segmentsCount))
                                }
                                
                                val res = com.example.accessiblevideoeditor.media.FFmpegProcessor.executeWithProgress(command)
                                if (res) {
                                    com.example.accessiblevideoeditor.utils.FileUtils.saveToGallery(currentContext, java.io.File(outputPathSegment), "video/mp4")
                                } else {
                                    allSaved = false
                                }
                            }
                            allSaved
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
                        .setMessage(AppStrings.get(currentContext, R.string.msg_shorts_success))
                        .setPositiveButton(AppStrings.get(currentContext, R.string.btn_ok)) { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_shorts_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkModelStatus() {
        val currentContext = context ?: return
        val modelFile = CloudConfigManager.getDownloadedModelFile(currentContext, featureId)
        if (modelFile != null && modelFile.exists()) {
            val sizeKb = (modelFile.length() / 1024).toInt()
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_shorts_loaded, sizeKb)
            binding.btnDownloadModel.visibility = View.GONE
            binding.pbModelDownload.visibility = View.GONE
        } else {
            binding.tvModelStatus.text = AppStrings.get(currentContext, R.string.model_status_shorts_not_installed)
            binding.btnDownloadModel.visibility = View.VISIBLE
            binding.pbModelDownload.visibility = View.GONE
        }
    }

    private fun promptDownloadModel() {
        val currentActivity = activity ?: return
        val dialogContext = android.view.ContextThemeWrapper(currentActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        AlertDialog.Builder(dialogContext)
            .setTitle(AppStrings.get(currentActivity, R.string.dialog_download_title))
            .setMessage(AppStrings.get(currentActivity, R.string.dialog_download_message_shorts))
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
                    if (percent % 20 == 0) {
                        try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                    }
                }
            }

            if (success) {
                try { Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_download_success), Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
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
