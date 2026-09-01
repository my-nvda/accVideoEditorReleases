package com.example.accessiblevideoeditor.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentCinematicLutShadersBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CinematicLutShadersFragment : Fragment() {

    private var _binding: FragmentCinematicLutShadersBinding? = null
    private val binding get() = _binding!!
    private var selectedMediaUri: Uri? = null

    // Preset keys, names, descriptions, and filters
    private val presets = listOf(
        Preset("default", R.string.preset_lut_default, R.string.desc_lut_default, "curves=preset=vintage"),
        Preset("warm", R.string.preset_lut_warm, R.string.desc_lut_warm, "colorbalance=rh=0.2:gh=0.1:bh=-0.1"),
        Preset("hollywood", R.string.preset_lut_hollywood, R.string.desc_lut_hollywood, "colorbalance=rh=0.15:rm=-0.1:bh=0.15:bm=-0.15"),
        Preset("cool", R.string.preset_lut_cool, R.string.desc_lut_cool, "colorbalance=rh=-0.1:gh=-0.05:bh=0.15:rm=-0.1:gm=-0.05:bm=0.15"),
        Preset("bw", R.string.preset_lut_bw, R.string.desc_lut_bw, "format=gray"),
        Preset("pastel", R.string.preset_lut_vintage_pastel, R.string.desc_lut_vintage_pastel, "eq=saturation=0.5:contrast=0.9")
    )

    data class Preset(
        val key: String,
        val nameResId: Int,
        val descResId: Int,
        val filterString: String
    )

    private val selectMediaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            binding.tvSelectedMedia.text = AppStrings.get(requireContext(), R.string.label_selected_file_path, uri.lastPathSegment ?: uri.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCinematicLutShadersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            try { findNavController().navigateUp() } catch (_: Exception) {}
        }

        binding.btnSelectMedia.setOnClickListener {
            selectMediaLauncher.launch("*/*")
        }

        // Setup preset spinner
        val context = requireContext()
        val presetNames = presets.map { AppStrings.get(context, it.nameResId) }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, presetNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerPreset.adapter = adapter
        binding.spinnerPreset.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = presets[position]
                val desc = AppStrings.get(context, preset.descResId)
                binding.tvPresetDescription.text = desc
                // Announce details to TalkBack
                binding.tvPresetDescription.announceForAccessibility(desc)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnApplyLut.setOnClickListener {
            val currentContext = context
            if (selectedMediaUri == null) {
                Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.toast_select_video_or_image), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedPreset = presets[binding.spinnerPreset.selectedItemPosition]

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                ProcessingManager.startProcessing(AppStrings.get(currentContext, R.string.msg_lut_apply_start))
                
                val inputUri = selectedMediaUri ?: return@launch
                val isVideo = MediaUtils.isVideoFile(currentContext, inputUri)
                val ext = if (isVideo) "mp4" else "jpg"
                val outputPath = currentContext.cacheDir.absolutePath + "/cinematic_out_${System.currentTimeMillis()}.$ext"
                
                val success = withContext(Dispatchers.IO) {
                    try {
                        val tempInput = MediaUtils.copyUriToTempFile(currentContext, inputUri, "cinematic_input")
                        if (tempInput != null && tempInput.exists()) {
                            val filter = selectedPreset.filterString
                            val command = if (isVideo) {
                                val hasAudio = FFmpegProcessor.hasAudioTrack(tempInput.absolutePath)
                                if (hasAudio) {
                                    arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-vf", filter,
                                        "-c:v", "libx264",
                                        "-preset", "ultrafast",
                                        "-crf", "23",
                                        "-c:a", "copy",
                                        outputPath
                                    )
                                } else {
                                    arrayOf(
                                        "-y",
                                        "-i", tempInput.absolutePath,
                                        "-vf", filter,
                                        "-c:v", "libx264",
                                        "-preset", "ultrafast",
                                        "-crf", "23",
                                        outputPath
                                    )
                                }
                            } else {
                                arrayOf(
                                    "-y",
                                    "-i", tempInput.absolutePath,
                                    "-vf", filter,
                                    outputPath
                                )
                            }
                            val res = FFmpegProcessor.executeWithProgress(command)
                            if (res) {
                                val mime = if (isVideo) "video/mp4" else "image/jpeg"
                                FileUtils.saveToGallery(currentContext, File(outputPath), mime)
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
                
                ProcessingManager.stopProcessing()
                
                if (success) {
                    SoundManager.playSuccess()
                    AlertDialog.Builder(currentContext)
                        .setTitle(AppStrings.get(currentContext, R.string.msg_dialog_success_title))
                        .setMessage(AppStrings.get(currentContext, R.string.msg_lut_success))
                        .setPositiveButton(AppStrings.get(currentContext, R.string.btn_ok)) { d, _ -> d.dismiss() }
                        .show()
                } else {
                    Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_lut_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ProcessingManager.sharedMediaUri?.let { uri ->
            selectedMediaUri = uri
            ProcessingManager.sharedMediaUri = null
            binding.tvSelectedMedia.text = AppStrings.get(requireContext(), R.string.label_selected_file_path, uri.lastPathSegment ?: uri.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
