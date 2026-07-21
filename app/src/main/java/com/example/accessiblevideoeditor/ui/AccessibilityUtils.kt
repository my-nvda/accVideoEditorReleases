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
            val targetView = toolbar ?: view
            
            // Make sure the view is focusable for both standard focus and accessibility
            targetView.isFocusable = true
            targetView.isFocusableInTouchMode = true
            targetView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            // Request standard focus so Jieshuo/Talkback follow it naturally
            targetView.requestFocus()

            // Perform accessibility focus action
            ViewCompat.performAccessibilityAction(targetView, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null)

            // Send standard accessibility events to notify the screen reader
            val eventState = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            eventState.className = targetView.javaClass.name
            eventState.packageName = targetView.context.packageName
            eventState.text.add(title)
            targetView.sendAccessibilityEventUnchecked(eventState)

            targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }, 500)
    }

    fun focusView(targetView: View) {
        targetView.postDelayed({
            targetView.isFocusable = true
            targetView.isFocusableInTouchMode = true
            targetView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            targetView.requestFocus()
            ViewCompat.performAccessibilityAction(targetView, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null)
            targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }, 300)
    }

    fun findAccessibilityFocusedView(view: View): View? {
        if (view.isAccessibilityFocused) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val focused = findAccessibilityFocusedView(view.getChildAt(i))
                if (focused != null) return focused
            }
        }
        return null
    }

    fun findToolbar(view: View): MaterialToolbar? {
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
