package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * ContextWrapper that returns our AppStringResources instead of the default Resources.
 * This causes ALL getString() calls — including from XML-inflated views — to pass
 * through our cloud translation filter automatically.
 */
class AppStringContext(base: Context) : ContextWrapper(base) {
    private val appResources: Resources by lazy {
        AppStringResources(super.getResources())
    }

    override fun getResources(): Resources = appResources
}
