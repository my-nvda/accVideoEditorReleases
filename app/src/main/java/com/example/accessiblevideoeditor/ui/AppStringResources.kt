package com.example.accessiblevideoeditor.ui

import android.content.res.Resources

/**
 * Custom Resources wrapper that intercepts getString/getText calls
 * and returns cloud-translated strings when available.
 * Falls back transparently to the original APK strings if no translation exists.
 */
class AppStringResources(private val base: Resources) : Resources(
    base.assets, base.displayMetrics, base.configuration
) {

    override fun getString(id: Int): String {
        AppStrings.customStrings?.let { strings ->
            try {
                val name = base.getResourceEntryName(id)
                strings[name]?.let { return it }
            } catch (_: Exception) { }
        }
        return base.getString(id)
    }

    override fun getString(id: Int, vararg formatArgs: Any): String {
        AppStrings.customStrings?.let { strings ->
            try {
                val name = base.getResourceEntryName(id)
                strings[name]?.let { return String.format(it, *formatArgs) }
            } catch (_: Exception) { }
        }
        return base.getString(id, *formatArgs)
    }

    override fun getText(id: Int): CharSequence {
        AppStrings.customStrings?.let { strings ->
            try {
                val name = base.getResourceEntryName(id)
                strings[name]?.let { return it }
            } catch (_: Exception) { }
        }
        return base.getText(id)
    }
}
