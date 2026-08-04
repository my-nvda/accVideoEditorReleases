package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentHomeBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.CloudConfigManager
import com.example.accessiblevideoeditor.updater.BeepUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onResume() {
        super.onResume()
        try {
            val cachedDisabled = CloudConfigManager.getCachedDisabledFeatures(requireContext())
            updateFeatureVisibilities(cachedDisabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        checkRemoteCloudConfig()
    }

    private fun updateButtonTexts() {
        val context = context ?: return
        try {
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
            
            // Dynamic Cloud Features
            binding.btnAiVoiceDubbing.text = AppStrings.get(context, R.string.btn_ai_voice_dubbing)
            binding.btnAudioStemSeparator.text = AppStrings.get(context, R.string.btn_audio_stem_separator)
            binding.btnAutoShortsCreator.text = AppStrings.get(context, R.string.btn_auto_shorts_creator)
            binding.btnCinematicLutShaders.text = AppStrings.get(context, R.string.btn_cinematic_lut_shaders)
            binding.btnAiSceneAudioDescription.text = AppStrings.get(context, R.string.btn_ai_scene_audio_description)
            binding.btnSubtitlesOcrSrt.text = AppStrings.get(context, R.string.btn_subtitles_ocr_srt)

            binding.btnHistory.text = AppStrings.get(context, R.string.string_116)
            binding.topAppBar.menu?.findItem(R.id.action_settings)?.title = AppStrings.get(context, R.string.string_133)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            binding.topAppBar.title = "Accessible Video Editor"
            
            // Setup Buttons with AppStrings
            updateButtonTexts()
            
            // Settings menu
            binding.topAppBar.inflateMenu(R.menu.home_menu)
            try {
                binding.topAppBar.menu?.findItem(R.id.action_settings)?.title = AppStrings.get(requireContext(), R.string.string_133)
            } catch (e: Exception) {}
            binding.topAppBar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_settings -> {
                        navigateWithFocus(binding.topAppBar, R.id.action_homeFragment_to_settingsFragment)
                        true
                    }
                    else -> false
                }
            }
            
            // Main Navigation clicks
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

            // Dynamic Features clicks
            binding.btnAiVoiceDubbing.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_aiVoiceDubbingFragment) }
            binding.btnAudioStemSeparator.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_audioStemSeparatorFragment) }
            binding.btnAutoShortsCreator.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_autoShortsCreatorFragment) }
            binding.btnCinematicLutShaders.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_cinematicLutShadersFragment) }
            binding.btnAiSceneAudioDescription.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_aiSceneAudioDescriptionFragment) }
            binding.btnSubtitlesOcrSrt.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_subtitlesOcrSrtFragment) }

            binding.btnHistory.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_historyFragment) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkRemoteCloudConfig() {
        val currentContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = CloudConfigManager.checkCloudConfig(currentContext)
                withContext(Dispatchers.Main) {
                    val activeActivity = activity ?: return@withContext
                    if (!isAdded || activeActivity.isFinishing || activeActivity.isDestroyed) return@withContext

                    if (CloudConfigManager.stringsUpdated) {
                        CloudConfigManager.stringsUpdated = false
                        activeActivity.recreate()
                        return@withContext
                    }

                    if (result.pendingDownloads.isNotEmpty()) {
                        Toast.makeText(currentContext, "هناك ميزات إضافية جديدة متوفرة للتنزيل!", Toast.LENGTH_LONG).show()
                    }

                    // Apply feature visibility based on cloud_config.json
                    updateFeatureVisibilities(result.currentlyDisabledIds)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateFeatureVisibilities(disabledIds: Set<String>) {
        if (_binding == null) return
        try {
            binding.btnVideoEditor.visibility = if (disabledIds.contains("btnVideoEditor")) View.GONE else View.VISIBLE
            binding.btnImageEditor.visibility = if (disabledIds.contains("btnImageEditor")) View.GONE else View.VISIBLE
            binding.btnWatermark.visibility = if (disabledIds.contains("btnWatermark")) View.GONE else View.VISIBLE
            binding.btnCreateBlankImage.visibility = if (disabledIds.contains("btnCreateBlankImage")) View.GONE else View.VISIBLE
            binding.btnVideoTrimmer.visibility = if (disabledIds.contains("btnVideoTrimmer")) View.GONE else View.VISIBLE
            binding.btnSmartCut.visibility = if (disabledIds.contains("btnSmartCut")) View.GONE else View.VISIBLE
            binding.btnAudioEditor.visibility = if (disabledIds.contains("btnAudioEditor")) View.GONE else View.VISIBLE
            binding.btnAudioStudio.visibility = if (disabledIds.contains("btnAudioStudio")) View.GONE else View.VISIBLE
            binding.btnAiAnalysis.visibility = if (disabledIds.contains("btnAiAnalysis")) View.GONE else View.VISIBLE
            binding.btnStt.visibility = if (disabledIds.contains("btnStt")) View.GONE else View.VISIBLE
            binding.btnOcr.visibility = if (disabledIds.contains("btnOcr")) View.GONE else View.VISIBLE
            binding.btnFastConverter.visibility = if (disabledIds.contains("btnFastConverter")) View.GONE else View.VISIBLE
            binding.btnBoostVolume.visibility = if (disabledIds.contains("btnBoostVolume")) View.GONE else View.VISIBLE
            binding.btnExtractAudio.visibility = if (disabledIds.contains("btnExtractAudio")) View.GONE else View.VISIBLE
            binding.btnCompressVideo.visibility = if (disabledIds.contains("btnCompressVideo")) View.GONE else View.VISIBLE
            binding.btnMergeVideos.visibility = if (disabledIds.contains("btnMergeVideos")) View.GONE else View.VISIBLE
            binding.btnReverseMedia.visibility = if (disabledIds.contains("btnReverseMedia")) View.GONE else View.VISIBLE
            binding.btnSlideshowMaker.visibility = if (disabledIds.contains("btnSlideshowMaker")) View.GONE else View.VISIBLE
            binding.btnTickerText.visibility = if (disabledIds.contains("btnTickerText")) View.GONE else View.VISIBLE
            binding.btnBatchProcess.visibility = if (disabledIds.contains("btnBatchProcess")) View.GONE else View.VISIBLE
            binding.btnSpeedControl.visibility = if (disabledIds.contains("btnSpeedControl")) View.GONE else View.VISIBLE
            binding.btnNoiseReduction.visibility = if (disabledIds.contains("btnNoiseReduction")) View.GONE else View.VISIBLE
            binding.btnBackgroundMusic.visibility = if (disabledIds.contains("btnBackgroundMusic")) View.GONE else View.VISIBLE
            binding.btnAudioNormalization.visibility = if (disabledIds.contains("btnAudioNormalization")) View.GONE else View.VISIBLE
            binding.btnAiSceneInspector.visibility = if (disabledIds.contains("btnAiSceneInspector")) View.GONE else View.VISIBLE
            
            // Dynamic Features visibilities
            binding.btnAiVoiceDubbing.visibility = if (disabledIds.contains("btnAiVoiceDubbing")) View.GONE else View.VISIBLE
            binding.btnAudioStemSeparator.visibility = if (disabledIds.contains("btnAudioStemSeparator")) View.GONE else View.VISIBLE
            binding.btnAutoShortsCreator.visibility = if (disabledIds.contains("btnAutoShortsCreator")) View.GONE else View.VISIBLE
            binding.btnCinematicLutShaders.visibility = if (disabledIds.contains("btnCinematicLutShaders")) View.GONE else View.VISIBLE
            binding.btnAiSceneAudioDescription.visibility = if (disabledIds.contains("btnAiSceneAudioDescription")) View.GONE else View.VISIBLE
            binding.btnSubtitlesOcrSrt.visibility = if (disabledIds.contains("btnSubtitlesOcrSrt")) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun navigateWithFocus(v: View, actionId: Int) {
        try {
            val activeActivity = activity ?: return
            if (!isAdded || activeActivity.isFinishing || activeActivity.isDestroyed) return
            (activeActivity as? com.example.accessiblevideoeditor.MainActivity)?.saveLastFocusedViewId("HomeFragment", v.id)
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.homeFragment) {
                navController.navigate(actionId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
