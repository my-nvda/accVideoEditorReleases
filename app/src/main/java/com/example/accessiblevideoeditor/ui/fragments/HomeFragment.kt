package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentHomeBinding
import com.example.accessiblevideoeditor.R
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val context = requireContext()
        
        binding.topAppBar.title = "Accessible Video Editor"
        
        // Setup Buttons with AppStrings
        binding.btnVideoEditor.text = AppStrings.get(context, R.string.string_112)
        binding.btnImageEditor.text = AppStrings.get(context, R.string.string_128)
        binding.btnWatermark.text = AppStrings.get(context, R.string.string_74)
        binding.btnCreateBlankImage.text = AppStrings.get(context, R.string.string_271)
        binding.btnVideoTrimmer.text = AppStrings.get(context, R.string.string_94)
        binding.btnSmartCut.text = AppStrings.get(context, R.string.string_45)
        binding.btnAudioEditor.text = AppStrings.get(context, R.string.string_102)
        binding.btnAudioStudio.text = AppStrings.get(context, R.string.string_55)
        binding.btnAiAnalysis.text = AppStrings.get(context, R.string.string_31)
        binding.btnStt.text = AppStrings.get(context, R.string.string_63)
        binding.btnOcr.text = AppStrings.get(context, R.string.string_20)
        binding.btnFastConverter.text = AppStrings.get(context, R.string.string_59)
        binding.btnBoostVolume.text = AppStrings.get(context, R.string.string_86)
        binding.btnExtractAudio.text = AppStrings.get(context, R.string.string_41)
        binding.btnCompressVideo.text = AppStrings.get(context, R.string.string_125)
        binding.btnMergeVideos.text = AppStrings.get(context, R.string.string_75)
        binding.btnReverseMedia.text = AppStrings.get(context, R.string.string_68)
        binding.btnSlideshowMaker.text = AppStrings.get(context, R.string.string_80)
        binding.btnTickerText.text = AppStrings.get(context, R.string.string_52)
        binding.btnBatchProcess.text = AppStrings.get(context, R.string.string_32)
        binding.btnSpeedControl.text = AppStrings.get(context, R.string.btn_speed_control)
        binding.btnNoiseReduction.text = AppStrings.get(context, R.string.btn_noise_reduction)
        binding.btnBackgroundMusic.text = AppStrings.get(context, R.string.btn_background_music)
        binding.btnAudioNormalization.text = AppStrings.get(context, R.string.btn_audio_normalization)
        binding.btnAiSceneInspector.text = AppStrings.get(context, R.string.btn_ai_scene_inspector)
        binding.btnHistory.text = AppStrings.get(context, R.string.string_116)
        
        // Settings menu
        binding.topAppBar.inflateMenu(R.menu.home_menu)
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
                    true
                }
                else -> false
            }
        }
        
        // Navigation clicks
        binding.btnVideoEditor.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_videoEditorFragment) }
        binding.btnVideoTrimmer.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_videoTrimmerFragment) }
        binding.btnSmartCut.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_smartCutFragment) }
        binding.btnMergeVideos.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_mergeVideosFragment) }
        binding.btnReverseMedia.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_reverseMediaFragment) }
        binding.btnAudioEditor.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_audioEditorFragment) }
        binding.btnAudioStudio.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_audioStudioFragment) }
        binding.btnExtractAudio.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_extractAudioFragment) }
        binding.btnBoostVolume.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_boostVolumeFragment) }
        binding.btnCompressVideo.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_compressVideoFragment) }
        binding.btnImageEditor.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_imageEditorFragment) }
        binding.btnWatermark.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_watermarkFragment) }
        binding.btnCreateBlankImage.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_createBlankImageFragment) }
        binding.btnSlideshowMaker.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_slideshowMakerFragment) }
        binding.btnTickerText.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_tickerTextFragment) }
        binding.btnAiAnalysis.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_aiAnalysisFragment) }
        binding.btnStt.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_sttFragment) }
        binding.btnOcr.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_ocrFragment) }
        binding.btnBatchProcess.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_batchProcessFragment) }
        binding.btnFastConverter.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_fastConverterFragment) }
        binding.btnSpeedControl.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_speedControlFragment) }
        binding.btnNoiseReduction.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_noiseReductionFragment) }
        binding.btnBackgroundMusic.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_backgroundMusicFragment) }
        binding.btnAudioNormalization.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_audioNormalizationFragment) }
        binding.btnAiSceneInspector.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_aiSceneInspectorFragment) }
        binding.btnHistory.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_historyFragment) }

        checkRemoteCloudConfig()
    }

    private fun checkRemoteCloudConfig() {
        lifecycleScope.launch {
            val result = com.example.accessiblevideoeditor.ui.CloudConfigManager.checkCloudConfig(requireContext())
            
            // 1. Dynamic Silent Hide for ANY Disabled Feature (ALL 25+ buttons supported)
            for (disabledId in result.currentlyDisabledIds) {
                val resId = resources.getIdentifier(disabledId, "id", requireContext().packageName)
                if (resId != 0) {
                    binding.root.findViewById<View>(resId)?.visibility = View.GONE
                }
            }

            // 2. Gentle Notification when a Feature is Re-enabled
            if (result.reEnabledFeatureIds.isNotEmpty()) {
                Toast.makeText(requireContext(), "تم إعادة تفعيل الميزات المتوقفة بنجاح", Toast.LENGTH_SHORT).show()
            }

            // 3. Explicit User-Driven Download Prompt for New Features & Updates
            for (item in result.pendingDownloads) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(item.title)
                    .setMessage("${item.description}\n\nهل ترغب في تنزيل وتفعيل هذه الميزة الآن؟")
                    .setPositiveButton("تنزيل الميزة الآن") { dialog, _ ->
                        com.example.accessiblevideoeditor.ui.CloudConfigManager.markFeatureAsDownloaded(item.id)
                        Toast.makeText(requireContext(), "تم تنزيل وتفعيل الميزة بنجاح!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("لاحقاً") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
                break
            }
        }
    }

    private fun navigateWithFocus(v: View, actionId: Int) {
        (activity as? com.example.accessiblevideoeditor.MainActivity)?.saveLastFocusedViewId("HomeFragment", v.id)
        findNavController().navigate(actionId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

