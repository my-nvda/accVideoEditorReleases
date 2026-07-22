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
        val doFocus = {
            targetView.isFocusable = true
            targetView.isFocusableInTouchMode = true
            targetView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            targetView.requestRectangleOnScreen(android.graphics.Rect(0, 0, targetView.width, targetView.height), true)
            targetView.requestFocus()
            
            ViewCompat.performAccessibilityAction(targetView, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null)
            
            val eventState = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            eventState.className = targetView.javaClass.name
            eventState.packageName = targetView.context.packageName
            targetView.sendAccessibilityEventUnchecked(eventState)

            targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }

        doFocus()
        targetView.postDelayed({ doFocus() }, 100)
        targetView.postDelayed({ doFocus() }, 300)
        targetView.postDelayed({ doFocus() }, 600)
    }

    fun attachAccessibilityFocusTracker(rootView: View, fragmentName: String, focusMap: MutableMap<String, Int>) {
        if (rootView.id != View.NO_ID) {
            val existingDelegate = ViewCompat.getAccessibilityDelegate(rootView)
            ViewCompat.setAccessibilityDelegate(rootView, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                    if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS || 
                        action == android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS ||
                        action == AccessibilityNodeInfoCompat.ACTION_CLICK ||
                        action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                        if (host.id != View.NO_ID) {
                            focusMap[fragmentName] = host.id
                        }
                    }
                    return existingDelegate?.performAccessibilityAction(host, action, args) 
                           ?: super.performAccessibilityAction(host, action, args)
                }

                override fun sendAccessibilityEvent(host: View, eventType: Int) {
                    existingDelegate?.sendAccessibilityEvent(host, eventType) ?: super.sendAccessibilityEvent(host, eventType)
                    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED || 
                        eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                        eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        if (host.id != View.NO_ID) {
                            focusMap[fragmentName] = host.id
                        }
                    }
                }
            })
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                attachAccessibilityFocusTracker(rootView.getChildAt(i), fragmentName, focusMap)
            }
        }
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
