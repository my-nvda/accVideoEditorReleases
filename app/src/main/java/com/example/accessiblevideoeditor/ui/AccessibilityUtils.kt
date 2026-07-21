package com.example.accessiblevideoeditor.ui

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.appbar.MaterialToolbar

object AccessibilityUtils {

    fun announceScreenChanged(view: View) {
        val toolbar = findToolbar(view)
        val title = toolbar?.title?.toString() ?: "Screen"
        
        // Set native pane title
        ViewCompat.setAccessibilityPaneTitle(view, title)

        view.postDelayed({
            // Explicitly move TalkBack focus to the toolbar (or root view if not found)
            val targetView = toolbar ?: view
            ViewCompat.performAccessibilityAction(targetView, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null)
            targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }, 500) // 500ms delay to ensure fragment transition is completely finished
    }

    private fun findToolbar(view: View): MaterialToolbar? {
        if (view is MaterialToolbar) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val tb = findToolbar(view.getChildAt(i))
                if (tb != null) return tb
            }
        }
        return null
    }
}
