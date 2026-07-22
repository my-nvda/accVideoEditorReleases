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
 * This is necessary because Android's TypedArray reads XML-defined strings
 * directly from the compiled APK resource table via native code, bypassing
 * any Resources.getString() override entirely.
 */
class TranslationInflaterFactory(
    private val delegate: LayoutInflater.Factory2?,
    private val inflater: LayoutInflater
) : LayoutInflater.Factory2 {

    companion object {
        private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
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

        fun translateAttr(attrName: String): String? {
            val resId = attrs.getAttributeResourceValue(NS_ANDROID, attrName, 0)
            if (resId == 0) return null
            return try {
                val entryName = context.resources.getResourceEntryName(resId)
                strings[entryName]
            } catch (_: Exception) { null }
        }

        if (view is TextView) {
            translateAttr("text")?.let { view.text = it }
            translateAttr("hint")?.let { view.hint = it }
        }
        translateAttr("contentDescription")?.let { view.contentDescription = it }
    }
}
