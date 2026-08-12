package com.example.accessiblevideoeditor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.updater.AppUpdater
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import com.example.accessiblevideoeditor.ui.AccessibilityUtils

class MainActivity : AppCompatActivity() {
  private val fragmentStack = mutableListOf<String>()
  private val lastFocusedViewIdMap = mutableMapOf<String, Int>()
  private var isAppReturningFromBackground = false
  private var wasProcessing = false

  fun saveLastFocusedViewId(fragmentName: String, viewId: Int) {
      if (viewId != View.NO_ID) {
          lastFocusedViewIdMap[fragmentName] = viewId
      }
  }

  override fun onStop() {
      super.onStop()
      isAppReturningFromBackground = true
  }

  /**
   * Preloads cached cloud translations before the Activity inflates any views.
   * All translation lookups go through AppStrings.get(context, resId) — no
   * Resources subclassing or LayoutInflater reflection is used.
   */
  override fun attachBaseContext(newBase: Context) {
      AppStrings.loadCustomStrings(newBase)
      super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Global Error Logger Setup
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        com.example.accessiblevideoeditor.utils.ErrorLogger.logError(
            applicationContext,
            "CRASH",
            "Uncaught crash in thread ${thread.name}",
            throwable
        )
        defaultHandler?.uncaughtException(thread, throwable)
    }

    // Initialize Managers
    com.example.accessiblevideoeditor.telemetry.CrashReporter.init(this)
    com.example.accessiblevideoeditor.ui.SettingsManager.init(this)
    SoundManager.init(this)
    SoundManager.playStartup()
    ProcessingManager.init(this)

