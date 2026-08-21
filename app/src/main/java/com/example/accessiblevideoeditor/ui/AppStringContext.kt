package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

class AppStringContext(base: Context) : ContextWrapper(base) {
    private var customResources: Resources? = null

    override fun getResources(): Resources {
        if (customResources == null) {
            customResources = AppCustomResources(super.getResources(), this)
        }
        return customResources!!
    }
}
