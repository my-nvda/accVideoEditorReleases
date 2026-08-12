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

    private var selectedUris: List<Uri> = emptyList()

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedUris = uris
        if (uris.isNotEmpty()) {
            binding.tvSelectedCount.visibility = View.VISIBLE
            binding.tvSelectedCount.text = AppStrings.get(requireContext(), R.string.string_4, uris.size)
        } else {
            binding.tvSelectedCount.visibility = View.GONE
        }
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputs = uris.mapIndexedNotNull { index, uri ->
                    MediaUtils.copyUriToTempFile(requireContext(), uri, "merge_temp_${System.currentTimeMillis()}_$index.mp4")?.absolutePath
                }
                
                if (inputs.size > 1) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_92))
                        ProcessingManager.updateJob(coroutineContext[kotlinx.coroutines.Job])
                    }
                    val outputPath = requireContext().cacheDir.absolutePath + "/merged_${System.currentTimeMillis()}.mp4"
                    
                    val hasAudioList = inputs.map { path ->
                        val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(path)
                        info.mediaInformation?.streams?.any { it.type == "audio" } ?: false
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
                            filterParts.append("[bg$index]scale=$targetWidth:$targetHeight:force_original_aspect_ratio=increase,crop=$targetWidth:$targetHeight,boxblur=20:20[bgblur$index];")
                            filterParts.append("[fg$index]scale=$targetWidth:$targetHeight:force_original_aspect_ratio=decrease[fgscale$index];")
                            filterParts.append("[bgblur$index][fgscale$index]overlay=(W-w)/2:(H-h)/2,setsar=1,fps=30$fadeFilter[v$index];")
                        }

                        if (hasAudio) {
                            filterParts.append("[$index:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a$index];")
                        } else {
                            filterParts.append("anullsrc=r=44100:cl=stereo:d=$segDur[a$index];")
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
                            "-c:v", "mpeg4", "-q:v", "2",
                            "-c:a", "aac", "-b:a", "192k",
                            outputPath
                        )
                    )
                    
                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = if (totalMs > 0f) totalMs else null)
                    if (success) {
                        FileUtils.saveToGallery(requireContext(), File(outputPath), "video/mp4")
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
}

