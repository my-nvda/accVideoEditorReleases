package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
            binding.btnAiVoiceDubbing.text = "دبلجة وتوليد الصوت بالذكاء الاصطناعي"
            binding.btnAudioStemSeparator.text = "عازل ومحلل الآلات والموسيقى"
            binding.btnAutoShortsCreator.text = "مولد الفيديوهات القصيرة والقوالب"
            binding.btnCinematicLutShaders.text = "حزمة الفلاتر والتأثيرات السينمائية"
            binding.btnAiSceneAudioDescription.text = "الوصف الصوتي التفاعلي للمكفوفين"
            binding.btnSubtitlesOcrSrt.text = "مستخرج وقارئ الترجمات SRT"

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
            binding.btnAiVoiceDubbing.setOnClickListener { handleDynamicFeatureClick("btnAiVoiceDubbing", "دبلجة وتوليد الصوت بالذكاء الاصطناعي", 63.2) }
            binding.btnAudioStemSeparator.setOnClickListener { handleDynamicFeatureClick("btnAudioStemSeparator", "عازل ومحلل الآلات والموسيقى", 48.6) }
            binding.btnAutoShortsCreator.setOnClickListener { handleDynamicFeatureClick("btnAutoShortsCreator", "مولد الفيديوهات القصيرة والقوالب", 8.0) }
            binding.btnCinematicLutShaders.setOnClickListener { handleDynamicFeatureClick("btnCinematicLutShaders", "حزمة الفلاتر والتأثيرات السينمائية", 5.0) }
            binding.btnAiSceneAudioDescription.setOnClickListener { handleDynamicFeatureClick("btnAiSceneAudioDescription", "الوصف الصوتي التفاعلي للمكفوفين", 10.0) }
            binding.btnSubtitlesOcrSrt.setOnClickListener { handleDynamicFeatureClick("btnSubtitlesOcrSrt", "مستخرج وقارئ الترجمات SRT", 1.43) }

            binding.btnHistory.setOnClickListener { navigateWithFocus(it, R.id.action_homeFragment_to_historyFragment) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleDynamicFeatureClick(featureId: String, title: String, sizeMb: Double) {
        try {
            val activeActivity = activity ?: return
            if (!isAdded || activeActivity.isFinishing || activeActivity.isDestroyed) return

            CloudConfigManager.init(activeActivity)
            val isDownloaded = CloudConfigManager.isFeatureDownloaded(featureId)

            if (isDownloaded) {
                launchDynamicFeature(featureId)
            } else {
                AlertDialog.Builder(activeActivity)
                    .setTitle("تنزيل وتفعيل ميزة $title")
                    .setMessage("هذه الميزة سحابية وحجمها تقريباً ($sizeMb ميجابايت).\n\nهل تريد تنزيلها وتفعيلها الآن على تطبيقك؟")
                    .setPositiveButton("تنزيل وتفعيل الآن") { dialog, _ ->
                        dialog.dismiss()
                        downloadAndActivateFeature(featureId, title)
                    }
                    .setNegativeButton("لاحقاً") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchDynamicFeature(featureId: String) {
        val viewRef = when (featureId) {
            "btnAiVoiceDubbing" -> binding.btnAiVoiceDubbing
            "btnAudioStemSeparator" -> binding.btnAudioStemSeparator
            "btnAutoShortsCreator" -> binding.btnAutoShortsCreator
            "btnCinematicLutShaders" -> binding.btnCinematicLutShaders
            "btnAiSceneAudioDescription" -> binding.btnAiSceneAudioDescription
            "btnSubtitlesOcrSrt" -> binding.btnSubtitlesOcrSrt
            else -> binding.btnVideoEditor
        }

        val actionId = when (featureId) {
            "btnAiVoiceDubbing" -> R.id.action_homeFragment_to_aiVoiceDubbingFragment
            "btnAudioStemSeparator" -> R.id.action_homeFragment_to_audioStemSeparatorFragment
            "btnAutoShortsCreator" -> R.id.action_homeFragment_to_autoShortsCreatorFragment
            "btnCinematicLutShaders" -> R.id.action_homeFragment_to_cinematicLutShadersFragment
            "btnAiSceneAudioDescription" -> R.id.action_homeFragment_to_aiSceneAudioDescriptionFragment
            "btnSubtitlesOcrSrt" -> R.id.action_homeFragment_to_subtitlesOcrSrtFragment
            else -> R.id.action_homeFragment_to_videoEditorFragment
        }

        navigateWithFocus(viewRef, actionId)
    }

    private fun downloadAndActivateFeature(featureId: String, title: String) {
        val activeActivity = activity ?: return
        if (!isAdded || activeActivity.isFinishing || activeActivity.isDestroyed) return

        val downloadUrl = when (featureId) {
            "btnAiVoiceDubbing" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/voice_dubbing.onnx"
            "btnAudioStemSeparator" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/vocal_separator_model.tar.bz2"
            "btnSubtitlesOcrSrt" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/ara.traineddata"
            "btnAutoShortsCreator" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/shorts_templates.json"
            "btnCinematicLutShaders" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/cinematic_luts.json"
            "btnAiSceneAudioDescription" -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/audio_description_rules.json"
            else -> "https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/models/voice_dubbing.onnx"
        }

        var progressDialog: AlertDialog? = null
        var progressTv: android.widget.TextView? = null

        try {
            progressTv = android.widget.TextView(activeActivity).apply {
                text = "جاري الاتصال بالسيرفر وتنزيل نموذج ميزة $title..."
                setPadding(40, 30, 40, 30)
                textSize = 15f
            }

            progressDialog = AlertDialog.Builder(activeActivity)
                .setTitle("تنزيل الموديل السحابي الحقيقي")
                .setView(progressTv)
                .setCancelable(false)
                .create()

            progressDialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            try {
                val success = CloudConfigManager.downloadFeatureModel(
                    activeActivity.applicationContext,
                    featureId,
                    downloadUrl
                ) { percent ->
                    try {
                        progressTv?.text = "جاري تنزيل نموذج ميزة $title...\nالتقدم: $percent%"
                        if (percent % 20 == 0) {
                            try { BeepUtils.playProgressBeep(percent) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }

                try {
                    if (progressDialog != null && progressDialog.isShowing) {
                        progressDialog.dismiss()
                    }
                } catch (_: Exception) {}

                val currentAct = activity ?: return@launch
                if (!isAdded || currentAct.isFinishing || currentAct.isDestroyed) return@launch

                CloudConfigManager.markFeatureAsDownloaded(featureId)
                Toast.makeText(currentAct, "تم تنزيل وتفعيل ميزة $title بنجاح!", Toast.LENGTH_SHORT).show()
                launchDynamicFeature(featureId)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    if (progressDialog != null && progressDialog.isShowing) {
                        progressDialog.dismiss()
                    }
                } catch (_: Exception) {}

                val currentAct = activity ?: return@launch
                if (isAdded && !currentAct.isFinishing && !currentAct.isDestroyed) {
                    CloudConfigManager.markFeatureAsDownloaded(featureId)
                    launchDynamicFeature(featureId)
                }
            }
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
