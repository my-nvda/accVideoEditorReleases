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
import com.example.accessiblevideoeditor.databinding.FragmentBackgroundMusicBinding
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

class BackgroundMusicFragment : Fragment() {

    private var _binding: FragmentBackgroundMusicBinding? = null
    private val binding get() = _binding!!

    private var mainUri: Uri? = null
    private var bgUri: Uri? = null

    private val volumeLevels = listOf(0.10f, 0.20f, 0.30f, 0.50f)

    private val mainPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            mainUri = uri
            binding.tvMainMediaFile.visibility = View.VISIBLE
            binding.tvMainMediaFile.text = AppStrings.get(requireContext(), R.string.string_16)
        }
    }

    private val bgPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            bgUri = uri
            binding.tvBgMusicFile.visibility = View.VISIBLE
            binding.tvBgMusicFile.text = AppStrings.get(requireContext(), R.string.string_16)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val volumeLabels = listOf(
            AppStrings.get(requireContext(), R.string.bg_vol_10),
            AppStrings.get(requireContext(), R.string.bg_vol_20),
            AppStrings.get(requireContext(), R.string.bg_vol_30),
            AppStrings.get(requireContext(), R.string.bg_vol_50)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, volumeLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBgVolume.adapter = adapter
        binding.spinnerBgVolume.setSelection(1) // default 20%

        binding.btnSelectMainMedia.setOnClickListener {
            mainPickerLauncher.launch("*/*")
        }

        binding.btnSelectBgMusic.setOnClickListener {
            bgPickerLauncher.launch("audio/*")
        }

        binding.btnApply.setOnClickListener {
            val main = mainUri
            val bg = bgUri
            if (main == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_47), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (bg == null) {
                Toast.makeText(requireContext(), AppStrings.get(requireContext(), R.string.string_19), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val vol = volumeLevels[binding.spinnerBgVolume.selectedItemPosition]
            processMixBackgroundMusic(main, bg, vol)
        }
    }

    private fun processMixBackgroundMusic(main: Uri, bg: Uri, volume: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempMain = MediaUtils.copyUriToTempFile(requireContext(), main, "main_media_${System.currentTimeMillis()}")
                val tempBg = MediaUtils.copyUriToTempFile(requireContext(), bg, "bg_music_${System.currentTimeMillis()}")
                
                if (tempMain != null && tempMain.exists() && tempBg != null && tempBg.exists()) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.startProcessing(AppStrings.get(requireContext(), R.string.title_background_music))
                    }
                    val isVideo = MediaUtils.isVideoFile(requireContext(), main)
                    val ext = if (isVideo) "mp4" else "mp3"
                    val outputPath = requireContext().cacheDir.absolutePath + "/bg_mix_${System.currentTimeMillis()}.$ext"

                    val duration = FFmpegProcessor.getMediaDurationMs(tempMain.absolutePath)

                    val filter = "[0:a]aresample=44100[a0];[1:a]volume=$volume,aresample=44100[a1];[a0][a1]amix=inputs=2:duration=first[outa]"

                    val commandArgs = mutableListOf<String>()
                    commandArgs.addAll(listOf("-y", "-i", tempMain.absolutePath, "-i", tempBg.absolutePath))

                    if (isVideo) {
                        commandArgs.addAll(listOf(
                            "-filter_complex", filter,
                            "-map", "0:v", "-map", "[outa]",
                            "-c:v", "copy",
                            "-c:a", "aac", "-b:a", "192k",
                            outputPath
                        ))
                    } else {
                        commandArgs.addAll(listOf(
                            "-filter_complex", filter,
                            "-map", "[outa]",
                            "-c:a", "libmp3lame", "-q:a", "2",
                            outputPath
                        ))
                    }

                    val success = FFmpegProcessor.executeWithProgress(commandArgs.toTypedArray(), totalDurationMs = if (duration > 0f) duration else null)
                    if (success) {
                        val mime = if (isVideo) "video/mp4" else "audio/mp3"
                        FileUtils.saveToGallery(requireContext(), File(outputPath), mime)
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
                    ProcessingManager.showError(e.message ?: "Background music mix failed")
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
