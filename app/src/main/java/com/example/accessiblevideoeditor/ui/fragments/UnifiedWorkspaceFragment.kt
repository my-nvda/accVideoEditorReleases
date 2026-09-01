package com.example.accessiblevideoeditor.ui.fragments

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.data.TextOverlayConfig
import com.example.accessiblevideoeditor.data.UnifiedProjectManager
import com.example.accessiblevideoeditor.data.UnifiedProjectModel
import com.example.accessiblevideoeditor.databinding.FragmentUnifiedWorkspaceBinding
import com.example.accessiblevideoeditor.media.FFmpegPipelineBuilder
import com.example.accessiblevideoeditor.media.FFmpegProcessor
import com.example.accessiblevideoeditor.media.MediaUtils
import com.example.accessiblevideoeditor.media.OnlineAudioModel
import com.example.accessiblevideoeditor.media.SpeechToTextProcessor
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.example.accessiblevideoeditor.ui.ShareDialogHelper
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UnifiedWorkspaceFragment : Fragment() {

    private var _binding: FragmentUnifiedWorkspaceBinding? = null
    private val binding get() = _binding!!
    private var exoPlayer: ExoPlayer? = null
    private var project: UnifiedProjectModel? = null
    private var projectId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updatePlayerProgressRunnable = object : Runnable {
        override fun run() {
            val safeBinding = _binding ?: return
            exoPlayer?.let { player ->
                safeBinding.textOverlayView.updatePosition(player.currentPosition)
            }
            handler.postDelayed(this, 100)
        }
    }

    private val watermarkPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val currentContext = context ?: return@registerForActivityResult
        if (uri != null) {
            project?.let {
                val ext = currentContext.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "png"
                val copiedFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToProjectsDir(
                    currentContext, uri, "proj_watermark_${System.currentTimeMillis()}.$ext"
                )
                it.watermarkImagePath = copiedFile?.absolutePath ?: uri.toString()
                it.watermarkEnabled = true
                UnifiedProjectManager.saveProject(currentContext, it)
                announceAccessibility("Watermark image selected successfully")
                updateUI()
            }
        }
    }

    private val backgroundImagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val currentContext = context ?: return@registerForActivityResult
        if (uri != null) {
            project?.let { proj ->
                try {
                    val ext = currentContext.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "png"
                    val copiedFile = com.example.accessiblevideoeditor.media.MediaUtils.copyUriToProjectsDir(
                        currentContext, uri, "proj_bg_${System.currentTimeMillis()}.$ext"
                    )
                    if (copiedFile != null) {
                        proj.backgroundRemovalCustomBgPath = copiedFile.absolutePath
                        UnifiedProjectManager.saveProject(currentContext, proj)
                        announceAccessibility("Background image chosen successfully")
                        android.widget.Toast.makeText(currentContext, "تم اختيار صورة الخلفية بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnifiedWorkspaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        projectId = arguments?.getString("projectId")
        val currentContext = context ?: return

        if (projectId.isNullOrBlank()) {
            Toast.makeText(currentContext, getString(R.string.msg_error_project_id_missing), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        project = UnifiedProjectManager.getProject(currentContext, projectId!!)
        if (project == null) {
            Toast.makeText(currentContext, getString(R.string.msg_error_project_not_found), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val prefs = currentContext.getSharedPreferences("CameraPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("last_open_project_id", projectId)
            putBoolean("last_project_clean_exit", false)
            apply()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndExit()
            }
        })

        binding.topAppBar.title = project!!.name
        binding.topAppBar.setNavigationOnClickListener {
            saveAndExit()
        }

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(currentContext).build()
        binding.playerView.player = exoPlayer
        binding.playerView.controllerShowTimeoutMs = 0
        binding.playerView.showController()

        val videoUri = Uri.parse(project!!.videoPath)
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    handler.post(updatePlayerProgressRunnable)
                } else {
                    handler.removeCallbacks(updatePlayerProgressRunnable)
                }
            }
        })

        setupListeners()
        updateUI()
    }

    private fun setupListeners() {
        val currentContext = context ?: return
        val proj = project ?: return

        binding.cbTrim.setOnCheckedChangeListener { _, isChecked ->
            proj.trimEnabled = isChecked
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility(if (isChecked) "Video trim layer enabled" else "Video trim layer disabled")
            updateUI()
        }

        binding.cbText.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && proj.textOverlays.isEmpty()) {
                binding.cbText.isChecked = false
                showTextOverlaysDialog()
            } else {
                updateUI()
            }
        }

        binding.cbBackgroundRemoval.setOnCheckedChangeListener { _, isChecked ->
            proj.backgroundRemovalEnabled = isChecked
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility(if (isChecked) "Background removal enabled" else "Background removal disabled")
            updateUI()
        }

        binding.cbKeyframe.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && proj.keyframePreset == "none") {
                proj.keyframePreset = "zoom_in_center"
            } else if (!isChecked) {
                proj.keyframePreset = "none"
            }
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility(if (isChecked) "Motion keyframe preset enabled: ${proj.keyframePreset}" else "Motion keyframe preset disabled")
            updateUI()
        }

        binding.cbColorFilter.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                proj.colorFilterPreset = "none"
                proj.brightness = 0.0f
                proj.contrast = 1.0f
                proj.saturation = 1.0f
            } else if (proj.colorFilterPreset == "none") {
                proj.colorFilterPreset = "warm_cinematic"
            }
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility(if (isChecked) "Color filter enabled: ${proj.colorFilterPreset}" else "Color filter disabled")
            updateUI()
        }

        binding.cbWatermark.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && proj.watermarkImagePath.isEmpty()) {
                binding.cbWatermark.isChecked = false
                watermarkPickerLauncher.launch("image/*")
            } else {
                proj.watermarkEnabled = isChecked
                UnifiedProjectManager.saveProject(currentContext, proj)
                announceAccessibility(if (isChecked) "Watermark enabled" else "Watermark disabled")
                updateUI()
            }
        }

        binding.cbSpeed.setOnCheckedChangeListener { _, isChecked ->
            updateUI()
        }

        binding.btnEditTrim.setOnClickListener { showTrimDialog() }
        binding.btnEditText.setOnClickListener { showTextOverlaysDialog() }
        binding.btnEditBackgroundRemoval.setOnClickListener { showBackgroundRemovalDialog() }
        binding.btnEditKeyframes.setOnClickListener { showKeyframeDialog() }
        binding.btnEditColorFilter.setOnClickListener { showColorFilterDialog() }
        binding.btnEditWatermark.setOnClickListener { showWatermarkOptionsDialog() }
        binding.btnEditSpeed.setOnClickListener { showSpeedVolumeDialog() }
        binding.btnExportProject.setOnClickListener { exportProject() }
    }

    private fun updateUI() {
        val safeBinding = _binding ?: return
        val proj = project ?: return
        val currentContext = context ?: return

        // Update Subtitle / Text Overlay Canvas
        safeBinding.textOverlayView.setOverlays(proj.textOverlays)

        // 1. Trim Layer Card
        safeBinding.cbTrim.isChecked = proj.trimEnabled
        safeBinding.tvTrimDesc.text = if (proj.trimEnabled) {
            "${formatTime(proj.trimStartMs)} - ${formatTime(proj.trimEndMs)}"
        } else {
            getString(R.string.label_disabled)
        }

        // 2. Text Overlays Card
        safeBinding.cbText.isChecked = proj.textOverlays.isNotEmpty()
        safeBinding.tvTextDesc.text = if (proj.textOverlays.isEmpty()) {
            getString(R.string.msg_no_text_overlays)
        } else {
            "${getString(R.string.label_text_action)}: ${proj.textOverlays.size} ${getString(R.string.label_layers)}"
        }

        // 3. Background Removal Card
        safeBinding.cbBackgroundRemoval.isChecked = proj.backgroundRemovalEnabled
        safeBinding.tvBackgroundRemovalDesc.text = if (proj.backgroundRemovalEnabled) {
            "Type: ${proj.backgroundRemovalType.replace("_", " ").uppercase()}"
        } else {
            getString(R.string.label_disabled)
        }

        // 4. Keyframes Card
        safeBinding.cbKeyframe.isChecked = proj.keyframePreset != "none"
        safeBinding.tvKeyframeDesc.text = if (proj.keyframePreset != "none") {
            "Preset: ${proj.keyframePreset.replace("_", " ")}"
        } else {
            getString(R.string.label_disabled)
        }

        // 5. Color Filter Card
        val isColorActive = proj.colorFilterPreset != "none" || proj.brightness != 0.0f || proj.contrast != 1.0f || proj.saturation != 1.0f
        safeBinding.cbColorFilter.isChecked = isColorActive
        safeBinding.tvColorFilterDesc.text = if (isColorActive) {
            "Filter: ${proj.colorFilterPreset}, B:${proj.brightness}, C:${proj.contrast}, S:${proj.saturation}"
        } else {
            "Normal"
        }

        // 6. Watermark Card
        safeBinding.cbWatermark.isChecked = proj.watermarkEnabled && proj.watermarkImagePath.isNotEmpty()
        safeBinding.tvWatermarkDesc.text = if (proj.watermarkImagePath.isNotEmpty()) {
            val lastSeg = Uri.parse(proj.watermarkImagePath).lastPathSegment ?: "image"
            "${getString(R.string.label_watermark_action)}: $lastSeg"
        } else {
            getString(R.string.label_disabled)
        }

        // 7. Speed & Volume Card
        safeBinding.cbSpeed.isChecked = proj.speedMultiplier != 1.0f || proj.volumeLevel != 1.0f
        safeBinding.tvSpeedDesc.text = "${getString(R.string.label_speed)}: ${proj.speedMultiplier}x, ${getString(R.string.label_volume)}: ${(proj.volumeLevel * 100).toInt()}%"
    }

    private fun showTrimDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.label_trim_action))

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val etStart = EditText(currentContext).apply {
            hint = getString(R.string.string_35)
            setText(formatTime(proj.trimStartMs))
            contentDescription = "Start time in MM:SS"
        }

        val etEnd = EditText(currentContext).apply {
            hint = getString(R.string.string_34)
            setText(formatTime(proj.trimEndMs))
            contentDescription = "End time in MM:SS"
        }

        val btnSetStart = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_start_current)
            setOnClickListener {
                exoPlayer?.let {
                    val pos = it.currentPosition
                    etStart.setText(formatTime(pos))
                    announceAccessibility("Start position set to ${formatTime(pos)}")
                }
            }
        }

        val btnSetEnd = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_end_current)
            setOnClickListener {
                exoPlayer?.let {
                    val pos = it.currentPosition
                    etEnd.setText(formatTime(pos))
                    announceAccessibility("End position set to ${formatTime(pos)}")
                }
            }
        }

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_start_time) })
        layout.addView(etStart)
        layout.addView(btnSetStart)
        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_end_time) })
        layout.addView(etEnd)
        layout.addView(btnSetEnd)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            val startMs = parseTimeToMs(etStart.text.toString())
            val endMs = parseTimeToMs(etEnd.text.toString())
            proj.trimStartMs = startMs
            proj.trimEndMs = endMs
            proj.trimEnabled = true
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Trim saved: from ${formatTime(startMs)} to ${formatTime(endMs)}")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showTextOverlaysDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.label_text_action))

        val outerLayout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        var dialogInstance: AlertDialog? = null

        val btnAdd = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_add_new_text)
            setOnClickListener {
                showAddTextOverlayDialog { newText ->
                    proj.textOverlays.add(newText)
                    UnifiedProjectManager.saveProject(currentContext, proj)
                    announceAccessibility("Subtitle layer added: ${getPlainText(newText.text)}")
                    updateUI()
                    dialogInstance?.dismiss()
                    showTextOverlaysDialog()
                }
            }
        }
        outerLayout.addView(btnAdd)

        // Button to Auto-Generate Captions from Audio Speech with Timestamps & Custom Style
        val btnAutoStt = MaterialButton(currentContext, null, com.google.android.material.R.style.Widget_Material3_Button_TonalButton).apply {
            text = "تحويل الصوت إلى نصوص تلقائياً واختيار أسلوب الحركة (Auto Captions & Animation)"
            setOnClickListener {
                dialogInstance?.dismiss()
                showAutoCaptionStyleDialog()
            }
        }
        outerLayout.addView(btnAutoStt)

        // List existing text overlays
        if (proj.textOverlays.isEmpty()) {
            outerLayout.addView(TextView(currentContext).apply {
                text = getString(R.string.msg_no_text_added)
                setPadding(16, 16, 16, 16)
            })
        } else {
            val listCopy = ArrayList(proj.textOverlays)
            listCopy.forEachIndexed { index, textOverlay ->
                val row = LinearLayout(currentContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 8, 8, 8)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val label = TextView(currentContext).apply {
                    text = "${index + 1}. \"${getPlainText(textOverlay.text)}\" (${formatTime(textOverlay.startTimeMs)} - ${formatTime(textOverlay.endTimeMs)}) [${textOverlay.animationType}]"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                    contentDescription = "Subtitle ${index + 1}: ${getPlainText(textOverlay.text)}, from ${formatTime(textOverlay.startTimeMs)} to ${formatTime(textOverlay.endTimeMs)}, Animation: ${textOverlay.animationType}"
                }
                val btnEdit = MaterialButton(currentContext, null, com.google.android.material.R.style.Widget_Material3_Button_TonalButton).apply {
                    text = "✏️"
                    contentDescription = "Edit subtitle ${index + 1}"
                    setOnClickListener {
                        showEditTextOverlayDialog(index, textOverlay) { updated ->
                            val actualIndex = proj.textOverlays.indexOf(textOverlay)
                            if (actualIndex != -1) {
                                proj.textOverlays[actualIndex] = updated
                                UnifiedProjectManager.saveProject(currentContext, proj)
                                announceAccessibility("Subtitle ${index + 1} updated")
                                updateUI()
                                dialogInstance?.dismiss()
                                showTextOverlaysDialog()
                            }
                        }
                    }
                }
                val btnDel = MaterialButton(currentContext, null, com.google.android.material.R.style.Widget_Material3_Button_TonalButton).apply {
                    text = "X"
                    contentDescription = "Delete subtitle ${index + 1}"
                    setOnClickListener {
                        val actualIndex = proj.textOverlays.indexOf(textOverlay)
                        if (actualIndex != -1) {
                            proj.textOverlays.removeAt(actualIndex)
                            UnifiedProjectManager.saveProject(currentContext, proj)
                            announceAccessibility("Subtitle ${index + 1} deleted")
                            updateUI()
                            dialogInstance?.dismiss()
                            showTextOverlaysDialog()
                        }
                    }
                }
                row.addView(label)
                row.addView(btnEdit)
                row.addView(btnDel)
                outerLayout.addView(row)
            }
        }

        val scrollView = android.widget.ScrollView(currentContext).apply {
            addView(outerLayout)
        }
        builder.setView(scrollView)
        builder.setPositiveButton(getString(R.string.btn_close)) { dialog, _ -> dialog.dismiss() }
        dialogInstance = builder.create()
        dialogInstance.show()
    }

    private fun showAutoCaptionStyleDialog() {
        val currentContext = context ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle("خيارات عرض وشكل النصوص التلقائية (Auto Captions Style)")

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val animSpinner = Spinner(currentContext)
        val textAnimsList = com.example.accessiblevideoeditor.ui.CloudConfigManager.getTextAnimations(currentContext)
        val animOptions = textAnimsList.map { it.second }.toTypedArray()
        val animValues = textAnimsList.map { it.first }.toTypedArray()
        val animAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, animOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        animSpinner.adapter = animAdapter
        val defaultAnimIdx = animValues.indexOf("fade_in").let { if (it >= 0) it else 0 }
        animSpinner.setSelection(defaultAnimIdx)

        val colorSpinner = Spinner(currentContext)
        val colorNames = arrayOf("أبيض (#FFFFFF)", "أصفر (#FFFF00)", "أخضر (#00FF00)", "أزرق سماوي (#00FFFF)", "أحمر (#FF3333)")
        val colorValues = arrayOf("#FFFFFF", "#FFFF00", "#00FF00", "#00FFFF", "#FF3333")
        val colorAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, colorNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        colorSpinner.adapter = colorAdapter

        val posSpinner = Spinner(currentContext)
        val posNames = arrayOf("أسفل الفيديو (82%)", "منتصف الفيديو (50%)", "أعلى الفيديو (15%)")
        val posValues = arrayOf(0.82f, 0.50f, 0.15f)
        val posAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, posNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        posSpinner.adapter = posAdapter

        val sizeSpinner = Spinner(currentContext)
        val sizeNames = arrayOf("عادي (24)", "كبير (28)", "ضخم (32)", "عملاق (36)")
        val sizeValues = arrayOf(24, 28, 32, 36)
        val sizeAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, sizeNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sizeSpinner.adapter = sizeAdapter

        val cbBackdrop = androidx.appcompat.widget.AppCompatCheckBox(currentContext).apply {
            text = "إضافة خلفية مظللة للنص (Box Backdrop) لضمان الوضوح"
            isChecked = true
        }

        layout.addView(TextView(currentContext).apply { text = "طريقة عرض الحركة (Animation Style):" })
        layout.addView(animSpinner)
        layout.addView(TextView(currentContext).apply { text = "لون الخط (Font Color):" })
        layout.addView(colorSpinner)
        layout.addView(TextView(currentContext).apply { text = "موقع النص في الشاشة (Position):" })
        layout.addView(posSpinner)
        layout.addView(TextView(currentContext).apply { text = "حجم الخط (Font Size):" })
        layout.addView(sizeSpinner)
        layout.addView(cbBackdrop)

        builder.setView(layout)
        builder.setPositiveButton("بدء الاستخراج والتوليد 🚀") { dialog, _ ->
            val selAnim = animValues[animSpinner.selectedItemPosition]
            val selColor = colorValues[colorSpinner.selectedItemPosition]
            val selPos = posValues[posSpinner.selectedItemPosition]
            val selSize = sizeValues[sizeSpinner.selectedItemPosition]
            val selBackdrop = cbBackdrop.isChecked
            dialog.dismiss()
            autoGenerateSpeechToTextCaptions(selAnim, selColor, selPos, selSize, selBackdrop)
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun autoGenerateSpeechToTextCaptions(
        animationType: String = "fade_in",
        colorHex: String = "#FFFFFF",
        yPosPercent: Float = 0.82f,
        fontSize: Int = 24,
        hasBackdrop: Boolean = true
    ) {
        val currentContext = context ?: return
        val proj = project ?: return

        ProcessingManager.startProcessing("جارٍ استخراج الصوت وتحويل الكلام إلى نصوص بأوقات دقيقة...")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var tempSourceFile: File? = null
            try {
                val rawPath = proj.videoPath
                val realVideoPath = if (rawPath.startsWith("content://") || rawPath.startsWith("file://")) {
                    tempSourceFile = MediaUtils.copyUriToTempFile(currentContext, Uri.parse(rawPath), "temp_stt_src_${System.currentTimeMillis()}.mp4")
                    tempSourceFile?.absolutePath ?: rawPath
                } else {
                    val f = File(rawPath)
                    if (!f.exists()) {
                        tempSourceFile = MediaUtils.copyUriToTempFile(currentContext, Uri.parse(rawPath), "temp_stt_src_${System.currentTimeMillis()}.mp4")
                        tempSourceFile?.absolutePath ?: rawPath
                    } else {
                        rawPath
                    }
                }

                val geminiKey = SettingsManager.geminiApiKey.trim()
                val isGemini = geminiKey.isNotBlank()

                val audioFile = if (isGemini) {
                    File(currentContext.cacheDir, "temp_stt_${System.currentTimeMillis()}.mp3")
                } else {
                    File(currentContext.cacheDir, "temp_stt_${System.currentTimeMillis()}.wav")
                }

                val trimStart = if (proj.trimEnabled) proj.trimStartMs else 0L
                val trimEnd = if (proj.trimEnabled) proj.trimEndMs else 0L

                val extractSuccess = if (isGemini) {
                    FFmpegProcessor.extractAudio(realVideoPath, audioFile.absolutePath, "mp3", startMs = trimStart, endMs = trimEnd)
                } else {
                    FFmpegProcessor.extractAudioToWav(realVideoPath, audioFile.absolutePath, startMs = trimStart, endMs = trimEnd)
                }

                if (extractSuccess && audioFile.exists()) {
                    var resultText = ""

                    if (isGemini) {
                        try {
                            val userModel = SettingsManager.geminiModel
                            val model = GenerativeModel(
                                modelName = if (userModel.isNotBlank()) userModel else "gemini-2.5-flash",
                                apiKey = geminiKey
                            )
                            val bytes = audioFile.readBytes()
                            val inputContent = content {
                                blob("audio/mp3", bytes)
                                text("""
                                    You are a high-precision Speech-to-Text AI. Listen carefully to this audio recording and transcribe the speech with precise timing.
                                    - Transcribe in the exact spoken language (Arabic, English, etc.) preserving correct spelling and natural punctuation.
                                    - Break the transcription into natural, readable subtitle segments.
                                    - For each segment, determine the start and end time in milliseconds.
                                    - Output the result ONLY as a valid JSON array of objects, with no markdown tags, no wrapper text. Each object must have exactly these keys:
                                      "text" (string): the subtitle text
                                      "start" (number): start time in milliseconds
                                      "end" (number): end time in milliseconds
                                    Example output format:
                                    [
                                      {"text": "Hello world", "start": 500, "end": 2500},
                                      {"text": "Welcome to our video", "start": 3000, "end": 5500}
                                    ]
                                """.trimIndent())
                            }
                            val response = model.generateContent(inputContent)
                            resultText = response.text ?: ""
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (resultText.isBlank() || resultText.startsWith("ERROR")) {
                        val wavFile = if (isGemini) {
                            val convertedWav = File(currentContext.cacheDir, "temp_stt_conv_${System.currentTimeMillis()}.wav")
                            val convSuccess = FFmpegProcessor.extractAudioToWav(realVideoPath, convertedWav.absolutePath)
                            if (convSuccess) convertedWav else audioFile
                        } else {
                            audioFile
                        }

                        val sttProcessor = SpeechToTextProcessor()
                        resultText = sttProcessor.recognizeWavFileOnline(
                            wavFile.absolutePath,
                            OnlineAudioModel.WIT_AI
                        ) { progress ->
                            ProcessingManager.updateProgress(progress / 100f, "تحليل الصوت: $progress%")
                        }

                        if (isGemini && wavFile != audioFile) {
                            try { wavFile.delete() } catch (_: Exception) {}
                        }
                    }

                    val totalDurMs = try {
                        FFmpegProcessor.getMediaDurationMs(realVideoPath).toLong()
                    } catch (_: Exception) {
                        0L
                    }

                    try { audioFile.delete() } catch (_: Exception) {}

                    withContext(Dispatchers.Main) {
                        if (resultText.isNotBlank() && !resultText.startsWith("ERROR")) {
                            var parsedJson = false
                            try {
                                val cleanedJson = resultText.trim()
                                    .removePrefix("```json")
                                    .removePrefix("```")
                                    .removeSuffix("```")
                                    .trim()
                                val jsonArray = org.json.JSONArray(cleanedJson)
                                proj.textOverlays.clear()
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val text = obj.getString("text")
                                    val start = obj.getLong("start")
                                    val end = obj.getLong("end")
                                    proj.textOverlays.add(
                                        TextOverlayConfig(
                                            text = applySemanticColoring(text),
                                            startTimeMs = start,
                                            endTimeMs = end,
                                            xPosPercent = 0.5f,
                                            yPosPercent = yPosPercent,
                                            colorHex = colorHex,
                                            fontSize = fontSize,
                                            animationType = animationType,
                                            hasBackdrop = hasBackdrop
                                        )
                                    )
                                }
                                parsedJson = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            if (!parsedJson) {
                                val rawSentences = resultText.split(Regex("(?<=[.!?؟,،\n])\\s+|\n+"))
                                    .map { it.trim().trim('`', '*', '"') }
                                    .filter { it.isNotBlank() }

                                val sentences = if (rawSentences.isNotEmpty()) rawSentences else {
                                    val words = resultText.split(" ").filter { it.isNotBlank() }
                                    val list = mutableListOf<String>()
                                    for (i in words.indices step 6) {
                                        list.add(words.subList(i, minOf(i + 6, words.size)).joinToString(" "))
                                    }
                                    list
                                }

                                val count = maxOf(1, sentences.size)
                                val durationPerSentence = (totalDurMs / count).coerceAtLeast(1000L)

                                proj.textOverlays.clear()

                                var currentStart = 0L
                                for (sentence in sentences) {
                                    if (currentStart >= totalDurMs) break
                                    val chunkEnd = minOf(currentStart + durationPerSentence, totalDurMs)

                                    proj.textOverlays.add(
                                        TextOverlayConfig(
                                            text = applySemanticColoring(sentence),
                                            startTimeMs = currentStart,
                                            endTimeMs = chunkEnd,
                                            xPosPercent = 0.5f,
                                            yPosPercent = yPosPercent,
                                            colorHex = colorHex,
                                            fontSize = fontSize,
                                            animationType = animationType,
                                            hasBackdrop = hasBackdrop
                                        )
                                    )
                                    currentStart = chunkEnd + 150L
                                }
                            }

                            UnifiedProjectManager.saveProject(currentContext, proj)
                            SoundManager.playSuccess()
                            announceAccessibility("تم تحويل الصوت إلى نصوص وإضافة ${proj.textOverlays.size} طبقات ترجمة بمزامنة دقيقة")
                            updateUI()
                        } else {
                            Toast.makeText(currentContext, "تعذر تحويل الصوت تلقائياً. تأكد من ضبط الـ API Key في الإعدادات", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(currentContext, "فشل استخراج الصوت من الفيديو", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { tempSourceFile?.delete() } catch (_: Exception) {}
                ProcessingManager.stopProcessing()
            }
        }
    }

    private fun applySemanticColoring(text: String): String {
        val words = text.split(" ")
        val redWords = setOf("أفسدت", "أفسد", "انهيار", "آذيت", "آذى", "أذى", "ضعف", "حاجة", "أدوية", "انهدم", "هدمته", "أعدائه", "ضره", "الضرر", "خذلوني", "خذل", "الخذلان", "ألم", "الألم", "حزن", "الحزن")
        val goldWords = setOf("أسامحك", "أسامح", "مسامحة", "أعتزل", "كرامتي", "الكرامة", "معروفه", "معروف", "المعروف", "الود", "ود", "ثقة", "أثق", "الصدفة", "قلوب", "القلوب", "القلب", "قلب")
        val greenWords = setOf("الخير", "خير", "سلام", "السلام", "حب", "أحبهم", "الحب", "النجاح")

        val resultWords = words.map { word ->
            val cleaned = word.replace(Regex("[.,!؟?()،]"), "").trim()
            when {
                redWords.contains(cleaned) -> "<font color=\"#FF3333\"><b>$word</b></font>"
                goldWords.contains(cleaned) -> "<font color=\"#FFD700\"><b>$word</b></font>"
                greenWords.contains(cleaned) -> "<font color=\"#00FF00\"><b>$word</b></font>"
                else -> word
            }
        }
        return resultWords.joinToString(" ")
    }

    private fun getPlainText(text: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(text).toString()
        }
    }


    private fun showAddTextOverlayDialog(onAdd: (TextOverlayConfig) -> Unit) {
        val currentContext = context ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.dialog_add_text_title))

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val etText = EditText(currentContext).apply {
            hint = getString(R.string.hint_enter_text_content)
            contentDescription = getString(R.string.cd_text_content)
        }
        val etStart = EditText(currentContext).apply {
            hint = getString(R.string.hint_start_time_eg)
            contentDescription = getString(R.string.cd_start_time)
        }
        val etEnd = EditText(currentContext).apply {
            hint = getString(R.string.hint_end_time_eg)
            contentDescription = getString(R.string.cd_end_time)
        }

        val btnSetStart = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_current_position)
            setOnClickListener { etStart.setText(formatTime(exoPlayer?.currentPosition ?: 0L)) }
        }

        val btnSetEnd = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_current_position)
            setOnClickListener { etEnd.setText(formatTime(exoPlayer?.currentPosition ?: 0L)) }
        }

        val animSpinner = Spinner(currentContext)
        val textAnimsList = com.example.accessiblevideoeditor.ui.CloudConfigManager.getTextAnimations(currentContext)
        val animOptions = textAnimsList.map { it.second }.toTypedArray()
        val animValues = textAnimsList.map { it.first }.toTypedArray()
        val animAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, animOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        animSpinner.adapter = animAdapter

        val cbBackdrop = androidx.appcompat.widget.AppCompatCheckBox(currentContext).apply {
            text = "إضافة خلفية مظللة للنص (Box Backdrop)"
            isChecked = true
        }

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_text_content) })
        layout.addView(etText)
        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_start_time) })
        layout.addView(etStart)
        layout.addView(btnSetStart)
        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_end_time) })
        layout.addView(etEnd)
        layout.addView(btnSetEnd)
        layout.addView(TextView(currentContext).apply { text = "Text Animation:" })
        layout.addView(animSpinner)
        layout.addView(cbBackdrop)

        val scrollView = android.widget.ScrollView(currentContext).apply {
            addView(layout)
        }
        builder.setView(scrollView)
        builder.setPositiveButton("Add") { dialog, _ ->
            val txt = etText.text.toString().trim()
            val startMs = parseTimeToMs(etStart.text.toString())
            val endMs = parseTimeToMs(etEnd.text.toString())

            if (startMs >= endMs || startMs < 0) {
                Toast.makeText(currentContext, "أوقات البداية والنهاية غير صالحة!", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (txt.isNotEmpty()) {
                val overlay = TextOverlayConfig(
                    text = applySemanticColoring(txt),
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    animationType = animValues[animSpinner.selectedItemPosition],
                    hasBackdrop = cbBackdrop.isChecked
                )
                onAdd(overlay)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showEditTextOverlayDialog(index: Int, overlay: TextOverlayConfig, onSave: (TextOverlayConfig) -> Unit) {
        val currentContext = context ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle("تعديل نص الترجمة #${index + 1}")

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val etText = EditText(currentContext).apply {
            setText(getPlainText(overlay.text))
            hint = getString(R.string.hint_enter_text_content)
            contentDescription = getString(R.string.cd_text_content)
        }
        val etStart = EditText(currentContext).apply {
            setText(formatTime(overlay.startTimeMs))
            contentDescription = getString(R.string.cd_start_time)
        }
        val etEnd = EditText(currentContext).apply {
            setText(formatTime(overlay.endTimeMs))
            contentDescription = getString(R.string.cd_end_time)
        }

        val btnSetStart = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_current_position)
            setOnClickListener { etStart.setText(formatTime(exoPlayer?.currentPosition ?: 0L)) }
        }

        val btnSetEnd = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_set_current_position)
            setOnClickListener { etEnd.setText(formatTime(exoPlayer?.currentPosition ?: 0L)) }
        }

        val animSpinner = Spinner(currentContext)
        val textAnimsList = com.example.accessiblevideoeditor.ui.CloudConfigManager.getTextAnimations(currentContext)
        val animOptions = textAnimsList.map { it.second }.toTypedArray()
        val animValues = textAnimsList.map { it.first }.toTypedArray()
        val animAdapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, animOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        animSpinner.adapter = animAdapter
        val currentAnimIdx = animValues.indexOf(overlay.animationType).let { if (it >= 0) it else 0 }
        animSpinner.setSelection(currentAnimIdx)

        val cbBackdrop = androidx.appcompat.widget.AppCompatCheckBox(currentContext).apply {
            text = "إضافة خلفية مظللة للنص (Box Backdrop)"
            isChecked = overlay.hasBackdrop
        }

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_text_content) })
        layout.addView(etText)
        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_start_time) })
        layout.addView(etStart)
        layout.addView(btnSetStart)
        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_end_time) })
        layout.addView(etEnd)
        layout.addView(btnSetEnd)
        layout.addView(TextView(currentContext).apply { text = "نوع الحركة (Animation):" })
        layout.addView(animSpinner)
        layout.addView(cbBackdrop)

        val scrollView = android.widget.ScrollView(currentContext).apply {
            addView(layout)
        }
        builder.setView(scrollView)
        builder.setPositiveButton("حفظ") { dialog, _ ->
            val txt = etText.text.toString().trim()
            val startMs = parseTimeToMs(etStart.text.toString())
            val endMs = parseTimeToMs(etEnd.text.toString())

            if (startMs >= endMs || startMs < 0) {
                Toast.makeText(currentContext, "أوقات البداية والنهاية غير صالحة!", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (txt.isNotEmpty()) {
                val updated = overlay.copy(
                    text = applySemanticColoring(txt),
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    animationType = animValues[animSpinner.selectedItemPosition],
                    hasBackdrop = cbBackdrop.isChecked
                )
                onSave(updated)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showBackgroundRemovalDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.bg_removal_title))

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // 1. Background removal mode selection
        layout.addView(TextView(currentContext).apply { 
            text = getString(R.string.bg_removal_mode_label)
            setPadding(0, 16, 0, 8)
        })

        val types = arrayOf("auto_subject", "green_screen", "blue_screen", "transparent", "custom_bg")
        val typeLabels = arrayOf(
            getString(R.string.bg_removal_auto),
            getString(R.string.bg_removal_green),
            getString(R.string.bg_removal_blue),
            getString(R.string.bg_removal_transparent),
            getString(R.string.bg_removal_custom)
        )

        val spinnerType = Spinner(currentContext)
        spinnerType.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, typeLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currIdx = types.indexOf(proj.backgroundRemovalType)
        if (currIdx != -1) spinnerType.setSelection(currIdx)
        layout.addView(spinnerType)

        // 2. Custom background image chooser (shown dynamically)
        val customBgContainer = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            visibility = if (proj.backgroundRemovalType == "custom_bg") View.VISIBLE else View.GONE
        }

        val txtBgPath = TextView(currentContext).apply {
            text = if (proj.backgroundRemovalCustomBgPath.isNotEmpty()) {
                "${getString(R.string.label_selected_bg)}: ${File(proj.backgroundRemovalCustomBgPath).name}"
            } else {
                getString(R.string.label_no_bg_selected)
            }
            setPadding(0, 8, 0, 8)
        }

        val btnChooseBg = com.google.android.material.button.MaterialButton(currentContext).apply {
            text = getString(R.string.btn_choose_background_image)
            setOnClickListener { 
                backgroundImagePickerLauncher.launch("image/*")
            }
        }

        customBgContainer.addView(btnChooseBg)
        customBgContainer.addView(txtBgPath)
        layout.addView(customBgContainer)

        // Show/hide custom bg selection dynamically
        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (types[position] == "custom_bg") {
                    customBgContainer.visibility = View.VISIBLE
                } else {
                    customBgContainer.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // 3. FPS Option selection
        layout.addView(TextView(currentContext).apply { 
            text = getString(R.string.bg_removal_fps_label)
            setPadding(0, 16, 0, 8)
        })

        val fpsOptions = arrayOf("auto", "15", "24", "30", "60")
        val fpsLabels = arrayOf(
            getString(R.string.fps_auto),
            "15 FPS",
            "24 FPS",
            "30 FPS",
            "60 FPS"
        )
        val spinnerFps = Spinner(currentContext)
        spinnerFps.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, fpsLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currFpsIdx = fpsOptions.indexOf(proj.backgroundRemovalFpsOption)
        if (currFpsIdx != -1) spinnerFps.setSelection(currFpsIdx)
        layout.addView(spinnerFps)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            val selectedType = types[spinnerType.selectedItemPosition]
            val selectedFps = fpsOptions[spinnerFps.selectedItemPosition]

            proj.backgroundRemovalType = selectedType
            proj.backgroundRemovalFpsOption = selectedFps
            proj.backgroundRemovalEnabled = true

            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Background removal set to $selectedType at $selectedFps FPS")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showKeyframeDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle("Keyframe & Motion Presets")

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val spinner = Spinner(currentContext)
        val presets = arrayOf("none", "zoom_in_center", "pan_left_to_right")
        spinner.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, presets).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currIdx = presets.indexOf(proj.keyframePreset)
        if (currIdx != -1) spinner.setSelection(currIdx)

        layout.addView(TextView(currentContext).apply { text = "Select Keyframe Motion Preset:" })
        layout.addView(spinner)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            proj.keyframePreset = spinner.selectedItem.toString()
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Keyframe preset set to ${proj.keyframePreset}")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showColorFilterDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle("Color Adjustments & Relight Filters")

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val spinnerPreset = Spinner(currentContext)
        val presets = arrayOf("none", "warm_cinematic", "cool_noir", "vivid_hdr", "vintage_sepia")
        spinnerPreset.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, presets).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currPresetIdx = presets.indexOf(proj.colorFilterPreset)
        if (currPresetIdx != -1) spinnerPreset.setSelection(currPresetIdx)

        layout.addView(TextView(currentContext).apply { text = "Color Preset Filter:" })
        layout.addView(spinnerPreset)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            proj.colorFilterPreset = spinnerPreset.selectedItem.toString()
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Color filter set to ${proj.colorFilterPreset}")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showWatermarkOptionsDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.label_watermark_action))

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val btnSelectImg = MaterialButton(currentContext).apply {
            text = getString(R.string.btn_choose_watermark_image)
            setOnClickListener { watermarkPickerLauncher.launch("image/*") }
        }
        layout.addView(btnSelectImg)

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_watermark_position) })
        val positions = arrayOf("top_right", "top_left", "bottom_right", "bottom_left")
        val spinner = Spinner(currentContext)
        spinner.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, positions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currIdx = positions.indexOf(proj.watermarkPosition)
        if (currIdx != -1) spinner.setSelection(currIdx)
        layout.addView(spinner)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            proj.watermarkPosition = spinner.selectedItem.toString()
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Watermark position set to ${proj.watermarkPosition}")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showSpeedVolumeDialog() {
        val currentContext = context ?: return
        val proj = project ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.label_speed_action))

        val layout = LinearLayout(currentContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_speed_multiplier) })
        val speeds = arrayOf("0.5", "1.0", "1.5", "2.0")
        val spinnerSpeed = Spinner(currentContext)
        spinnerSpeed.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, speeds).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currSpeedIdx = speeds.indexOf(proj.speedMultiplier.toString())
        if (currSpeedIdx != -1) spinnerSpeed.setSelection(currSpeedIdx)
        layout.addView(spinnerSpeed)

        layout.addView(TextView(currentContext).apply { text = getString(R.string.label_volume_level) })
        val volumes = arrayOf(getString(R.string.label_mute), "50%", "100%", "150%", "200%")
        val spinnerVolume = Spinner(currentContext)
        spinnerVolume.adapter = ArrayAdapter(currentContext, android.R.layout.simple_spinner_item, volumes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currVolIdx = when (proj.volumeLevel) {
            0.0f -> 0
            0.5f -> 1
            1.5f -> 3
            2.0f -> 4
            else -> 2
        }
        spinnerVolume.setSelection(currVolIdx)
        layout.addView(spinnerVolume)

        builder.setView(layout)
        builder.setPositiveButton(getString(R.string.btn_save_project)) { dialog, _ ->
            proj.speedMultiplier = spinnerSpeed.selectedItem.toString().toFloatOrNull() ?: 1.0f
            proj.volumeLevel = when (spinnerVolume.selectedItemPosition) {
                0 -> 0.0f
                1 -> 0.5f
                3 -> 1.5f
                4 -> 2.0f
                else -> 1.0f
            }
            UnifiedProjectManager.saveProject(currentContext, proj)
            announceAccessibility("Speed set to ${proj.speedMultiplier}x, volume set to ${(proj.volumeLevel * 100).toInt()}%")
            updateUI()
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun exportProject() {
        val currentContext = context ?: return
        val proj = project ?: return

        SoundManager.playProcessing()
        val processMsg = AppStrings.get(currentContext, R.string.string_28).replace(" %1\$s%%", "")
        ProcessingManager.startProcessing(processMsg)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputFile = File(currentContext.cacheDir, "project_export_${System.currentTimeMillis()}.mp4")
                val isSuccess = FFmpegPipelineBuilder.renderProject(
                    currentContext,
                    proj,
                    outputFile.absolutePath
                )

                if (isSuccess) {
                    val savedUri = MediaUtils.saveVideoToGallery(
                        currentContext,
                        outputFile,
                        "AccessibleProject_Video_${System.currentTimeMillis()}.mp4"
                    )
                    SoundManager.playSuccess()
                    withContext(Dispatchers.Main) {
                        val safeContext = context ?: return@withContext
                        ShareDialogHelper.showSuccessShareDialog(
                            safeContext,
                            savedUri,
                            try { getString(R.string.msg_project_export_success) } catch (_: Exception) { "تم تصدير المشروع وحفظه بنجاح 🎉" },
                            "video/mp4"
                        ) {
                            try { findNavController().navigateUp() } catch (_: Exception) {}
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val safeContext = context ?: return@withContext
                        Toast.makeText(safeContext, try { getString(R.string.msg_error_project_render_failed) } catch (_: Exception) { "فشل تصدير المشروع" }, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val safeContext = context ?: return@withContext
                    Toast.makeText(safeContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                ProcessingManager.stopProcessing()
            }
        }
    }

    private fun announceAccessibility(message: String) {
        val view = view ?: return
        view.announceForAccessibility(message)
    }

    private fun saveAndExit() {
        val currentContext = context ?: return
        project?.let {
            UnifiedProjectManager.saveProject(currentContext, it)
        }
        val prefs = currentContext.getSharedPreferences("CameraPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("last_project_clean_exit", true)
            apply()
        }
        try { findNavController().navigateUp() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updatePlayerProgressRunnable)
        exoPlayer?.pause()
        val currentContext = context ?: return
        project?.let {
            UnifiedProjectManager.saveProject(currentContext, it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updatePlayerProgressRunnable)
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }

    private fun formatTime(ms: Long): String {
        val millis = ms % 1000
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60)) % 24
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }

    private fun parseTimeToMs(timeStr: String): Long {
        if (timeStr.isBlank()) return 0L
        try {
            val dotParts = timeStr.split(".")
            val baseTime = dotParts[0].trim()
            val msPart = if (dotParts.size > 1) dotParts[1].trim().padEnd(3, '0').take(3).toLongOrNull() ?: 0L else 0L
            
            val parts = baseTime.split(":")
            val baseMs = when (parts.size) {
                1 -> (parts[0].trim().toLongOrNull() ?: 0L) * 1000L
                2 -> ((parts[0].trim().toLongOrNull() ?: 0L) * 60 + (parts[1].trim().toLongOrNull() ?: 0L)) * 1000L
                3 -> ((parts[0].trim().toLongOrNull() ?: 0L) * 3600 + (parts[1].trim().toLongOrNull() ?: 0L) * 60 + (parts[2].trim().toLongOrNull() ?: 0L)) * 1000L
                else -> 0L
            }
            return baseMs + msPart
        } catch (_: Exception) {
            return 0L
        }
    }
}
