package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.btnSelectVideos.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        binding.btnApply.setOnClickListener {
            if (selectedUris.size < 2) {
                Toast.makeText(requireContext(), "Please select at least two videos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            processVideos(selectedUris)
        }
    }

    private fun processVideos(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputs = uris.mapIndexedNotNull { index, uri ->
                    MediaUtils.copyUriToTempFile(requireContext(), uri, "merge_temp_${System.currentTimeMillis()}_$index.mp4")?.absolutePath
                }
                
                if (inputs.size > 1) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.string_92))
                    }
                    val outputPath = requireContext().cacheDir.absolutePath + "/merged_${System.currentTimeMillis()}.mp4"
                    
                    val hasAudioList = inputs.map { path ->
                        val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(path)
                        info.mediaInformation?.streams?.any { it.type == "audio" } ?: false
                    }

                    var totalMs = 0f
                    inputs.forEach { path ->
                        totalMs += FFmpegProcessor.getMediaDurationMs(path)
                    }

                    val filterParts = StringBuilder()
                    val concatParts = StringBuilder()
                    
                    inputs.forEachIndexed { index, path ->
                        val hasAudio = hasAudioList[index]
                        filterParts.append("[$index:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,setsar=1,fps=30[v$index];")
                        if (hasAudio) {
                            filterParts.append("[$index:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a$index];")
                        } else {
                            val durSec = FFmpegProcessor.getMediaDurationMs(path) / 1000f
                            val validDur = if (durSec > 0f) durSec else 5f
                            filterParts.append("anullsrc=r=44100:cl=stereo:d=$validDur[a$index];")
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
