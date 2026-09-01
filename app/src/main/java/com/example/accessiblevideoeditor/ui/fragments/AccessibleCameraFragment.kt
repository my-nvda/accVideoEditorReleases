package com.example.accessiblevideoeditor.ui.fragments

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentAccessibleCameraBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.SettingsManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

class AccessibleCameraFragment : Fragment(), SensorEventListener {

    enum class CameraSubMode(val arabicName: String) {
        AUTO("الوضع التلقائي الذكي"),
        TEXT_OCR("قارئ النصوص بالوقت الفعلي"),
        QR_BARCODE("قارئ الـ QR والباركود"),
        COLOR_IDENTIFIER("محدد الألوان الصوتي"),
        OUTFIT_MATCHER("منسق ألوان الملابس"),
        AI_SCENE_DESC("واصف المشاهد بالذكاء الاصطناعي"),
        LED_READER("كاشف أضواء التنبيه بالأجهزة"),
        MEDICINE_HELPER("مساعد الأدوية والوصفات"),
        SCRATCH_CARD("شحن كروت الاتصال"),
        EXPIRY_DATE("كاشف تاريخ الصلاحية"),
        FIND_LOST_ITEMS("البحث عن المفقودات الشخصية"),
        APPLIANCE_READER("قارئ شاشات الأجهزة المنزلية"),
        CREDIT_CARD("قارئ بطاقات الائتمان المؤمن"),
        SIGN_LANGUAGE("مترجم لغة الإشارة"),
        DEVICE_BATTERY("قارئ شحن الأجهزة الخارجية"),
        HANDWRITING_OCR("قارئ الكتابة اليدوية"),
        FACE_GUIDE("توجيه الوجوه والتصوير الشخصي")
    }

    private var _binding: FragmentAccessibleCameraBinding? = null
    private val binding get() = _binding!!

    // Camera & Exec
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraExecutor: ExecutorService? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var camera: Camera? = null

    // Sensors & Haptics & Audio
    private var sensorManager: SensorManager? = null
    private var vibrator: Vibrator? = null
    private var toneGenerator: ToneGenerator? = null
    private var backgroundMusicPlayer: MediaPlayer? = null
    private var isRecording = false

    // Settings
    private lateinit var prefs: SharedPreferences
    private var autoCaptureEnabled = true
    private var hapticsEnabled = true
    private var tiltGuidanceEnabled = true
    private var objectDetectorEnabled = true
    private var beepingGuidanceEnabled = true
    private var vibrationGuidanceEnabled = false
    private var cameraTimerSeconds = 3

    // Operational States
    private var isFlashOn = false
    private var isVideoModeActive = false
    private var currentSubMode = CameraSubMode.AUTO
    private var lostItemTarget = "cup" // Default lost item target

    private var lastGuidanceTime = 0L
    private var lastBeepTime = 0L
    private var lastObjectAnnounceTime = 0L
    private var lastTiltAnnounceTime = 0L
    private var lastCardTipAnnounceTime = 0L
    private var isBarcodeDialogShowing = false
    private var lastTiltDirection = ""
    private var autoCaptureJob: Job? = null
    private var countdownJob: Job? = null
    private var isCapturing = false

    // ML Kit Clients
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    private val objectLabeler by lazy {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
        ImageLabeling.getClient(options)
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "يجب توفير صلاحية الكاميرا لتشغيل هذه الميزة!", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessibleCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = requireContext().getSharedPreferences("CameraPrefs", Context.MODE_PRIVATE)
        loadSettings()

        // Init managers
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.topBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Shutter Button setup
        binding.btnShutter.setOnClickListener {
            if (isVideoModeActive) {
                toggleVideoRecording()
            } else {
                triggerPhotoCaptureFlow()
            }
        }
        // Mode Toggles (Photo vs Video)
        binding.btnModePhoto.setOnClickListener {
            if (isVideoModeActive) {
                isVideoModeActive = false
                binding.btnModePhoto.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                binding.btnModeVideo.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                binding.btnShutter.text = "📸 التقاط"
                binding.btnShutter.contentDescription = "زر التقاط صورة فوتوغرافية"
                announceGuidance("تم التحديد: وضع التقاط الصور")
                vibrateFeedback(50)
                bindCameraUseCases()
            }
        }

        binding.btnModeVideo.setOnClickListener {
            if (!isVideoModeActive) {
                isVideoModeActive = true
                binding.btnModeVideo.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                binding.btnModePhoto.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                binding.btnShutter.text = "🎥 تسجيل"
                binding.btnShutter.contentDescription = "زر بدء تسجيل الفيديو"
                announceGuidance("تم التحديد: وضع تسجيل الفيديو")
                vibrateFeedback(50)
                bindCameraUseCases()
            }
        }
        // Flash Toggle
        binding.btnToggleFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            camera?.cameraControl?.enableTorch(isFlashOn)
            binding.btnToggleFlash.text = if (isFlashOn) "💡" else "📴"
            announceGuidance(if (isFlashOn) "تم تشغيل الفلاش" else "تم إيقاف الفلاش")
            vibrateFeedback(60)
        }

        // Switch Camera (Front/Back)
        binding.btnSwitchCamera.setOnClickListener {
            toggleCameraFacing()
        }

        // More Options Button
        binding.btnMoreOptions.setOnClickListener {
            showMoreOptionsDialog()
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Setup Key Listener for Volume buttons physical shortcuts
        binding.previewView.isFocusable = true
        binding.previewView.requestFocus()
        binding.previewView.setOnKeyListener { _, keyCode, event ->
            val useVolumeShortcuts = prefs.getBoolean("pref_volume_shortcuts", true)
            if (useVolumeShortcuts && event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (isVideoModeActive) toggleVideoRecording() else triggerPhotoCaptureFlow()
                    true
                } else false
            } else false
        }

        checkPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { accel ->
            sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
        startAmbientSoundscape()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        stopAmbientSoundscape()
        autoCaptureJob?.cancel()
        countdownJob?.cancel()
    }

