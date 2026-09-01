package com.example.accessiblevideoeditor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import java.io.File
import java.io.FileOutputStream

object TextRenderer {
    fun createTickerPng(
        options: TextOptions,
        outputFile: File
    ): Boolean {
        var bitmap: Bitmap? = null
        try {
            outputFile.parentFile?.mkdirs()
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = options.textColor
                textSize = options.textSizeSp * 2.5f
                val family = when (options.fontFamily) {
                    TextFontFamily.DEFAULT -> Typeface.DEFAULT_BOLD
                    TextFontFamily.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    TextFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    TextFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                typeface = family
                if (options.shadowRadius > 0f) {
                    setShadowLayer(options.shadowRadius, options.shadowDx, options.shadowDy, options.shadowColor)
                }
            }

            // Support HTML formatting and trim extra trailing newlines appended by Html.fromHtml
            val rawSpannedText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(options.text, android.text.Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(options.text)
            }
            val spannedText = trimSpanned(rawSpannedText)
            val plainText = spannedText.toString()

            val textWidth = textPaint.measureText(plainText).toInt() + 40 // padding
            val fontMetrics = textPaint.fontMetrics
            val textHeight = (fontMetrics.descent - fontMetrics.ascent).toInt() + 20 // padding
            
            bitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (options.bgColor != Color.TRANSPARENT) {
                val bgPaint = Paint().apply {
                    color = options.bgColor
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, textWidth.toFloat(), textHeight.toFloat(), bgPaint)
            }

            // Handle RTL script align mapping
            val isRtl = android.text.BidiFormatter.getInstance().isRtl(plainText)
            val layoutAlignment = when (options.alignment) {
                TextAlignment.LEFT -> if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                TextAlignment.RIGHT -> if (isRtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
            }

            // Use StaticLayout to draw spanned text (handles color spans correctly)
            // Use (textWidth - 40) constraint for correct padding bounds
            val contentWidth = maxOf(10, textWidth - 40)
            val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spannedText, 0, spannedText.length, textPaint, contentWidth)
                    .setAlignment(layoutAlignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spannedText, textPaint, contentWidth, layoutAlignment, 1f, 0f, true)
            }

            canvas.save()
            canvas.translate(20f, 10f)

            // 1. Draw Outline Stroke Pass (Black Outline)
            // Strip ForegroundColorSpans from the outline layout so that the outline is purely black
            val outlineSpanned = android.text.SpannableStringBuilder(spannedText)
            val colorSpans = outlineSpanned.getSpans(0, outlineSpanned.length, android.text.style.ForegroundColorSpan::class.java)
            for (span in colorSpans) {
                outlineSpanned.removeSpan(span)
            }

            val strokePaint = TextPaint(textPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = Color.BLACK
                clearShadowLayer()
            }

            val outlineLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(outlineSpanned, 0, outlineSpanned.length, strokePaint, contentWidth)
                    .setAlignment(layoutAlignment)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(outlineSpanned, strokePaint, contentWidth, layoutAlignment, 1f, 0f, true)
            }
            outlineLayout.draw(canvas)

