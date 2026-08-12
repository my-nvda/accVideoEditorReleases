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

    private val allFeatures = setOf(
        "btnVideoEditor",
        "btnImageEditor",
        "btnWatermark",
        "btnCreateBlankImage",
        "btnVideoTrimmer",
        "btnSmartCut",
        "btnAudioEditor",
        "btnAudioStudio",
        "btnAiAnalysis",
        "btnStt",
        "btnOcr",
        "btnFastConverter",
        "btnBoostVolume",
        "btnExtractAudio",
        "btnCompressVideo",
        "btnMergeVideos",
        "btnReverseMedia",
        "btnSlideshowMaker",
        "btnTickerText",
        "btnBatchProcess",
        "btnSpeedControl",
        "btnNoiseReduction",
        "btnBackgroundMusic",
        "btnAudioNormalization",
        "btnAiSceneInspector",
        "btnAiVoiceDubbing",
        "btnAudioStemSeparator",
        "btnAutoShortsCreator",
        "btnCinematicLutShaders",
        "btnAiSceneAudioDescription",
        "btnSubtitlesOcrSrt"
    )

    private val enabledFeatures = mutableSetOf<String>()

    private fun handleFeatureClick(featureId: String, actionId: Int, view: View) {
        val context = context ?: return
        if (enabledFeatures.contains(featureId)) {
            com.example.accessiblevideoeditor.telemetry.TelemetryManager.recordFeatureClick(context, featureId)
            navigateWithFocus(view, actionId)
        } else {
            val title = AppStrings.get(context, R.string.dialog_feature_disabled_title)
            val msg = AppStrings.get(context, R.string.dialog_feature_disabled_message)
            val btnOk = AppStrings.get(context, R.string.btn_close)
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(if (title.isNotBlank()) title else context.getString(R.string.dialog_feature_disabled_title))
                .setMessage(if (msg.isNotBlank()) msg else context.getString(R.string.dialog_feature_disabled_message))
                .setPositiveButton(if (btnOk.isNotBlank()) btnOk else context.getString(R.string.btn_close)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        enabledFeatures.clear()
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
            binding.btnVideoEditor.setOnClickListener { handleFeatureClick("btnVideoEditor", R.id.action_homeFragment_to_videoEditorFragment, it) }
            binding.btnVideoTrimmer.setOnClickListener { handleFeatureClick("btnVideoTrimmer", R.id.action_homeFragment_to_videoTrimmerFragment, it) }
            binding.btnSmartCut.setOnClickListener { handleFeatureClick("btnSmartCut", R.id.action_homeFragment_to_smartCutFragment, it) }
            binding.btnMergeVideos.setOnClickListener { handleFeatureClick("btnMergeVideos", R.id.action_homeFragment_to_mergeVideosFragment, it) }
            binding.btnReverseMedia.setOnClickListener { handleFeatureClick("btnReverseMedia", R.id.action_homeFragment_to_reverseMediaFragment, it) }
            binding.btnAudioEditor.setOnClickListener { handleFeatureClick("btnAudioEditor", R.id.action_homeFragment_to_audioEditorFragment, it) }
            binding.btnAudioStudio.setOnClickListener { handleFeatureClick("btnAudioStudio", R.id.action_homeFragment_to_audioStudioFragment, it) }
            binding.btnExtractAudio.setOnClickListener { handleFeatureClick("btnExtractAudio", R.id.action_homeFragment_to_extractAudioFragment, it) }
            binding.btnBoostVolume.setOnClickListener { handleFeatureClick("btnBoostVolume", R.id.action_homeFragment_to_boostVolumeFragment, it) }
            binding.btnCompressVideo.setOnClickListener { handleFeatureClick("btnCompressVideo", R.id.action_homeFragment_to_compressVideoFragment, it) }
            binding.btnImageEditor.setOnClickListener { handleFeatureClick("btnImageEditor", R.id.action_homeFragment_to_imageEditorFragment, it) }
            binding.btnWatermark.setOnClickListener { handleFeatureClick("btnWatermark", R.id.action_homeFragment_to_watermarkFragment, it) }
            binding.btnCreateBlankImage.setOnClickListener { handleFeatureClick("btnCreateBlankImage", R.id.action_homeFragment_to_createBlankImageFragment, it) }
            binding.btnSlideshowMaker.setOnClickListener { handleFeatureClick("btnSlideshowMaker", R.id.action_homeFragment_to_slideshowMakerFragment, it) }
            binding.btnTickerText.setOnClickListener { handleFeatureClick("btnTickerText", R.id.action_homeFragment_to_tickerTextFragment, it) }
            binding.btnAiAnalysis.setOnClickListener { handleFeatureClick("btnAiAnalysis", R.id.action_homeFragment_to_aiAnalysisFragment, it) }
            binding.btnStt.setOnClickListener { handleFeatureClick("btnStt", R.id.action_homeFragment_to_sttFragment, it) }
            binding.btnOcr.setOnClickListener { handleFeatureClick("btnOcr", R.id.action_homeFragment_to_ocrFragment, it) }
            binding.btnBatchProcess.setOnClickListener { handleFeatureClick("btnBatchProcess", R.id.action_homeFragment_to_batchProcessFragment, it) }
            binding.btnFastConverter.setOnClickListener { handleFeatureClick("btnFastConverter", R.id.action_homeFragment_to_fastConverterFragment, it) }
            binding.btnSpeedControl.setOnClickListener { handleFeatureClick("btnSpeedControl", R.id.action_homeFragment_to_speedControlFragment, it) }
            binding.btnNoiseReduction.setOnClickListener { handleFeatureClick("btnNoiseReduction", R.id.action_homeFragment_to_noiseReductionFragment, it) }
            binding.btnBackgroundMusic.setOnClickListener { handleFeatureClick("btnBackgroundMusic", R.id.action_homeFragment_to_backgroundMusicFragment, it) }
            binding.btnAudioNormalization.setOnClickListener { handleFeatureClick("btnAudioNormalization", R.id.action_homeFragment_to_audioNormalizationFragment, it) }
            binding.btnAiSceneInspector.setOnClickListener { handleFeatureClick("btnAiSceneInspector", R.id.action_homeFragment_to_aiSceneInspectorFragment, it) }

            // Dynamic Features clicks
            binding.btnAiVoiceDubbing.setOnClickListener { handleFeatureClick("btnAiVoiceDubbing", R.id.action_homeFragment_to_aiVoiceDubbingFragment, it) }
            binding.btnAudioStemSeparator.setOnClickListener { handleFeatureClick("btnAudioStemSeparator", R.id.action_homeFragment_to_audioStemSeparatorFragment, it) }
            binding.btnAutoShortsCreator.setOnClickListener { handleFeatureClick("btnAutoShortsCreator", R.id.action_homeFragment_to_autoShortsCreatorFragment, it) }
            binding.btnCinematicLutShaders.setOnClickListener { handleFeatureClick("btnCinematicLutShaders", R.id.action_homeFragment_to_cinematicLutShadersFragment, it) }
            binding.btnAiSceneAudioDescription.setOnClickListener { handleFeatureClick("btnAiSceneAudioDescription", R.id.action_homeFragment_to_aiSceneAudioDescriptionFragment, it) }
            binding.btnSubtitlesOcrSrt.setOnClickListener { handleFeatureClick("btnSubtitlesOcrSrt", R.id.action_homeFragment_to_subtitlesOcrSrtFragment, it) }

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
                        Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.msg_new_features_download), Toast.LENGTH_LONG).show()
                    }

                    if (result.isSuccess) {
                        enabledFeatures.clear()
                        val androidId = try {
                            android.provider.Settings.Secure.getString(
                                currentContext.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            ) ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                        for (id in allFeatures) {
                            val isGloballyDisabled = result.currentlyDisabledIds.contains(id)
                            val whitelist = result.whitelistedFeatures[id] ?: emptyList()
                            val isWhitelisted = androidId.isNotBlank() && whitelist.contains(androidId)
                            if (!isGloballyDisabled || isWhitelisted) {
                                enabledFeatures.add(id)
                            }
                        }
                    } else {
                        enabledFeatures.clear()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