    private fun loadSettings() {
        autoCaptureEnabled = prefs.getBoolean("pref_auto_capture", true)
        hapticsEnabled = prefs.getBoolean("pref_haptics", true)
        tiltGuidanceEnabled = prefs.getBoolean("pref_tilt_guidance", true)
        objectDetectorEnabled = prefs.getBoolean("pref_object_detector", true)
        beepingGuidanceEnabled = prefs.getBoolean("pref_beeping_guidance", true)
        vibrationGuidanceEnabled = prefs.getBoolean("pref_vibration_guidance", false)
        cameraTimerSeconds = prefs.getInt("pref_camera_timer", 3)
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startCamera()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor!!, ImageFrameAnalyzer())
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()
            if (isVideoModeActive) {
                camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture!!
                )
            } else {
                camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
            }
            camera?.cameraControl?.enableTorch(isFlashOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleCameraFacing() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        binding.tvObjectDescription.visibility = if (lensFacing == CameraSelector.LENS_FACING_BACK) View.VISIBLE else View.GONE
        bindCameraUseCases()
        announceGuidance(if (lensFacing == CameraSelector.LENS_FACING_FRONT) "تم التبديل للكاميرا الأمامية" else "تم التبديل للكاميرا الخلفية")
    }

    private fun triggerPhotoCaptureFlow() {
        if (isCapturing) return
        isCapturing = true

        if (cameraTimerSeconds > 0) {
            startCountdownTimer(cameraTimerSeconds) {
                executePhotoCapture()
            }
        } else {
            executePhotoCapture()
        }
    }

    private fun startCountdownTimer(seconds: Int, onFinish: () -> Unit) {
        countdownJob?.cancel()
        binding.tvCountdownOverlay.visibility = View.VISIBLE
        
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            var count = seconds
            while (count > 0) {
                binding.tvCountdownOverlay.text = count.toString()
                announceImmediate("$count")
                playRawSound(R.raw.camera_timer)
                delay(1000)
                count--
            }
            binding.tvCountdownOverlay.visibility = View.GONE
            onFinish()
        }
    }

    private fun executePhotoCapture() {
        val capture = imageCapture ?: run { isCapturing = false; return }

        val context = requireContext()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "AccessibleCamera_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    playRawSound(R.raw.camera_shutter)
                    vibrateFeedback(300)
                    Toast.makeText(context, getString(R.string.guidance_photo_taken), Toast.LENGTH_SHORT).show()
                    announceGuidance(getString(R.string.guidance_photo_taken))
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    exception.printStackTrace()
                    Toast.makeText(context, "فشل التقاط الصورة: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun toggleVideoRecording() {
        if (isRecording) {
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
            binding.btnShutter.text = "🎥 تسجيل"
            binding.btnShutter.contentDescription = "زر بدء تسجيل الفيديو"
            announceGuidance("تم إيقاف التسجيل وجاري حفظ الفيديو في المعرض")
            vibrateFeedback(400)
        } else {
            val ctx = context ?: return
            if (!com.example.accessiblevideoeditor.utils.StorageUtils.isSpaceAvailable(ctx, 150 * 1024 * 1024L)) {
                com.example.accessiblevideoeditor.utils.StorageUtils.showLowSpaceWarning(ctx, view)
                return
            }
            val vc = videoCapture ?: run {
                Toast.makeText(context, "الكاميرا غير جاهزة للتسجيل", Toast.LENGTH_SHORT).show()
                return
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "AccessibleVideo_${System.currentTimeMillis()}")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }

            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                ctx.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            try {
                activeRecording = vc.output
                    .prepareRecording(ctx, mediaStoreOutput)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(ctx)) { event ->
                        when (event) {
                            is VideoRecordEvent.Finalize -> {
                                if (event.hasError()) {
                                    Toast.makeText(ctx, "فشل حفظ الفيديو: ${event.cause?.message}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(ctx, getString(R.string.guidance_video_saved), Toast.LENGTH_SHORT).show()
                                    announceGuidance(getString(R.string.guidance_video_saved))
                                }
                            }
                        }
                    }

                isRecording = true
                binding.btnShutter.text = "⏹️ إيقاف"
                binding.btnShutter.contentDescription = "زر إيقاف تسجيل الفيديو"
                announceGuidance("بدأ تسجيل الفيديو")
                vibrateFeedback(100)
                if (vibrationGuidanceEnabled) {
                    startRecordingVibrationLoop()
                }
            } catch (e: SecurityException) {
                Toast.makeText(ctx, "لا توجد صلاحية تسجيل الصوت", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecordingVibrationLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isRecording && vibrationGuidanceEnabled) {
                vibrateFeedback(50)
                delay(1500)
            }
        }
    }

    private var pendingAnnouncementJob: kotlinx.coroutines.Job? = null
    private var lastAnnouncedText: String = ""

    private fun announceGuidance(text: String) {
        if (_binding == null || view == null || !isAdded) return
        val now = System.currentTimeMillis()
        // Minimum 3.5s between announcements to let TalkBack finish speaking
        if (now - lastGuidanceTime < 3500) return
        // Don't repeat the exact same text consecutively
        if (text == lastAnnouncedText && now - lastGuidanceTime < 8000) return

        // Cancel any pending announcement to prevent collision
        pendingAnnouncementJob?.cancel()

        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return

        pendingAnnouncementJob = lifecycleOwner.lifecycleScope.launch {
            // Small debounce: wait 300ms to collect the "final" text
            kotlinx.coroutines.delay(300)
            val currentBinding = _binding ?: return@launch
            if (view == null || !isAdded) return@launch

            currentBinding.tvCameraGuidance.text = text
            lastAnnouncedText = text
            lastGuidanceTime = System.currentTimeMillis()

            // Use AccessibilityEvent to interrupt and replace any queued speech
            try {
                val contextRef = context ?: return@launch
                if (com.example.accessiblevideoeditor.ui.AccessibilityUtils.isAccessibilityEnabled(contextRef)) {
                    val accessibilityManager = contextRef.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                    accessibilityManager?.interrupt()
                    val event = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    event.text.add(text)
                    event.className = javaClass.name
                    event.packageName = contextRef.packageName
                    try {
                        currentBinding.tvCameraGuidance.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    } catch (_: Exception) {}
                    try {
                        activity?.window?.decorView?.rootView?.sendAccessibilityEventUnchecked(event)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                try {
                    currentBinding.tvCameraGuidance.announceForAccessibility(text)
                } catch (_: Exception) {}
            }
        }
    }

    /** Immediate announcement that bypasses throttle - for countdown, critical alerts */
    private fun announceImmediate(text: String) {
        val currentBinding = _binding ?: return
        if (view == null || !isAdded) return
        pendingAnnouncementJob?.cancel()
        currentBinding.tvCameraGuidance.text = text
        lastAnnouncedText = text
        lastGuidanceTime = System.currentTimeMillis()
        try {
            val contextRef = context ?: return
            val accessibilityManager = contextRef.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            if (accessibilityManager != null && accessibilityManager.isEnabled) {
                accessibilityManager.interrupt()
                val event = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(text)
                event.className = javaClass.name
                event.packageName = contextRef.packageName
                try {
                    activity?.window?.decorView?.rootView?.sendAccessibilityEventUnchecked(event)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try {
                currentBinding.tvCameraGuidance.announceForAccessibility(text)
            } catch (_: Exception) {}
        }
    }

    private fun playBeepGuidance(centered: Boolean) {
        if (!beepingGuidanceEnabled) return
        val now = System.currentTimeMillis()
        val interval = if (centered) 300L else 1200L
        if (now - lastBeepTime > interval) {
            val tone = if (centered) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
            toneGenerator?.startTone(tone, 100)
            lastBeepTime = now
        }
    }

    private fun vibrateFeedback(durationMs: Long) {
        if (!hapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: SecurityException) {
            // Silently ignore permission errors
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !tiltGuidanceEnabled) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val angleRad = atan2(-ax.toDouble(), ay.toDouble())
            val angleDeg = angleRad * (180.0 / Math.PI)
            val now = System.currentTimeMillis()

            // Only announce tilt every 8 seconds AND only if direction changed
            if (now - lastTiltAnnounceTime < 8000) return

            val direction: String? = when {
                angleDeg < -10.0 -> getString(R.string.guidance_tilt_left)
                angleDeg > 10.0 -> getString(R.string.guidance_tilt_right)
                else -> null // Phone is level enough, no announcement needed
            }

            if (direction != null && direction != lastTiltDirection) {
                lastTiltDirection = direction
                lastTiltAnnounceTime = now
                announceGuidance(direction)
                vibrateFeedback(80)
            } else if (direction == null && lastTiltDirection.isNotEmpty()) {
                // Phone leveled — reset so next tilt will be announced
                lastTiltDirection = ""
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startAmbientSoundscape() {
        val activeSoundscape = prefs.getString("pref_camera_soundscape", "none") ?: "none"
        val soundRes = when (activeSoundscape) {
            "rain" -> R.raw.ambient_rain
            "keyboard" -> R.raw.ambient_keyboard
            else -> 0
        }
        if (soundRes != 0) {
            try {
                backgroundMusicPlayer?.release()
                backgroundMusicPlayer = MediaPlayer.create(requireContext(), soundRes).apply {
                    isLooping = true
                    setVolume(0.15f, 0.15f)
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopAmbientSoundscape() {
        backgroundMusicPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        backgroundMusicPlayer = null
    }

    private fun playRawSound(resId: Int) {
        try {
            MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showMoreOptionsDialog() {
        val modes = CameraSubMode.values()
        val names = modes.map { it.arabicName }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("اختر وضع الكاميرا المخصص")
            .setItems(names) { dialog, which ->
                val selected = modes[which]
                currentSubMode = selected
                announceGuidance("تم تفعيل وضع: ${selected.arabicName}")
                vibrateFeedback(100)
                dialog.dismiss()

                // Special handling for Find Lost Items
                if (selected == CameraSubMode.FIND_LOST_ITEMS) {
                    showLostItemSelectionDialog()
                }
            }
            .show()
    }

    private fun showLostItemSelectionDialog() {
        val items = arrayOf("كوب / فنجان", "هاتف محمول", "مفاتيح", "زجاجة مياه")
        val keys = arrayOf("cup", "cell phone", "keys", "bottle")

        AlertDialog.Builder(requireContext())
            .setTitle("ما هو الكائن الذي تبحث عنه؟")
            .setItems(items) { dialog, which ->
                lostItemTarget = keys[which]
                announceGuidance("جاري البحث عن: ${items[which]}")
                vibrateFeedback(80)
                dialog.dismiss()
            }
            .show()
    }

    // YUV Direct pixel average color reader
    private fun getCenterColorName(imageProxy: ImageProxy): String {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val pixelStride = yPlane.pixelStride
        val rowStride = yPlane.rowStride

        val cx = width / 2
        val cy = height / 2
        val index = cy * rowStride + cx * pixelStride

        if (index < yBuffer.capacity()) {
            val yValue = yBuffer.get(index).toInt() and 0xFF
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uvPixelStride = uPlane.pixelStride
            val uvRowStride = uPlane.rowStride

            val uvIndex = (cy / 2) * uvRowStride + (cx / 2) * uvPixelStride
            if (uvIndex < uBuffer.capacity() && uvIndex < vBuffer.capacity()) {
                val uValue = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vValue = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                val r = (yValue + 1.370705 * vValue).roundToInt().coerceIn(0, 255)
                val g = (yValue - 0.337633 * uValue - 0.698001 * vValue).roundToInt().coerceIn(0, 255)
                val b = (yValue + 1.732446 * uValue).roundToInt().coerceIn(0, 255)

                return rgbToColorName(r, g, b)
            }
        }
        return "غير معروف"
    }

    private fun rgbToColorName(r: Int, g: Int, b: Int): String {
        if (r < 45 && g < 45 && b < 45) return "أسود"
        if (r > 210 && g > 210 && b > 210) return "أبيض"
        if (abs(r - g) < 25 && abs(g - b) < 25) return "رمادي"

        val max = maxOf(r, g, b)
        if (max == r) {
            if (g > 140 && b < 60) return "برتقالي"
            if (g > 190) return "أصفر"
            if (b > 140) return "بنفسجي"
            return "أحمر"
        }
        if (max == g) {
            if (r > 190) return "أصفر"
            return "أخضر"
        }
        if (max == b) {
            if (r > 140) return "بنفسجي"
            return "أزرق"
        }
        return "رمادي"
    }

    private fun getCenterColorNameRegion(imageProxy: ImageProxy, regionYPercent: Float): String {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val pixelStride = yPlane.pixelStride
        val rowStride = yPlane.rowStride

        val cx = width / 2
        val cy = (height * regionYPercent).roundToInt()
        val index = cy * rowStride + cx * pixelStride

        if (index < yBuffer.capacity()) {
            val yValue = yBuffer.get(index).toInt() and 0xFF
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uvPixelStride = uPlane.pixelStride
            val uvRowStride = uPlane.rowStride

            val uvIndex = (cy / 2) * uvRowStride + (cx / 2) * uvPixelStride
            if (uvIndex < uBuffer.capacity() && uvIndex < vBuffer.capacity()) {
                val uValue = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val vValue = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                val r = (yValue + 1.370705 * vValue).roundToInt().coerceIn(0, 255)
                val g = (yValue - 0.337633 * uValue - 0.698001 * vValue).roundToInt().coerceIn(0, 255)
                val b = (yValue + 1.732446 * uValue).roundToInt().coerceIn(0, 255)

                return rgbToColorName(r, g, b)
            }
        }
        return "غير معروف"
    }

    private fun getOutfitRecommendation(shirtColor: String, pantsColor: String): String {
        if (shirtColor == "أسود" || pantsColor == "أسود" || shirtColor == "أبيض" || pantsColor == "أبيض" || shirtColor == "رمادي" || pantsColor == "رمادي") {
            return "الألوان متناسقة جداً ومناسبة للارتداء."
        }
        if (shirtColor == pantsColor) {
            return "ألوان متطابقة تماماً."
        }
        if ((shirtColor == "أزرق" && pantsColor == "بني") || (shirtColor == "أحمر" && pantsColor == "أزرق") || (shirtColor == "أصفر" && pantsColor == "أزرق")) {
            return "الألوان متناسقة ومريحة للعين."
        }
        return "تنبيه: قد لا تكون هذه الألوان متناسقة بشكل مثالي."
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        yBuffer.get(nv21, 0, ySize)

        var pos = ySize
        val uPixelStride = image.planes[1].pixelStride
        val uRowStride = image.planes[1].rowStride
        val vPixelStride = image.planes[2].pixelStride
        val vRowStride = image.planes[2].rowStride

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                nv21[pos++] = vBuffer.get(vIndex)
                nv21[pos++] = uBuffer.get(uIndex)
            }
        }

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0 && bitmap != null) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private suspend fun describeImageWithGemini(bitmap: Bitmap, prompt: String): String {
        val apiKey = SettingsManager.geminiApiKey.trim()
        if (apiKey.isBlank()) {
            return "يرجى إضافة مفتاح Gemini API من الإعدادات لاستخدام ميزات الذكاء الاصطناعي."
        }
        return try {
            val modelName = SettingsManager.geminiModel.ifBlank { "gemini-2.5-flash" }
            val model = GenerativeModel(modelName = modelName, apiKey = apiKey)
            val inputContent = content {
                image(bitmap)
                text(prompt)
            }
            model.generateContent(inputContent).text ?: "فشل الحصول على وصف للقطة."
        } catch (e: Exception) {
            e.printStackTrace()
            "خطأ أثناء الاتصال بالذكاء الاصطناعي."
        }
    }

    // Inner Image Analyzer Class
    private inner class ImageFrameAnalyzer : ImageAnalysis.Analyzer {

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (_binding == null || view == null || !isAdded) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            val width = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
            val height = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

            when (currentSubMode) {
                CameraSubMode.FACE_GUIDE -> {
                    // Guide Face
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                            if (faces.isNotEmpty()) {
                                handleFaceGuidance(faces[0], width, height)
                            } else {
                                autoCaptureJob?.cancel()
                                announceGuidance("لم يتم رصد وجه")
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                CameraSubMode.TEXT_OCR, CameraSubMode.MEDICINE_HELPER, CameraSubMode.SCRATCH_CARD, CameraSubMode.EXPIRY_DATE, CameraSubMode.APPLIANCE_READER, CameraSubMode.DEVICE_BATTERY -> {
                    // OCR based modes — wrapped in try-catch to guarantee imageProxy.close()
                    try {
                        textRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                                val detectedText = visionText.text
                                if (detectedText.isNotBlank()) {
                                    handleTextOcrMode(detectedText)
                                }
                            }
                            .addOnFailureListener { imageProxy.close() }
                            .addOnCompleteListener { imageProxy.close() }
                    } catch (_: Exception) {
                        imageProxy.close()
                    }
                }
                CameraSubMode.CREDIT_CARD -> {
                    try {
                        textRecognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                                val detectedText = visionText.text

                                // 1. Check if we can already parse a valid card number in the text
                                val cardPatternRegex = Regex("\\b\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}\\b|\\b\\d{16}\\b|\\b\\d{4}[ -]\\d{6}[ -]\\d{5}\\b")
                                var cardNum = cardPatternRegex.find(detectedText)?.value?.replace(" ", "")?.replace("-", "")
                                if (cardNum == null) {
                                    val lines = detectedText.split("\n", "\r")
                                    for (line in lines) {
                                        val digits = line.replace(Regex("[^0-9]"), "")
                                        if (digits.length in 13..19) {
                                            cardNum = digits
                                            break
                                        }
                                    }
                                }

                                if (cardNum != null && cardNum.length >= 13) {
                                    // Found! Announce immediately and play success beep
                                    handleTextOcrMode(detectedText)
                                    playCardBeepGuidance(centered = true, distanceOk = true)
                                } else {
                                    // 2. Guide user to align the card
                                    val blocks = visionText.textBlocks
                                    if (blocks.isEmpty()) {
                                        playCardBeepGuidance(centered = false, distanceOk = false)
                                    } else {
                                        var minLeft = Int.MAX_VALUE
                                        var minTop = Int.MAX_VALUE
                                        var maxRight = Int.MIN_VALUE
                                        var maxBottom = Int.MIN_VALUE
                                        for (block in blocks) {
                                            val box = block.boundingBox ?: continue
                                            if (box.left < minLeft) minLeft = box.left
                                            if (box.top < minTop) minTop = box.top
                                            if (box.right > maxRight) maxRight = box.right
                                            if (box.bottom > maxBottom) maxBottom = box.bottom
                                        }

                                        if (minLeft != Int.MAX_VALUE) {
                                            val boxCenterX = (minLeft + maxRight) / 2
                                            val boxCenterY = (minTop + maxBottom) / 2
                                            val imgCenterX = width / 2
                                            val imgCenterY = height / 2
                                            val xDiff = boxCenterX - imgCenterX
                                            val yDiff = boxCenterY - imgCenterY
                                            val xDiffAbs = if (xDiff < 0) -xDiff else xDiff
                                            val yDiffAbs = if (yDiff < 0) -yDiff else yDiff

                                            val toleranceX = (width * 0.35).toInt()
                                            val toleranceY = (height * 0.35).toInt()
                                            val centered = xDiffAbs < toleranceX && yDiffAbs < toleranceY

                                            val totalWidth = maxRight - minLeft
                                            val distanceOk = totalWidth >= (width * 0.25)

                                            playCardBeepGuidance(centered, distanceOk)

                                            val now = System.currentTimeMillis()
                                            if (now - lastCardTipAnnounceTime > 4000) {
                                                lastCardTipAnnounceTime = now
                                                if (!centered) {
                                                    announceGuidance("حرّك الهاتف لتوسيط البطاقة")
                                                } else {
                                                    announceGuidance("قرّب البطاقة أكثر من الكاميرا")
                                                }
                                            }
                                        } else {
                                            playCardBeepGuidance(centered = false, distanceOk = false)
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener { imageProxy.close() }
                            .addOnCompleteListener { imageProxy.close() }
                    } catch (_: Exception) {
                        imageProxy.close()
                    }
                }
                CameraSubMode.QR_BARCODE -> {
                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                            if (barcodes.isNotEmpty()) {
                                val barcode = barcodes[0]
                                if (!isBarcodeDialogShowing) {
                                    isBarcodeDialogShowing = true
                                    handleBarcodeTapped(barcode)
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                CameraSubMode.COLOR_IDENTIFIER -> {
                    val colorName = getCenterColorName(imageProxy)
                    val now = System.currentTimeMillis()
                    if (now - lastObjectAnnounceTime > 2500) {
                        announceGuidance("اللون في المنتصف: $colorName")
                        lastObjectAnnounceTime = now
                    }
                    imageProxy.close()
                }
                CameraSubMode.OUTFIT_MATCHER -> {
                    val shirtColor = getCenterColorNameRegion(imageProxy, 0.33f)
                    val pantsColor = getCenterColorNameRegion(imageProxy, 0.66f)
                    val now = System.currentTimeMillis()
                    if (now - lastObjectAnnounceTime > 3500) {
                        val rec = getOutfitRecommendation(shirtColor, pantsColor)
                        announceGuidance("القميص: $shirtColor، والبنطال: $pantsColor. $rec")
                        lastObjectAnnounceTime = now
                    }
                    imageProxy.close()
                }
                CameraSubMode.FIND_LOST_ITEMS -> {
                    objectLabeler.process(inputImage)
                        .addOnSuccessListener { labels ->
                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                            val targetLabel = labels.find { it.text.lowercase().contains(lostItemTarget) }
                            if (targetLabel != null) {
                                playBeepGuidance(true)
                                vibrateFeedback(150)
                                announceGuidance("تم رصد الكائن المفقود!")
                            } else {
                                announceGuidance("البحث مستمر...")
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                CameraSubMode.AI_SCENE_DESC, CameraSubMode.HANDWRITING_OCR -> {
                    val now = System.currentTimeMillis()
                    if (now - lastObjectAnnounceTime > 6000) {
                        lastObjectAnnounceTime = now
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            val prompt = if (currentSubMode == CameraSubMode.HANDWRITING_OCR) {
                                "يرجى قراءة الكلمات المكتوبة بخط اليد باللغة العربية في الصورة بالكامل ونطقها."
                            } else {
                                "صف المشهد الحالي بجملة مفيدة وموجزة باللغة العربية لمساعدة شخص كفيف."
                            }
                            val lifecycleOwner = try { viewLifecycleOwner } catch (_: Exception) { null }
                            if (lifecycleOwner != null) {
                                lifecycleOwner.lifecycleScope.launch {
                                    try {
                                        val resultDesc = describeImageWithGemini(bitmap, prompt)
                                        announceGuidance(resultDesc)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            } else {
                                bitmap.recycle()
                            }
                        }
                    }
                    imageProxy.close()
                }
                CameraSubMode.LED_READER -> {
                    val centerColor = getCenterColorName(imageProxy)
                    val now = System.currentTimeMillis()
                    if (now - lastObjectAnnounceTime > 3000) {
                        if (centerColor == "أخضر" || centerColor == "أحمر" || centerColor == "أزرق" || centerColor == "أصفر") {
                            announceGuidance("ضوء تنبيه نشط: $centerColor")
                        } else {
                            announceGuidance("لا يوجد ضوء تنبيه في المنتصف")
                        }
                        lastObjectAnnounceTime = now
                    }
                    imageProxy.close()
                }
                CameraSubMode.SIGN_LANGUAGE -> {
                    objectLabeler.process(inputImage)
                        .addOnSuccessListener { labels ->
                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                            val handLabel = labels.find { it.text.lowercase().contains("hand") || it.text.lowercase().contains("gesture") }
                            if (handLabel != null) {
                                announceGuidance("تم رصد حركة إشارة باليد")
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                CameraSubMode.AUTO -> {
                    // Runs face guide if face is present, otherwise falls back to object labeler
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                            if (faces.isNotEmpty()) {
                                handleFaceGuidance(faces[0], width, height)
                            } else {
                                autoCaptureJob?.cancel()
                                // Fallback to object labeler on back camera
                                if (lensFacing == CameraSelector.LENS_FACING_BACK && objectDetectorEnabled) {
                                    objectLabeler.process(inputImage)
                                        .addOnSuccessListener { labels ->
                                            if (_binding == null || view == null || !isAdded) return@addOnSuccessListener
                                            if (labels.isNotEmpty()) {
                                                val topLabels = labels.take(3)
                                                val descriptions = topLabels.map { label ->
                                                    val confidence = (label.confidence * 100).roundToInt()
                                                    "${label.text} (ثقة ${confidence}%)"
                                                }
                                                val text = "أمام الكاميرا: ${descriptions.joinToString("، ")}"
                                                val currentBinding = _binding
                                                if (currentBinding != null) {
                                                    currentBinding.tvObjectDescription.post {
                                                        if (_binding != null) {
                                                            currentBinding.tvObjectDescription.text = text
                                                        }
                                                    }
                                                }
                                                val now = System.currentTimeMillis()
                                                if (now - lastObjectAnnounceTime > 3000) {
                                                    announceGuidance(text)
                                                    lastObjectAnnounceTime = now
                                                }
                                            }
                                        }
                                } else {
                                    announceGuidance("البحث عن كائنات...")
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }
        }
    }

    private fun handleFaceGuidance(face: com.google.mlkit.vision.face.Face, width: Int, height: Int) {
        val currentBinding = _binding ?: return
        if (view == null || !isAdded) return
        val bounds = face.boundingBox
        val faceX = bounds.centerX()
        val faceY = bounds.centerY()

        val imgCenterX = width / 2
        val imgCenterY = height / 2

        val xDiff = faceX - imgCenterX
        val yDiff = faceY - imgCenterY

        val toleranceX = (width * 0.15).roundToInt()
        val toleranceY = (height * 0.15).roundToInt()

        val centeredX = abs(xDiff) < toleranceX
        val centeredY = abs(yDiff) < toleranceY
        val centered = centeredX && centeredY

        playBeepGuidance(centered)
        
        if (centered) {
            currentBinding.tvCameraGuidance.text = getString(R.string.guidance_face_centered)
            val leftOpen = face.leftEyeOpenProbability ?: 1.0f
            val rightOpen = face.rightEyeOpenProbability ?: 1.0f
            if (leftOpen < 0.4f || rightOpen < 0.4f) {
                announceGuidance(getString(R.string.guidance_eyes_closed))
            } else {
                announceGuidance(getString(R.string.guidance_face_centered))
                if (autoCaptureEnabled && !isCapturing) {
                    autoCaptureJob?.cancel()
                    val lifecycleOwner = try { viewLifecycleOwner } catch (_: Exception) { null }
                    if (lifecycleOwner != null) {
                        autoCaptureJob = lifecycleOwner.lifecycleScope.launch {
                            delay(1200)
                            triggerPhotoCaptureFlow()
                        }
                    }
                }
            }
        } else {
            autoCaptureJob?.cancel()
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                if (xDiff > toleranceX) {
                    announceGuidance(getString(R.string.guidance_move_right)) // Corrected front right
                } else if (xDiff < -toleranceX) {
                    announceGuidance(getString(R.string.guidance_move_left))  // Corrected front left
                }
            } else {
                if (xDiff > toleranceX) {
                    announceGuidance(getString(R.string.guidance_move_left))  // Corrected back left
                } else if (xDiff < -toleranceX) {
                    announceGuidance(getString(R.string.guidance_move_right)) // Corrected back right
                }
            }

            if (abs(xDiff) <= toleranceX * 1.5) {
                if (yDiff > toleranceY) {
                    announceGuidance(getString(R.string.guidance_move_down)) // Corrected vertical down
                } else if (yDiff < -toleranceY) {
                    announceGuidance(getString(R.string.guidance_move_up))   // Corrected vertical up
                }
            }
        }
    }

    private fun handleTextOcrMode(detectedText: String) {
        val now = System.currentTimeMillis()
        val isCreditCard = currentSubMode == CameraSubMode.CREDIT_CARD
        if (isCreditCard || now - lastObjectAnnounceTime > 3000) {
            when (currentSubMode) {
                CameraSubMode.TEXT_OCR -> {
                    announceGuidance("النص المقروء: ${detectedText.take(150)}")
                }
                CameraSubMode.MEDICINE_HELPER -> {
                    val cleanText = detectedText.lowercase()
                    if (cleanText.contains("panadol") || cleanText.contains("بندول") || cleanText.contains("بنادول")) {
                        announceGuidance("دواء بنادول، التركيز 500 ملجم، مسكن للآلام وخافض للحرارة")
                    } else if (cleanText.contains("aspirin") || cleanText.contains("أسبرين")) {
                        announceGuidance("دواء أسبرين، مسيل ومميع للدم وحماية القلب")
                    } else {
                        announceGuidance("دواء طبي محتمل: ${detectedText.take(60)}")
                    }
                }
                CameraSubMode.SCRATCH_CARD -> {
                    val digits = "\\d{14,16}".toRegex().find(detectedText)?.value
                    if (digits != null) {
                        announceGuidance("تم رصد كارت شحن برقم: $digits. يمكنك نسخه لطلب الكود مباشرة")
                    }
                }
                CameraSubMode.EXPIRY_DATE -> {
                    val dateRegex = "\\d{2}[/-]\\d{2}[/-]\\d{2,4}".toRegex().find(detectedText)?.value
                    if (dateRegex != null) {
                        announceGuidance("تاريخ انتهاء الصلاحية: $dateRegex")
                    } else if (detectedText.lowercase().contains("exp") || detectedText.contains("انتهاء")) {
                        announceGuidance("تم رصد ملصق انتهاء الصلاحية: ${detectedText.take(50)}")
                    }
                }
                CameraSubMode.APPLIANCE_READER -> {
                    val temp = "\\d{2}C|\\d{2}\\s*درجة".toRegex().find(detectedText)?.value
                    if (temp != null) {
                        announceGuidance("شاشة الجهاز تعرض: $temp")
                    } else {
                        announceGuidance("شاشة الجهاز: ${detectedText.take(50)}")
                    }
                }
                CameraSubMode.CREDIT_CARD -> {
                    // Extract via regex matching structured sequences (space or dash separated groups of 4 digits, or 16 consecutive digits)
                    val cardPatternRegex = Regex("\\b\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{4}\\b|\\b\\d{16}\\b|\\b\\d{4}[ -]\\d{6}[ -]\\d{5}\\b")
                    var cardNum = cardPatternRegex.find(detectedText)?.value?.replace(" ", "")?.replace("-", "")

                    if (cardNum == null) {
                        // Fallback: search individual lines for any sequence of 13 to 19 digits (avoids mixing expiry date etc.)
                        val lines = detectedText.split("\n", "\r")
                        for (line in lines) {
                            val digits = line.replace(Regex("[^0-9]"), "")
                            if (digits.length in 13..19) {
                                cardNum = digits
                                break
                            }
                        }
                    }

                    if (cardNum != null && cardNum.length >= 13) {
                        val firstFour = cardNum.take(4)
                        val lastFour = cardNum.takeLast(4)
                        val cardType = when {
                            firstFour.startsWith("4") -> "فيزا"
                            firstFour.startsWith("5") || firstFour.startsWith("2") -> "ماستر كارد"
                            firstFour.startsWith("3") -> "أمريكان إكسبريس"
                            firstFour.startsWith("6") -> "ديسكفر"
                            else -> "بطاقة ائتمانية"
                        }
                        val ctx = context
                        if (ctx != null) {
                            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Credit Card Number", cardNum)
                            clipboard.setPrimaryClip(clip)
                        }
                        announceGuidance("تم رصد $cardType: الرقم يبدأ بـ $firstFour وينتهي بـ $lastFour. تم نسخ الرقم بالكامل إلى الحافظة.")
                        vibrateFeedback(200)
                        lastObjectAnnounceTime = now
                    } else {
                        // Helpful interactive tip announced every 6 seconds if no card is read
                        if (now - lastCardTipAnnounceTime > 6000) {
                            lastCardTipAnnounceTime = now
                            announceGuidance("لم يتم العثور على أرقام بطاقة. قرّب البطاقة أكثر من الكاميرا وتأكد من الإضاءة، أو اقلب البطاقة على الوجه الآخر.")
                        }
                    }
                }
                CameraSubMode.DEVICE_BATTERY -> {
                    val percentage = "\\d{1,3}%".toRegex().find(detectedText)?.value
                    if (percentage != null) {
                        announceGuidance("نسبة شحن الجهاز المقابل: $percentage")
                    }
                }
                else -> {}
            }
            if (currentSubMode != CameraSubMode.CREDIT_CARD) {
                lastObjectAnnounceTime = now
            }
        }
    }

    private fun showSettingsDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_camera_settings, null)

        val swAutoCapture = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swAutoCapture)
        val swHaptics = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swHaptics)
        val swTilt = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swTilt)
        val swObject = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swObject)
        val swBeeping = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swBeeping)
        val swVibRecord = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.swVibRecord)
        val spinnerTimer = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerTimer)
        val btnViewScanHistory = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewScanHistory)

        swAutoCapture.isChecked = prefs.getBoolean("pref_auto_capture", true)
        swHaptics.isChecked = prefs.getBoolean("pref_haptics", true)
        swTilt.isChecked = prefs.getBoolean("pref_tilt_guidance", true)
        swObject.isChecked = prefs.getBoolean("pref_object_detector", true)
        swBeeping.isChecked = prefs.getBoolean("pref_beeping_guidance", true)
        swVibRecord.isChecked = prefs.getBoolean("pref_vibration_guidance", false)

        val timerOptions = listOf("بدون مؤقت", "3 ثوانٍ", "5 ثوانٍ", "10 ثوانٍ")
        val timerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, timerOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTimer.adapter = timerAdapter
        
        val currentTimer = prefs.getInt("pref_camera_timer", 3)
        val selIdx = when (currentTimer) {
            0 -> 0
            3 -> 1
            5 -> 2
            10 -> 3
            else -> 1
        }
        spinnerTimer.setSelection(selIdx)

        btnViewScanHistory.setOnClickListener {
            showScanHistoryDialog()
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.camera_settings_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_ok)) { dialog, _ ->
                prefs.edit().apply {
                    putBoolean("pref_auto_capture", swAutoCapture.isChecked)
                    putBoolean("pref_haptics", swHaptics.isChecked)
                    putBoolean("pref_tilt_guidance", swTilt.isChecked)
                    putBoolean("pref_object_detector", swObject.isChecked)
                    putBoolean("pref_beeping_guidance", swBeeping.isChecked)
                    putBoolean("pref_vibration_guidance", swVibRecord.isChecked)
                    
                    val selectedTimer = when (spinnerTimer.selectedItemPosition) {
                        0 -> 0
                        1 -> 3
                        2 -> 5
                        3 -> 10
                        else -> 3
                    }
                    putInt("pref_camera_timer", selectedTimer)
                    apply()
                }
                loadSettings()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showScanHistoryDialog() {
        val history = prefs.getStringSet("pref_scan_history", emptySet()) ?: emptySet()
        val sortedHistory = history.mapNotNull {
            val parts = it.split("|", limit = 2)
            if (parts.size == 2) {
                val time = parts[0].toLongOrNull() ?: 0L
                val value = parts[1]
                time to value
            } else null
        }.sortedByDescending { it.first }

        val displayList = sortedHistory.map {
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.first))
            "$date\n${it.second}"
        }.toTypedArray()

        if (displayList.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("سجل مسح الرموز")
                .setMessage("السجل فارغ حالياً.")
                .setPositiveButton("موافق") { d, _ -> d.dismiss() }
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("سجل مسح الرموز")
            .setItems(displayList) { dialog, which ->
                val selectedItem = sortedHistory[which].second
                AlertDialog.Builder(requireContext())
                    .setTitle("تفاصيل الرمز")
                    .setMessage(selectedItem)
                    .setPositiveButton("نسخ") { d, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Scanned Code", selectedItem)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "تم نسخ الرمز إلى الحافظة", Toast.LENGTH_SHORT).show()
                        d.dismiss()
                    }
                    .setNegativeButton("حذف") { d, _ ->
                        val newHistory = prefs.getStringSet("pref_scan_history", emptySet())?.toMutableSet() ?: mutableSetOf()
                        val targetEntry = history.find { it.endsWith("|$selectedItem") }
                        if (targetEntry != null) {
                            newHistory.remove(targetEntry)
                            prefs.edit().putStringSet("pref_scan_history", newHistory).apply()
                            Toast.makeText(requireContext(), "تم حذف الرمز من السجل", Toast.LENGTH_SHORT).show()
                        }
                        d.dismiss()
                        dialog.dismiss()
                    }
                    .setNeutralButton("إلغاء") { d, _ -> d.dismiss() }
                    .show()
            }
            .setNeutralButton("مسح الكل") { dialog, _ ->
                prefs.edit().remove("pref_scan_history").apply()
                Toast.makeText(requireContext(), "تم مسح السجل بالكامل", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setPositiveButton("إغلاق") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun playCardBeepGuidance(centered: Boolean, distanceOk: Boolean) {
        if (!beepingGuidanceEnabled) return
        val now = System.currentTimeMillis()
        if (centered && distanceOk) {
            if (now - lastBeepTime > 150) {
                toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 80)
                lastBeepTime = now
            }
        } else if (centered && !distanceOk) {
            if (now - lastBeepTime > 400) {
                toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 60)
                lastBeepTime = now
            }
        } else {
            if (now - lastBeepTime > 800) {
                toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 40)
                lastBeepTime = now
            }
        }
    }

    private fun handleBarcodeTapped(barcode: com.google.mlkit.vision.barcode.common.Barcode) {
        val context = context ?: return
        val rawValue = barcode.rawValue ?: barcode.displayValue ?: ""
        if (rawValue.isBlank()) {
            isBarcodeDialogShowing = false
            return
        }

        vibrateFeedback(100)
        try {
            com.example.accessiblevideoeditor.media.SoundManager.playSuccess()
        } catch (_: Exception) {}

        val valueType = barcode.valueType
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)

        when (valueType) {
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_WIFI -> {
                val wifi = barcode.wifi
                val ssid = wifi?.ssid ?: ""
                val password = wifi?.password ?: ""
                val type = when (wifi?.encryptionType) {
                    com.google.mlkit.vision.barcode.common.Barcode.WiFi.TYPE_OPEN -> "مفتوحة"
                    com.google.mlkit.vision.barcode.common.Barcode.WiFi.TYPE_WEP -> "WEP"
                    com.google.mlkit.vision.barcode.common.Barcode.WiFi.TYPE_WPA -> "WPA"
                    else -> "غير معروفة"
                }

                builder.setTitle("الاتصال بشبكة واي فاي")
                builder.setMessage("تم رصد كود شبكة واي فاي:\n\nاسم الشبكة (SSID): $ssid\nنوع التشفير: $type\n\nهل تريد نسخ كلمة المرور وفتح إعدادات الواي فاي للاتصال بها؟")
                builder.setPositiveButton("نسخ واتصال") { d, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Wifi Password", password)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "تم نسخ كلمة المرور للشبكة $ssid", Toast.LENGTH_LONG).show()

                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    d.dismiss()
                }
                builder.setNegativeButton("إلغاء") { d, _ -> d.dismiss() }
            }
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                builder.setTitle("رابط موقع إلكتروني")
                builder.setMessage("تم رصد رابط موقع:\n\n$url\n\nهل تريد فتح الرابط في متصفح الإنترنت؟")
                builder.setPositiveButton("فتح الرابط") { d, _ ->
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                    }
                    d.dismiss()
                }
                builder.setNegativeButton("إلغاء") { d, _ -> d.dismiss() }
            }
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_PHONE -> {
                val phone = barcode.phone?.number ?: rawValue
                builder.setTitle("رقم هاتف")
                builder.setMessage("تم رصد رقم هاتف:\n\n$phone\n\nهل تريد الاتصال بالرقم؟")
                builder.setPositiveButton("اتصال") { d, _ ->
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    d.dismiss()
                }
                builder.setNegativeButton("إلغاء") { d, _ -> d.dismiss() }
            }
            else -> {
                builder.setTitle("تفاصيل الباركود")
                builder.setMessage("تم رصد رمز يحتوي على:\n\n$rawValue")
                builder.setPositiveButton("نسخ النص") { d, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Barcode Text", rawValue)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "تم نسخ النص إلى الحافظة", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                }
                builder.setNegativeButton("إغلاق") { d, _ -> d.dismiss() }
            }
        }

        val dialog = builder.create()
        dialog.setOnDismissListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(2500)
                isBarcodeDialogShowing = false
            }
        }
        dialog.show()

        val announceText = when (valueType) {
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_WIFI -> {
                val ssid = barcode.wifi?.ssid ?: ""
                "تنبيه الاتصال بشبكة واي فاي: تم رصد شبكة $ssid. اضغط على زر نسخ واتصال لنسخ كلمة المرور وفتح إعدادات الهاتف."
            }
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_URL -> {
                val url = barcode.url?.url ?: rawValue
                "تنبيه رابط موقع إلكتروني: تم رصد رابط $url. اضغط على زر فتح الرابط لتصفحه."
            }
            com.google.mlkit.vision.barcode.common.Barcode.TYPE_PHONE -> {
                val phone = barcode.phone?.number ?: rawValue
                "تنبيه رقم هاتف: تم رصد رقم $phone. اضغط على زر اتصال للاتصال به."
            }
            else -> "تنبيه تفاصيل الرمز المكتشف: تم رصد نص $rawValue. اضغط على زر نسخ النص لحفظه."
        }
        announceGuidance(announceText)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Cancel all coroutine jobs to prevent accessing destroyed binding
        pendingAnnouncementJob?.cancel()
        autoCaptureJob?.cancel()
        countdownJob?.cancel()

        // Stop active recording if running
        try {
            activeRecording?.stop()
        } catch (_: Exception) {}
        activeRecording = null
        isRecording = false

        // Release ML Kit clients
        try { faceDetector.close() } catch (_: Exception) {}
        try { objectLabeler.close() } catch (_: Exception) {}
        try { textRecognizer.close() } catch (_: Exception) {}
        try { barcodeScanner.close() } catch (_: Exception) {}

        // Release audio resources
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null

        // Shutdown camera executor
        cameraExecutor?.shutdown()
        cameraExecutor = null
        _binding = null
    }
}
