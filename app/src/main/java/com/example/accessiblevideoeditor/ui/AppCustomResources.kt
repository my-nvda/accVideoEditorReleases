package com.example.accessiblevideoeditor.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.content.res.AssetFileDescriptor
import android.graphics.Movie
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.TypedValue
import java.io.InputStream

@Suppress("DEPRECATION")
class AppCustomResources(
    private val original: Resources,
    private val context: Context
) : Resources(original.assets, original.displayMetrics, original.configuration) {

    override fun getText(id: Int): CharSequence {
        return AppStrings.getDirect(context, id) ?: original.getText(id)
    }

    override fun getText(id: Int, def: CharSequence): CharSequence {
        return AppStrings.getDirect(context, id) ?: original.getText(id, def)
    }

    override fun getString(id: Int): String {
        return AppStrings.getDirect(context, id) ?: original.getString(id)
    }

    override fun getString(id: Int, vararg formatArgs: Any): String {
        return AppStrings.getDirect(context, id, *formatArgs) ?: original.getString(id, *formatArgs)
    }

    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        return original.getQuantityText(id, quantity)
    }

    override fun getQuantityString(id: Int, quantity: Int): String {
        return original.getQuantityString(id, quantity)
    }

    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
        return original.getQuantityString(id, quantity, *formatArgs)
    }

    // Forward all asset, theme and style methods to original to preserve compatibility and prevent crashes
    override fun getDrawable(id: Int): Drawable = original.getDrawable(id)
    override fun getDrawable(id: Int, theme: Theme?): Drawable = original.getDrawable(id, theme)
    override fun getDrawableForDensity(id: Int, density: Int): Drawable? = original.getDrawableForDensity(id, density)
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? = original.getDrawableForDensity(id, density, theme)
    override fun getColor(id: Int): Int = original.getColor(id)
    override fun getColor(id: Int, theme: Theme?): Int = original.getColor(id, theme)
    override fun getColorStateList(id: Int): ColorStateList = original.getColorStateList(id)
    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList = original.getColorStateList(id, theme)
    override fun getBoolean(id: Int): Boolean = original.getBoolean(id)
    override fun getDimension(id: Int): Float = original.getDimension(id)
    override fun getDimensionPixelOffset(id: Int): Int = original.getDimensionPixelOffset(id)
    override fun getDimensionPixelSize(id: Int): Int = original.getDimensionPixelSize(id)
    override fun getFraction(id: Int, base: Int, pbase: Int): Float = original.getFraction(id, base, pbase)
    override fun getInteger(id: Int): Int = original.getInteger(id)
    override fun getIntArray(id: Int): IntArray = original.getIntArray(id)
    override fun getStringArray(id: Int): Array<String> = original.getStringArray(id)
    override fun getTextArray(id: Int): Array<CharSequence> = original.getTextArray(id)
    override fun getXml(id: Int): XmlResourceParser = original.getXml(id)
    override fun getLayout(id: Int): XmlResourceParser = original.getLayout(id)
    override fun getAnimation(id: Int): XmlResourceParser = original.getAnimation(id)
    override fun getMovie(id: Int): Movie = original.getMovie(id)
    override fun openRawResource(id: Int): InputStream = original.openRawResource(id)
    override fun openRawResource(id: Int, value: TypedValue?): InputStream = original.openRawResource(id, value)
    override fun openRawResourceFd(id: Int): AssetFileDescriptor = original.openRawResourceFd(id)
    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        original.getValue(id, outValue, resolveRefs)
        if (outValue != null) {
            AppStrings.customStrings?.let { strings ->
                try {
                    if (outValue.type == TypedValue.TYPE_STRING && outValue.string != null) {
                        val name = original.getResourceEntryName(id)
                        strings[name]?.let { translated ->
                            outValue.string = translated
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun getValue(name: String?, outValue: TypedValue?, resolveRefs: Boolean) {
        original.getValue(name, outValue, resolveRefs)
        if (outValue != null) {
            AppStrings.customStrings?.let { strings ->
                try {
                    if (outValue.type == TypedValue.TYPE_STRING && outValue.string != null && name != null) {
                        strings[name]?.let { translated ->
                            outValue.string = translated
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun getValueForDensity(id: Int, density: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        original.getValueForDensity(id, density, outValue, resolveRefs)
        if (outValue != null) {
            AppStrings.customStrings?.let { strings ->
                try {
                    if (outValue.type == TypedValue.TYPE_STRING && outValue.string != null) {
                        val name = original.getResourceEntryName(id)
                        strings[name]?.let { translated ->
                            outValue.string = translated
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }
    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int = original.getIdentifier(name, defType, defPackage)
    override fun getResourceName(resid: Int): String = original.getResourceName(resid)
    override fun getResourcePackageName(resid: Int): String = original.getResourcePackageName(resid)
    override fun getResourceTypeName(resid: Int): String = original.getResourceTypeName(resid)
    override fun getResourceEntryName(resid: Int): String = original.getResourceEntryName(resid)
}
