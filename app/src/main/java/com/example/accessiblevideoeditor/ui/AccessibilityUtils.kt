package com.example.accessiblevideoeditor.ui

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.appbar.MaterialToolbar

object AccessibilityUtils {

    fun announceScreenChanged(view: View) {
        // Try to find the MaterialToolbar to get the title
        val title = findToolbarTitle(view) ?: "Screen"
        
        // Set pane title which natively tells TalkBack a new window has appeared
        ViewCompat.setAccessibilityPaneTitle(view, title)

        view.postDelayed({
            // Force a window state changed event which triggers TalkBack to announce the title
            // and automatically place focus on the first element in the layout.
            if (view.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE).let { 
                it as android.view.accessibility.AccessibilityManager
                it.isEnabled 
            }) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                event.className = view.javaClass.name
                event.packageName = view.context.packageName
                event.text.add(title)
                view.parent?.requestSendAccessibilityEvent(view, event)
            }
        }, 200)
    }

    private fun findToolbarTitle(view: View): String? {
        if (view is MaterialToolbar) {
            return view.title?.toString()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val title = findToolbarTitle(view.getChildAt(i))
                if (title != null) return title
            }
        }
        return null
    }
}
