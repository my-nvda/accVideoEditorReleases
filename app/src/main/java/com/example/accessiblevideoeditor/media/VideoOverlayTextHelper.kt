package com.example.accessiblevideoeditor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

object VideoOverlayTextHelper {

    /**
     * Creates a transparent PNG image with the provided text drawn on it.
     * This ensures that complex scripts like Arabic are rendered perfectly using Android's native text engine.
     */
    fun createTextBitmapOverlay(
        text: String,
        videoWidth: Int,
        videoHeight: Int,
        textSizeSp: Float,
        textColor: Int = Color.WHITE,
        backgroundColor: Int = Color.TRANSPARENT,
        outputFile: File
    ): Boolean {
        try {
            // Create a transparent bitmap matching the video dimensions
            val bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Optional background (e.g. translucent black behind text for readability)
            if (backgroundColor != Color.TRANSPARENT) {
                 canvas.drawColor(backgroundColor)
            }

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = textSizeSp * 3f // Simple scaling, can be refined based on display metrics
                setShadowLayer(5f, 2f, 2f, Color.BLACK) // Adds shadow for visibility on bright videos
            }

            // Margin for text
            val margin = 50
            val textWidth = videoWidth - (margin * 2)

            // StaticLayout handles text wrapping and BiDi (RTL/LTR) inherently
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            canvas.save()
            // Center the text vertically and horizontally
            val x = margin.toFloat()
            val y = (videoHeight - staticLayout.height) / 2f
            canvas.translate(x, y)
            staticLayout.draw(canvas)
            canvas.restore()

            // Save the bitmap to a PNG file
            val fos = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            bitmap.recycle()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
