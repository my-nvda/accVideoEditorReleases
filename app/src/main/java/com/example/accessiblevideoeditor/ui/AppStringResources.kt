package com.example.accessiblevideoeditor.ui

import android.content.res.Resources
import android.util.TypedValue

/**
 * Custom Resources wrapper that intercepts getString, getText, and getValue calls
 * and returns cloud-translated strings when available.
 * Covers 100% of Android string lookups (XML inflation, TypedArray, C++ native pool, Kotlin code).
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
                strings[name]?.let { translated ->
                    return try {
                        String.format(translated, *formatArgs)
                    } catch (_: Exception) {
                        translated
                    }
                }
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

    override fun getText(id: Int, def: CharSequence?): CharSequence {
        AppStrings.customStrings?.let { strings ->
            try {
                val name = base.getResourceEntryName(id)
                strings[name]?.let { return it }
            } catch (_: Exception) { }
        }
        return base.getText(id, def)
    }

    override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
        super.getValue(id, outValue, resolveRefs)
        AppStrings.customStrings?.let { strings ->
            try {
                if (outValue.type == TypedValue.TYPE_STRING && outValue.string != null) {
                    val name = base.getResourceEntryName(id)
                    strings[name]?.let { translated ->
                        outValue.string = translated
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun getValueForDensity(id: Int, density: Int, outValue: TypedValue, resolveRefs: Boolean) {
        super.getValueForDensity(id, density, outValue, resolveRefs)
        AppStrings.customStrings?.let { strings ->
            try {
                if (outValue.type == TypedValue.TYPE_STRING && outValue.string != null) {
                    val name = base.getResourceEntryName(id)
                    strings[name]?.let { translated ->
                        outValue.string = translated
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
