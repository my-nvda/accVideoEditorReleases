package com.example.accessiblevideoeditor

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.updater.AppUpdater
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import com.example.accessiblevideoeditor.ui.AccessibilityUtils

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Managers
    com.example.accessiblevideoeditor.ui.SettingsManager.init(this)
    SoundManager.init(this)
    SoundManager.playStartup()
    ProcessingManager.init(this)
    com.example.accessiblevideoeditor.ui.AppStrings.loadCustomStrings(this)

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

    // Request notification permission if Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    enableEdgeToEdge()
    setContentView(R.layout.activity_main)

    setupProcessingOverlay()
    
        // Register global fragment lifecycle callbacks for accessibility focus
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                super.onFragmentResumed(fm, f)
                f.view?.let {
                    AccessibilityUtils.announceScreenChanged(it)
                }
            }
        }, true)
    
    // Launch update checker
    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val info = AppUpdater.checkForUpdate(this@MainActivity)
        if (info != null) {
            AppUpdater.showUpdateNotification(this@MainActivity, info)
            AppUpdater.showUpdateDialog(this@MainActivity, info)
        }
    }
  }

  private fun setupProcessingOverlay() {
      val overlay = findViewById<View>(R.id.progressOverlay)
      val tvTitle = findViewById<TextView>(R.id.tvProgressTitle)
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
                  tvTitle.text = state.statusMessage
                  
                  if (state.progress > 0f) {
                      progressBar.isIndeterminate = false
                      progressBar.progress = (state.progress * 100).toInt()
                      val statusText = if (state.etaMessage.isNotEmpty()) {
                          "${(state.progress * 100).toInt()}% - ${state.etaMessage}"
                      } else {
                          "${(state.progress * 100).toInt()}%"
                      }
                      tvStatus.text = statusText
                  } else {
                      progressBar.isIndeterminate = true
                      tvStatus.text = getString(R.string.string_142)
                  }

                  btnCancel.visibility = if (state.isCancellable) View.VISIBLE else View.GONE
              } else {
                  overlay.visibility = View.GONE
              }
          }
      }
  }

  override fun onDestroy() {
      super.onDestroy()
      SoundManager.release()
  }
}
