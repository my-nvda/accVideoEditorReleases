package com.example.accessiblevideoeditor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.accessiblevideoeditor.theme.AccessibleVideoEditorTheme
import com.example.accessiblevideoeditor.ui.MainNavigation
import com.example.accessiblevideoeditor.media.SoundManager
import com.example.accessiblevideoeditor.ui.GlobalProgressDialog
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import com.example.accessiblevideoeditor.updater.AppUpdater


class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Managers
    com.example.accessiblevideoeditor.ui.SettingsManager.init(this)
    com.example.accessiblevideoeditor.media.SoundManager.init(this)
    com.example.accessiblevideoeditor.media.SoundManager.playStartup()
    com.example.accessiblevideoeditor.ui.ProcessingManager.init(this)
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

    // Extract Shared Uris
    val sharedUris = mutableListOf<android.net.Uri>()
    if (intent?.action == android.content.Intent.ACTION_SEND) {
        (intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM) as? android.net.Uri)?.let { sharedUris.add(it) }
    } else if (intent?.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
        intent.getParcelableArrayListExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM)?.forEach {
            if (it is android.net.Uri) sharedUris.add(it)
        }
    }

    enableEdgeToEdge()
    setContent {
      val isDarkTheme = com.example.accessiblevideoeditor.ui.SettingsManager.isDarkModeState.value
      AccessibleVideoEditorTheme(darkTheme = isDarkTheme) { 
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
              MainNavigation(sharedUris = sharedUris) 
              GlobalProgressDialog()
          } 
      }
    }
  }

  override fun onDestroy() {
      super.onDestroy()
      SoundManager.release()
  }
}