    // Disable screen sleep
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Cleanup old update APK if it exists
    try {
        val updateFile = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AccessibleVideoEditor_Update.apk")
        if (updateFile.exists()) {
            updateFile.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Clean up temporary files in cache directory on startup in background
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { file ->
                try {
                    file.deleteRecursively()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // Request notification permission if Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    setContentView(R.layout.activity_main)

    setupProcessingOverlay()

    findViewById<View>(R.id.nav_host_fragment).post {
        handleUpdateIntent(intent)
        handleShareIntent(intent)
    }
    
        // Register global fragment lifecycle callbacks for accessibility focus
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState)
                val fragmentName = f.javaClass.simpleName
                v.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
                    if (newFocus != null && newFocus.id != View.NO_ID) {
                        lastFocusedViewIdMap[fragmentName] = newFocus.id
                    }
                }
                AccessibilityUtils.attachAccessibilityFocusTracker(v, fragmentName, lastFocusedViewIdMap)
            }

            override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
                super.onFragmentPaused(fm, f)
                val fragmentName = f.javaClass.simpleName
                f.view?.let { fragmentView ->
                    val focusedView = AccessibilityUtils.findAccessibilityFocusedView(fragmentView)
                    if (focusedView != null && focusedView.id != View.NO_ID) {
                        lastFocusedViewIdMap[fragmentName] = focusedView.id
                    }
                }
            }

            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                val fragmentName = f.javaClass.simpleName
                f.view?.let { fragmentView ->
                    // Programmatically set back button description to localized string (string_246)
                    val toolbar = AccessibilityUtils.findToolbar(fragmentView)
                    toolbar?.let {
                        val backDesc = com.example.accessiblevideoeditor.ui.AppStrings.get(this@MainActivity, R.string.string_246)
                        it.navigationContentDescription = backDesc
                    }

                    val isBackward = fragmentStack.size > 1 && fragmentStack[fragmentStack.size - 2] == fragmentName
                    val returningFromBg = isAppReturningFromBackground

                    if (returningFromBg) {
                        isAppReturningFromBackground = false
                        val savedId = lastFocusedViewIdMap[fragmentName]
                        if (savedId != null && savedId != View.NO_ID) {
                            val targetView = fragmentView.findViewById<View>(savedId)
                            if (targetView != null) {
                                AccessibilityUtils.focusView(targetView)
                                return@let
                            }
                        }
                    } else if (isBackward) {
                        // Pop the stack
                        fragmentStack.removeAt(fragmentStack.size - 1)
                        
                        // Try to restore focus to the item clicked before navigating
                        val savedId = lastFocusedViewIdMap[fragmentName]
                        if (savedId != null && savedId != View.NO_ID) {
                            val targetView = fragmentView.findViewById<View>(savedId)
                            if (targetView != null) {
                                AccessibilityUtils.focusView(targetView)
                                return@let
                            }
                        }
                    } else {
                        // Forward navigation: track it in stack
                        if (!fragmentStack.contains(fragmentName)) {
                            fragmentStack.add(fragmentName)
                        } else {
                            // Clean up forward trace if it somehow re-entered
                            val idx = fragmentStack.indexOf(fragmentName)
                            while (fragmentStack.size > idx + 1) {
                                fragmentStack.removeAt(fragmentStack.size - 1)
                            }
                        }
                    }
                    
                    // Fallback to top toolbar focus for new forward navigation
                    AccessibilityUtils.announceScreenChanged(fragmentView)
                }
            }
        }, true)
    
    // Launch update checker
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        try {
            val info = AppUpdater.checkForUpdate(this@MainActivity)
            if (info != null && !isFinishing && !isDestroyed) {
                AppUpdater.showUpdateNotification(this@MainActivity, info)
                kotlinx.coroutines.delay(600)
                if (!isFinishing && !isDestroyed) {
                    AppUpdater.showUpdateDialog(this@MainActivity, info)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    scheduleCloudPolling()
    checkBatteryOptimizations()
  }

  private fun scheduleCloudPolling() {
      try {
          lifecycleScope.launch(Dispatchers.IO) {
              try {
                  com.example.accessiblevideoeditor.telemetry.TelemetryManager.uploadTelemetryData(applicationContext)
              } catch (e: Exception) {
                  e.printStackTrace()
              }
          }

          val constraints = androidx.work.Constraints.Builder()
              .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
              .build()

          val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.accessiblevideoeditor.updater.CloudPollWorker>(
              30, java.util.concurrent.TimeUnit.MINUTES
          )
              .setConstraints(constraints)
              .build()

          androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
              "CloudPollWork",
              androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
              workRequest
          )

          val telemetryWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.accessiblevideoeditor.telemetry.TelemetryUploadWorker>(
              12, java.util.concurrent.TimeUnit.HOURS
          )
              .setConstraints(constraints)
              .build()

          androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
              "TelemetryUploadWork",
              androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
              telemetryWorkRequest
          )
      } catch (e: Exception) {
          e.printStackTrace()
      }
  }

  private fun checkBatteryOptimizations() {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
          try {
              val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
              if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                  val title = try { AppStrings.get(this, R.string.dialog_battery_title) } catch (_: Exception) { "تحسين استهلاك البطارية" }
                  val msg = try { AppStrings.get(this, R.string.dialog_battery_msg) } catch (_: Exception) { "يتم إغلاق عمليات المونتاج وفحص الإشعارات في الخلفية بواسطة نظام الأندرويد لتوفير الطاقة. يرجى استثناء التطبيق لضمان استقرار العمليات." }
                  val btnSettings = try { AppStrings.get(this, R.string.btn_go_to_settings) } catch (_: Exception) { "الذهاب للإعدادات" }

                  androidx.appcompat.app.AlertDialog.Builder(this)
                      .setTitle(if (title.isNotBlank()) title else "تحسين استهلاك البطارية")
                      .setMessage(if (msg.isNotBlank()) msg else "يتم إغلاق عمليات المونتاج وفحص الإشعارات في الخلفية بواسطة نظام الأندرويد لتوفير الطاقة. يرجى استثناء التطبيق لضمان استقرار العمليات.")
                      .setPositiveButton(if (btnSettings.isNotBlank()) btnSettings else "الذهاب للإعدادات") { d, _ ->
                          d.dismiss()
                          try {
                              val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                  data = android.net.Uri.parse("package:$packageName")
                              }
                              startActivity(intent)
                          } catch (e: Exception) {
                              try {
                                  val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                  startActivity(intent)
                              } catch (_: Exception) {}
                          }
                      }
                      .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                      .show()
              }
          } catch (e: Exception) {
              e.printStackTrace()
          }
      }
  }

  private fun setupProcessingOverlay() {
      val overlay = findViewById<View>(R.id.progressOverlay)
      val navHost = findViewById<View>(R.id.nav_host_fragment)
      val tvTitle = findViewById<TextView>(R.id.tvProgressTitle)
      val tvPercent = findViewById<TextView>(R.id.tvProgressPercent)
      val tvEta = findViewById<TextView>(R.id.tvProgressEta)
      val tvStatus = findViewById<TextView>(R.id.tvProgressStatus)
      val progressBar = findViewById<ProgressBar>(R.id.progressBar)
      val btnCancel = findViewById<MaterialButton>(R.id.btnCancelProcess)

      btnCancel.setOnClickListener {
          ProcessingManager.cancelCurrentProcess(this)
      }

    lifecycleScope.launch {
        ProcessingManager.state.collectLatest { state ->
            if (state.isProcessing) {
                overlay.visibility = View.VISIBLE
                // Block screen reader from reaching content behind overlay
                navHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

                tvTitle.text = state.statusMessage
                
                if (state.progress > 0f) {
                    progressBar.isIndeterminate = false
                    progressBar.progress = (state.progress * 100).toInt()
                    tvPercent.text = "${(state.progress * 100).toInt()}%"
                    if (state.etaMessage.isNotEmpty()) {
                        tvEta.visibility = View.VISIBLE
                        tvEta.text = state.etaMessage
                    } else {
                        tvEta.visibility = View.GONE
                    }
                } else {
                    progressBar.isIndeterminate = true
                    tvPercent.text = getString(R.string.string_142)
                    tvEta.visibility = View.GONE
                }

                btnCancel.visibility = if (state.isCancellable) View.VISIBLE else View.GONE

                  // Send accessibility focus to the overlay title only once at startup
                  if (!wasProcessing) {
                      tvTitle.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                      tvTitle.requestFocus()
                      wasProcessing = true
                  }
              } else {
                  overlay.visibility = View.GONE
                  // Restore screen reader access to content behind overlay
                  navHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                  wasProcessing = false
              }

            // Show error dialog when errorLog is not null
            state.errorLog?.let { error ->
                SoundManager.playError()
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle(com.example.accessiblevideoeditor.ui.AppStrings.get(this@MainActivity, R.string.string_84)) // Error
                    .setMessage(error)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        dialog.dismiss()
                        ProcessingManager.dismissError()
                    }
                    .setOnCancelListener {
                        ProcessingManager.dismissError()
                    }
                    .show()
            }
        }
    }
  }

  private fun handleShareIntent(intent: android.content.Intent?) {
      if (intent == null) return
      val action = intent.action
      val type = intent.type
      if (android.content.Intent.ACTION_SEND == action && type != null) {
          val uri = intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? android.net.Uri
          if (uri != null) {
              navigateToSharedMedia(uri, type)
          }
      } else if (android.content.Intent.ACTION_SEND_MULTIPLE == action && type != null) {
          val uris = intent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
          if (!uris.isNullOrEmpty()) {
              navigateToSharedMedia(uris[0], type)
          }
      }
  }

  private fun navigateToSharedMedia(uri: android.net.Uri, mimeType: String) {
      ProcessingManager.sharedMediaUri = uri
      val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
      val navController = navHostFragment?.let { androidx.navigation.fragment.NavHostFragment.findNavController(it) }
      if (navController != null) {
          try {
              if (mimeType.startsWith("video/")) {
                  navController.navigate(R.id.videoEditorFragment)
              } else if (mimeType.startsWith("audio/")) {
                  navController.navigate(R.id.audioEditorFragment)
              } else if (mimeType.startsWith("image/")) {
                  navController.navigate(R.id.imageEditorFragment)
              }
          } catch (e: Exception) {
              e.printStackTrace()
          }
      }
  }

  override fun onNewIntent(intent: android.content.Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      handleUpdateIntent(intent)
      handleShareIntent(intent)
  }

  private fun handleUpdateIntent(intent: android.content.Intent?) {
      if (intent?.getBooleanExtra("start_download_update", false) == true) {
          val url = intent.getStringExtra("download_url") ?: return
          val version = intent.getStringExtra("download_version") ?: ""
          val info = com.example.accessiblevideoeditor.updater.AppUpdater.UpdateInfo(
              versionCode = 999,
              versionName = version,
              downloadUrl = url,
              releaseNotes = ""
          )
          com.example.accessiblevideoeditor.updater.AppUpdater.startDownloadWithProgress(this, info)
      }
  }

  override fun onDestroy() {
      super.onDestroy()
      SoundManager.release()
  }
}