            // 2. Draw Fill Pass (Original/Span Colors)
            staticLayout.draw(canvas)
            canvas.restore()

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            return false
        } finally {
            bitmap?.recycle()
        }
    }

    private fun trimSpanned(spanned: CharSequence): CharSequence {
        var len = spanned.length
        while (len > 0 && spanned[len - 1].isWhitespace()) {
            len--
        }
        return if (len == spanned.length) spanned else spanned.subSequence(0, len)
    }

    enum class TextPosition {
        TOP, CENTER, BOTTOM
    }
    
    enum class TextAlignment { LEFT, CENTER, RIGHT }
    enum class TextFontFamily { DEFAULT, SERIF, SANS_SERIF, MONOSPACE }

    data class TextOptions(
        val text: String,
        val textColor: Int = Color.WHITE,
        val bgColor: Int = Color.parseColor("#80000000"), // Semi-transparent black
        val textSizeSp: Float = 48f,
        val position: TextPosition = TextPosition.BOTTOM,
        val alignment: TextAlignment = TextAlignment.CENTER,
        val fontFamily: TextFontFamily = TextFontFamily.DEFAULT,
        val shadowRadius: Float = 0f,
        val shadowDx: Float = 0f,
        val shadowDy: Float = 0f,
        val shadowColor: Int = Color.BLACK
    )

    /**
     * Creates a transparent PNG with text drawn on it. Used as an overlay for FFmpeg.
     */
    fun createOverlayPng(
        width: Int,
        height: Int,
        options: TextOptions,
        outputFile: File
    ): Boolean {
        var bitmap: Bitmap? = null
        try {
            outputFile.parentFile?.mkdirs()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            drawTextOnCanvas(canvas, width, height, options)
            
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return true
        } catch (e: Throwable) {
            e.printStackTrace()
            return false
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Draws text directly onto an existing image Bitmap.
     */
    fun drawTextOnImage(
        sourceBitmap: Bitmap,
        options: TextOptions
    ): Bitmap {
        try {
            // Create a mutable copy of the source bitmap
            val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return sourceBitmap
            val canvas = Canvas(resultBitmap)
            drawTextOnCanvas(canvas, resultBitmap.width, resultBitmap.height, options)
            return resultBitmap
        } catch (e: Throwable) {
            e.printStackTrace()
            return sourceBitmap
        }
    }

    private fun drawTextOnCanvas(canvas: Canvas, width: Int, height: Int, options: TextOptions) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.textColor
            textSize = options.textSizeSp * 2.5f // Scale up for typical video resolutions (e.g. 1080p)
            
            val family = when (options.fontFamily) {
                TextFontFamily.DEFAULT -> Typeface.DEFAULT_BOLD
                TextFontFamily.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                TextFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                TextFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            typeface = family

            if (options.shadowRadius > 0f) {
                setShadowLayer(options.shadowRadius, options.shadowDx, options.shadowDy, options.shadowColor)
            }
        }

        val padding = (width * 0.05f).toInt() // 5% padding
        val textWidth = maxOf(10, width - (padding * 2))

        // Support HTML formatting and trim extra newlines
        val rawSpannedText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(options.text, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(options.text)
        }
        val spannedText = trimSpanned(rawSpannedText)
        val plainText = spannedText.toString()

        // Handle RTL script align mapping
        val isRtl = android.text.BidiFormatter.getInstance().isRtl(plainText)
        val layoutAlignment = when (options.alignment) {
            TextAlignment.LEFT -> if (isRtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> if (isRtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
        }

        // Use StaticLayout to handle multi-line text and Arabic shaping automatically
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(spannedText, 0, spannedText.length, textPaint, textWidth)
                .setAlignment(layoutAlignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(spannedText, textPaint, textWidth, layoutAlignment, 1f, 0f, true)
        }

        val textHeight = staticLayout.height

        // Calculate Y position based on requested position
        var startY = when (options.position) {
            TextPosition.TOP -> (height * 0.1f).toInt()
            TextPosition.CENTER -> (height - textHeight) / 2
            TextPosition.BOTTOM -> height - textHeight - (height * 0.1f).toInt()
        }
        // Prevent negative startY clipping
        if (startY < 0) startY = 0

        // Calculate exact bounding width of the text layout to size the background box
        var maxLineWidth = 0f
        for (line in 0 until staticLayout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, staticLayout.getLineWidth(line))
        }
        val actualTextWidth = maxLineWidth.toInt()

        // Draw Background Box centered according to layout alignment
        if (options.bgColor != Color.TRANSPARENT) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = options.bgColor
                style = Paint.Style.FILL
            }
            val boxPaddingH = (width * 0.04f).coerceAtLeast(24f)
            val boxPaddingV = (height * 0.015f).coerceAtLeast(12f)
            
            // Calculate horizontal bounds centering the backdrop box around aligned text
            val boxLeft = when (layoutAlignment) {
                Layout.Alignment.ALIGN_CENTER -> padding + (textWidth - actualTextWidth) / 2f
                Layout.Alignment.ALIGN_OPPOSITE -> (padding + textWidth - actualTextWidth).toFloat()
                Layout.Alignment.ALIGN_NORMAL -> padding.toFloat()
            }
            
            val bgRect = android.graphics.RectF(
                (boxLeft - boxPaddingH).coerceAtLeast(0f),
                (startY - boxPaddingV).coerceAtLeast(0f),
                (boxLeft + actualTextWidth + boxPaddingH).coerceAtMost(width.toFloat()),
                (startY + textHeight + boxPaddingV).coerceAtMost(height.toFloat())
            )
            canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
        }

        // Draw Text
        canvas.save()
        canvas.translate(padding.toFloat(), startY.toFloat())

        // 1. Draw Outline Stroke Pass (Black Outline)
        val outlineSpanned = android.text.SpannableStringBuilder(spannedText)
        val colorSpans = outlineSpanned.getSpans(0, outlineSpanned.length, android.text.style.ForegroundColorSpan::class.java)
        for (span in colorSpans) {
            outlineSpanned.removeSpan(span)
        }

        val strokePaint = TextPaint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f // Outline width
            color = Color.BLACK
            clearShadowLayer()
        }

        val outlineLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(outlineSpanned, 0, outlineSpanned.length, strokePaint, textWidth)
                .setAlignment(layoutAlignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(outlineSpanned, strokePaint, textWidth, layoutAlignment, 1f, 0f, true)
        }
        outlineLayout.draw(canvas)

        // 2. Draw Fill Pass (Original/Span Colors)
        staticLayout.draw(canvas)
        canvas.restore()
    }
}
