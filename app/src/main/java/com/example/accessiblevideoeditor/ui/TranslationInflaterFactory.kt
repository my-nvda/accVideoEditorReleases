package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

/**
 * A LayoutInflater.Factory2 that intercepts every view at creation time and
 * applies cloud translations to text/hint/contentDescription attributes.
 *
 * Checks both string resource entry names (e.g. "string_112") and
 * view entry names (e.g. "btnVideoEditor") for maximum flexibility.
 */
class TranslationInflaterFactory(
    private val delegate: LayoutInflater.Factory2?,
    private val inflater: LayoutInflater
) : LayoutInflater.Factory2 {

    companion object {
        private val CLASS_PREFIXES = arrayOf(
            "android.widget.",
            "android.view.",
            "android.webkit."
        )
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        var view = delegate?.onCreateView(parent, name, context, attrs)
        if (view == null) {
            view = createViewFromTag(name, context, attrs)
        }
        view?.let { applyTranslations(it, context, attrs) }
        return view
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        var view = delegate?.onCreateView(name, context, attrs)
        if (view == null) {
            view = createViewFromTag(name, context, attrs)
        }
        view?.let { applyTranslations(it, context, attrs) }
        return view
    }

    private fun createViewFromTag(name: String, context: Context, attrs: AttributeSet): View? {
        try {
            if (name.contains('.')) {
                return inflater.createView(name, null, attrs)
            }
            for (prefix in CLASS_PREFIXES) {
                try {
                    val view = inflater.createView(name, prefix, attrs)
                    if (view != null) return view
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return null
    }

    private fun applyTranslations(view: View, context: Context, attrs: AttributeSet) {
        val strings = AppStrings.customStrings ?: return
        if (strings.isEmpty()) return

        // 1. Check matching by View ID name (e.g. "btnVideoEditor")
        if (view.id != View.NO_ID) {
            try {
                val viewIdName = context.resources.getResourceEntryName(view.id)
                strings[viewIdName]?.let { translated ->
                    if (view is TextView) {
                        view.text = translated
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Check matching by String Resource ID name (e.g. "string_112")
        for (i in 0 until attrs.attributeCount) {
            val attrName = attrs.getAttributeName(i)
            val resId = attrs.getAttributeResourceValue(i, 0)
            if (resId != 0) {
                try {
                    val entryName = context.resources.getResourceEntryName(resId)
                    strings[entryName]?.let { translated ->
                        when (attrName) {
                            "text" -> if (view is TextView) view.text = translated
                            "hint" -> if (view is TextView) view.hint = translated
                            "contentDescription" -> view.contentDescription = translated
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
