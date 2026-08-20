package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentUserStatsBinding
import com.example.accessiblevideoeditor.telemetry.TelemetryManager
import com.example.accessiblevideoeditor.ui.AppStrings

class UserStatsFragment : Fragment() {

    private var _binding: FragmentUserStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnClearStats.setOnClickListener {
            val safeCtx = context ?: return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(safeCtx)
                .setTitle("تأكيد إعادة التعيين")
                .setMessage("هل أنت متأكد من رغبتك في حذف وإعادة تصفير جميع إحصائيات استخدام الأدوات محلياً؟")
                .setPositiveButton("نعم، احذف") { dialog, _ ->
                    dialog.dismiss()
                    TelemetryManager.clearUsageStatistics(safeCtx)
                    loadLocalStatistics()
                    android.widget.Toast.makeText(safeCtx, "تم إعادة تصفير الإحصائيات بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        loadLocalStatistics()
    }

    private fun loadLocalStatistics() {
        val context = context ?: return
        binding.containerStats.removeAllViews()

        val stats = TelemetryManager.getUsageStatistics(context)
        if (stats.length() == 0) {
            binding.tvEmptyPlaceholder.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyPlaceholder.visibility = View.GONE

        val allFeaturesList = listOf(
            "btnVideoEditor" to R.string.string_112,
            "btnImageEditor" to R.string.string_128,
            "btnWatermark" to R.string.string_74,
            "btnCreateBlankImage" to R.string.string_271,
            "btnVideoTrimmer" to R.string.string_94,
            "btnSmartCut" to R.string.string_45,
            "btnAudioEditor" to R.string.string_102,
            "btnAudioStudio" to R.string.string_55,
            "btnAiAnalysis" to R.string.string_31,
            "btnStt" to R.string.string_63,
            "btnOcr" to R.string.string_20,
            "btnFastConverter" to R.string.string_59,
            "btnBoostVolume" to R.string.string_86,
            "btnExtractAudio" to R.string.string_41,
            "btnCompressVideo" to R.string.string_125,
            "btnMergeVideos" to R.string.string_75,
            "btnReverseMedia" to R.string.string_68,
            "btnSlideshowMaker" to R.string.string_80,
            "btnTickerText" to R.string.string_52,
            "btnBatchProcess" to R.string.string_32,
            "btnSpeedControl" to R.string.btn_speed_control,
            "btnNoiseReduction" to R.string.btn_noise_reduction,
            "btnBackgroundMusic" to R.string.btn_background_music,
            "btnAudioNormalization" to R.string.btn_audio_normalization,
            "btnAiSceneInspector" to R.string.btn_ai_scene_inspector,
            "btnAiVoiceDubbing" to R.string.btn_ai_voice_dubbing,
            "btnAudioStemSeparator" to R.string.btn_audio_stem_separator,
            "btnAutoShortsCreator" to R.string.btn_auto_shorts_creator,
            "btnCinematicLutShaders" to R.string.btn_cinematic_lut_shaders,
            "btnAiSceneAudioDescription" to R.string.btn_ai_scene_audio_description,
            "btnSubtitlesOcrSrt" to R.string.btn_subtitles_ocr_srt
        )

        val mappedStats = mutableListOf<Triple<String, String, Int>>()
        for ((featureId, resId) in allFeaturesList) {
            val count = stats.optInt(featureId, 0)
            if (count > 0) {
                val label = AppStrings.get(context, resId)
                mappedStats.add(Triple(featureId, label, count))
            }
        }

        mappedStats.sortByDescending { it.third }

        for (item in mappedStats) {
            val row = LayoutInflater.from(context).inflate(R.layout.item_user_stat_row, binding.containerStats, false)
            val tvName = row.findViewById<TextView>(R.id.tvFeatureName)
            val tvCount = row.findViewById<TextView>(R.id.tvFeatureCount)

            tvName.text = item.second
            tvCount.text = "${item.third} مرة"

            row.contentDescription = "أداة ${item.second} تم استخدامها ${item.third} مرة"
            row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            row.isFocusable = true

            binding.containerStats.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
