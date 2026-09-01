package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentMergeVideosBinding
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

class MergeVideosFragment : Fragment() {

    private var _binding: FragmentMergeVideosBinding? = null
    private val binding get() = _binding!!

    private val selectedUris = mutableListOf<Uri>()

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedUris.clear()
        selectedUris.addAll(uris)
        updateVideoListUI()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMergeVideosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val transitionOptions = listOf(
            AppStrings.get(requireContext(), R.string.transition_none),
            AppStrings.get(requireContext(), R.string.transition_fade),
            AppStrings.get(requireContext(), R.string.transition_dissolve)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, transitionOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTransition.adapter = adapter

        binding.btnSelectVideos.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnApply.setOnClickListener {
            if (selectedUris.size < 2) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.toast_select_two_videos), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val transitionType = binding.spinnerTransition.selectedItemPosition
            processVideos(selectedUris, transitionType)
        }
    }

    private fun processVideos(uris: List<Uri>, transitionType: Int) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputs = uris.mapIndexedNotNull { index, uri ->
                    MediaUtils.copyUriToTempFile(appContext, uri, "merge_temp_${System.currentTimeMillis()}_$index.mp4")?.absolutePath
                }
                
                if (inputs.size > 1) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(appContext, R.string.string_92))
                        ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                    }
                    val outputPath = appContext.cacheDir.absolutePath + "/merged_${System.currentTimeMillis()}.mp4"
                    
                    // Storage check
                    var totalInputSize = 0L
                    inputs.forEach { path ->
                        totalInputSize += java.io.File(path).length()
                    }
                    if (!com.example.accessiblevideoeditor.utils.StorageUtils.isSpaceAvailable(appContext, (totalInputSize * 1.5).toLong())) {
                        withContext(Dispatchers.Main) {
                            com.example.accessiblevideoeditor.utils.StorageUtils.showLowSpaceWarning(requireContext(), view)
                            ProcessingManager.stopProcessing()
                        }
                        return@launch
                    }

                    val hasAudioList = inputs.map { path ->
                        val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(path)
                        info?.mediaInformation?.streams?.any { it.type == "audio" } ?: false
                    }

                    var totalMs = 0f
                    val durationsSec = inputs.map { path ->
                        val durMs = FFmpegProcessor.getMediaDurationMs(path)
                        totalMs += durMs
                        if (durMs > 0f) durMs / 1000f else 5f
                    }

                    val filterParts = StringBuilder()
                    val concatParts = StringBuilder()
                    
                    var portraitCount = 0
                    var landscapeCount = 0
                    inputs.forEach { path ->
                        val dims = FFmpegProcessor.getVideoDimensions(path)
                        if (dims.second > dims.first) {
                            portraitCount++
                        } else {
                            landscapeCount++
                        }
                    }
                    val targetWidth = if (portraitCount > landscapeCount) 720 else 1280
                    val targetHeight = if (portraitCount > landscapeCount) 1280 else 720

                    inputs.forEachIndexed { index, path ->
                        val hasAudio = hasAudioList[index]
                        val segDur = durationsSec[index]
                        val fadeFilter = if (transitionType > 0 && segDur > 1f) {
                            ",fade=t=in:st=0:d=0.5,fade=t=out:st=${segDur - 0.5f}:d=0.5"
                        } else ""

                        val dims = FFmpegProcessor.getVideoDimensions(path)
                        val isPortraitVideo = dims.second > dims.first
                        val isTargetPortrait = targetHeight > targetWidth
                        
                        if (isPortraitVideo == isTargetPortrait) {
                            filterParts.append("[$index:v]scale=$targetWidth:$targetHeight:force_original_aspect_ratio=increase,crop=$targetWidth:$targetHeight,setsar=1,fps=30$fadeFilter[v$index];")
                        } else {
                            filterParts.append("[$index:v]split[bg$index][fg$index];")
                            filterParts.append("[bg$index]scale=$targetWidth:$targetHeight:force_original_aspect_ratio=increase,crop=$targetWidth:$targetHeight,boxblur=20:2[bgblur$index];")
                            filterParts.append("[fg$index]scale=$targetWidth:$targetHeight:force_original_aspect_ratio=decrease[fgscale$index];")
                            filterParts.append("[bgblur$index][fgscale$index]overlay=(W-w)/2:(H-h)/2,setsar=1,fps=30$fadeFilter[v$index];")
                        }

                        if (hasAudio) {
                            filterParts.append("[$index:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a$index];")
                        } else {
                            filterParts.append("anullsrc=r=44100:cl=stereo:d=$segDur,aformat=sample_fmts=fltp:channel_layouts=stereo[a$index];")
                        }
                        concatParts.append("[v$index][a$index]")
                    }
                    
                    filterParts.append("${concatParts.toString()}concat=n=${inputs.size}:v=1:a=1[outv][outa]")

                    val commandArgs = mutableListOf<String>()
                    commandArgs.add("-y")
                    inputs.forEach { commandArgs.addAll(listOf("-i", it)) }
                    commandArgs.addAll(
                        listOf(
                            "-filter_complex", filterParts.toString(),
                            "-map", "[outv]", "-map", "[outa]",
                            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "18",
                            "-c:a", "aac", "-b:a", "192k",
                            outputPath
                        )
                    )
                    
                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = if (totalMs > 0f) totalMs else null)
                    if (success) {
                        val savedUri = FileUtils.saveToGallery(appContext, File(outputPath), "video/mp4")
                        withContext(Dispatchers.Main) {
                            if (isAdded && context != null) {
                                com.example.accessiblevideoeditor.ui.ShareDialogHelper.showSuccessShareDialog(
                                    requireContext(),
                                    savedUri,
                                    AppStrings.get(requireContext(), R.string.string_240),
                                    "video/mp4"
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                Toast.makeText(appContext, AppStrings.get(appContext, R.string.string_241), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    ProcessingManager.showError(e.message ?: "Unknown error occurred during merge")
                }
            } finally {
                withContext(NonCancellable) {
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

    private fun updateVideoListUI() {
        val context = context ?: return
        if (selectedUris.isNotEmpty()) {
            binding.tvVideosOrderHeader.visibility = View.VISIBLE
            binding.layoutVideoList.visibility = View.VISIBLE
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.tvSelectedCount.text = AppStrings.get(context, R.string.string_4, selectedUris.size)
        } else {
            binding.tvVideosOrderHeader.visibility = View.GONE
            binding.layoutVideoList.visibility = View.GONE
            binding.tvSelectedCount.visibility = View.GONE
        }

        binding.layoutVideoList.removeAllViews()

        selectedUris.forEachIndexed { index, uri ->
            val row = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val fileName = com.example.accessiblevideoeditor.utils.FileUtils.getFileName(context, uri) ?: "Video ${index + 1}"
            val tvName = android.widget.TextView(context).apply {
                text = "${index + 1}. $fileName"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textSize = 14f
                contentDescription = "الفيديو رقم ${index + 1}: $fileName"
            }
            row.addView(tvName)

            // Move Up button
            val btnUp = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "▲"
                contentDescription = "نقل الفيديو رقم ${index + 1} لأعلى"
                isEnabled = index > 0
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    if (index > 0) {
                        val temp = selectedUris[index]
                        selectedUris[index] = selectedUris[index - 1]
                        selectedUris[index - 1] = temp
                        updateVideoListUI()
                        announceAccessibility("تم نقل الفيديو رقم ${index + 1} لأعلى، الترتيب الجديد الآن هو ${index}")
                    }
                }
            }
            row.addView(btnUp)

            // Move Down button
            val btnDown = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "▼"
                contentDescription = "نقل الفيديو رقم ${index + 1} لأسفل"
                isEnabled = index < selectedUris.size - 1
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    if (index < selectedUris.size - 1) {
                        val temp = selectedUris[index]
                        selectedUris[index] = selectedUris[index + 1]
                        selectedUris[index + 1] = temp
                        updateVideoListUI()
                        announceAccessibility("تم نقل الفيديو رقم ${index + 1} لأسفل، الترتيب الجديد الآن هو ${index + 2}")
                    }
                }
            }
            row.addView(btnDown)

            // Preview button
            val btnPreview = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "👁️"
                contentDescription = "معاينة واستعراض الفيديو رقم ${index + 1}: $fileName"
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    previewVideo(uri)
                }
            }
            row.addView(btnPreview)

            // Remove button
            val btnRemove = com.google.android.material.button.MaterialButton(context, null, com.google.android.material.R.style.Widget_Material3_Button_IconButton).apply {
                text = "✕"
                contentDescription = "حذف وإزالة الفيديو رقم ${index + 1} من القائمة"
                minWidth = 48
                minHeight = 48
                setOnClickListener {
                    selectedUris.removeAt(index)
                    updateVideoListUI()
                    announceAccessibility("تمت إزالة الفيديو رقم ${index + 1} من القائمة")
                }
            }
            row.addView(btnRemove)

            binding.layoutVideoList.addView(row)
        }
    }

    private fun announceAccessibility(message: String) {
        view?.announceForAccessibility(message)
    }

    private fun previewVideo(uri: Uri) {
        val currentContext = context ?: return
        val dialogView = LayoutInflater.from(currentContext).inflate(R.layout.dialog_video_preview, null)
        val playerView = dialogView.findViewById<androidx.media3.ui.PlayerView>(R.id.playerView)

        val player = androidx.media3.exoplayer.ExoPlayer.Builder(currentContext).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        playerView.player = player

        androidx.appcompat.app.AlertDialog.Builder(currentContext)
            .setTitle(AppStrings.get(currentContext, R.string.btn_preview_combined) ?: "معاينة الفيديو")
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_close)) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                player.release()
            }
            .create()
            .show()
    }
}

