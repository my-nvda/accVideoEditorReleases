package com.example.accessiblevideoeditor

import android.os.Bundle
import android.content.res.Resources
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
  private val fragmentStack = mutableListOf<String>()
  private val lastFocusedViewIdMap = mutableMapOf<String, Int>()
  private var isAppReturningFromBackground = false

  fun saveLastFocusedViewId(fragmentName: String, viewId: Int) {
      if (viewId != View.NO_ID) {
          lastFocusedViewIdMap[fragmentName] = viewId
      }
  }

  override fun onStop() {
      super.onStop()
      isAppReturningFromBackground = true
  }

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
        val info = AppUpdater.checkForUpdate(this@MainActivity)
        if (info != null) {
            AppUpdater.showUpdateNotification(this@MainActivity, info)
            AppUpdater.showUpdateDialog(this@MainActivity, info)
        }
    }
  }

  private fun setupProcessingOverlay() {
      val overlay = findViewById<View>(R.id.progressOverlay)
      val navHost = findViewById<View>(R.id.nav_host_fragment)
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
                  // Block screen reader from reaching content behind overlay
                  navHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                  overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

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

                  // Send accessibility focus to the overlay title
                  tvTitle.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
                  tvTitle.requestFocus()
              } else {
                  overlay.visibility = View.GONE
                  // Restore screen reader access to content behind overlay
                  navHost.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
              }
          }
      }
  }

  private var wrappedResources: Resources? = null

  override fun getResources(): Resources {
      val base = super.getResources()
      if (wrappedResources == null) {
          wrappedResources = DynamicResources(base, this)
      }
      return wrappedResources!!
  }

  override fun onDestroy() {
      super.onDestroy()
      SoundManager.release()
  }
}

class DynamicResources(
    private val base: Resources,
    private val context: android.content.Context
) : Resources(base.assets, base.displayMetrics, base.configuration) {

    override fun getText(id: Int): CharSequence {
        return try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(context, id)
        } catch (e: Exception) {
            base.getText(id)
        }
    }

    override fun getText(id: Int, def: CharSequence?): CharSequence {
        return try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(context, id)
        } catch (e: Exception) {
            base.getText(id, def)
        }
    }

    override fun getString(id: Int): String {
        return try {
            com.example.accessiblevideoeditor.ui.AppStrings.get(context, id)
        } catch (e: Exception) {
            base.getString(id)
        }
    }

    override fun getString(id: Int, vararg formatArgs: Any?): String {
        return try {
            val nonNullArgs = formatArgs.filterNotNull().toTypedArray()
            com.example.accessiblevideoeditor.ui.AppStrings.get(context, id, *nonNullArgs)
        } catch (e: Exception) {
            try {
                @Suppress("SpreadOperator")
                base.getString(id, *formatArgs)
            } catch (ex: Exception) {
                base.getString(id)
            }
        }
    }

    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        return base.getQuantityText(id, quantity)
    }

    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
        @Suppress("SpreadOperator")
        return base.getQuantityString(id, quantity, *formatArgs)
    }

    override fun getQuantityString(id: Int, quantity: Int): String {
        return base.getQuantityString(id, quantity)
    }

    override fun getStringArray(id: Int): Array<String> {
        return base.getStringArray(id)
    }

    override fun getTextArray(id: Int): Array<CharSequence> {
        return base.getTextArray(id)
    }
}
