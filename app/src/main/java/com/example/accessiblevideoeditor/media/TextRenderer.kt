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
        try {
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

            val textWidth = textPaint.measureText(options.text).toInt() + 40 // padding
            val fontMetrics = textPaint.fontMetrics
            val textHeight = (fontMetrics.descent - fontMetrics.ascent).toInt() + 20 // padding
            
            val bitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (options.bgColor != Color.TRANSPARENT) {
                val bgPaint = Paint().apply {
                    color = options.bgColor
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, textWidth.toFloat(), textHeight.toFloat(), bgPaint)
            }

            canvas.drawText(options.text, 20f, -fontMetrics.ascent + 10f, textPaint)

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
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
        try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            drawTextOnCanvas(canvas, width, height, options)
            
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Draws text directly onto an existing image Bitmap.
     */
    fun drawTextOnImage(
        sourceBitmap: Bitmap,
        options: TextOptions
    ): Bitmap {
        // Create a mutable copy of the source bitmap
        val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        drawTextOnCanvas(canvas, resultBitmap.width, resultBitmap.height, options)
        return resultBitmap
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
        val textWidth = width - (padding * 2)

        val layoutAlignment = when (options.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        // Use StaticLayout to handle multi-line text and Arabic shaping automatically
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(options.text, 0, options.text.length, textPaint, textWidth)
                .setAlignment(layoutAlignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(options.text, textPaint, textWidth, layoutAlignment, 1f, 0f, true)
        }

        val textHeight = staticLayout.height

        // Calculate Y position based on requested position
        val startY = when (options.position) {
            TextPosition.TOP -> (height * 0.1f).toInt()
            TextPosition.CENTER -> (height - textHeight) / 2
            TextPosition.BOTTOM -> height - textHeight - (height * 0.1f).toInt()
        }

        // Draw Background Box (Padded Box Backdrop with Rounded Corners)
        if (options.bgColor != Color.TRANSPARENT) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = options.bgColor
                style = Paint.Style.FILL
            }
            val boxPaddingH = (width * 0.04f).coerceAtLeast(24f)
            val boxPaddingV = (height * 0.015f).coerceAtLeast(12f)
            val bgRect = android.graphics.RectF(
                (padding - boxPaddingH).coerceAtLeast(0f),
                (startY - boxPaddingV).coerceAtLeast(0f),
                (padding + textWidth + boxPaddingH).coerceAtMost(width.toFloat()),
                (startY + textHeight + boxPaddingV).coerceAtMost(height.toFloat())
            )
            canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
        }

        // Draw Text
        canvas.save()
        canvas.translate(padding.toFloat(), startY.toFloat())
        staticLayout.draw(canvas)
        canvas.restore()
    }
}
