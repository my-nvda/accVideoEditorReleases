package com.example.accessiblevideoeditor.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import com.example.accessiblevideoeditor.data.TextOverlayConfig

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textOverlays = mutableListOf<TextOverlayConfig>()
    private var currentPositionMs: Long = 0L
    private var lastAnnouncedOverlayIds = mutableSetOf<String>()

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.BLACK
    }

    fun setOverlays(overlays: List<TextOverlayConfig>) {
        textOverlays.clear()
        textOverlays.addAll(overlays)
        invalidate()
    }

    private fun getPlainText(text: String): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(text).toString()
        }
    }

    fun updatePosition(positionMs: Long) {
        if (currentPositionMs == positionMs) return
        currentPositionMs = positionMs

        val activeOverlays = textOverlays.filter {
            currentPositionMs >= it.startTimeMs && currentPositionMs <= it.endTimeMs
        }
        val activeIds = activeOverlays.map { it.id }.toSet()

        // Announce newly visible text overlay to screen readers for accessibility without HTML tags
        if (activeIds != lastAnnouncedOverlayIds) {
            val newlyActive = activeOverlays.filter { !lastAnnouncedOverlayIds.contains(it.id) }
            if (newlyActive.isNotEmpty()) {
                val announcement = newlyActive.joinToString(", ") { 
                    val plain = getPlainText(it.text)
                    "Subtitle: $plain"
                }
                contentDescription = announcement
                if (com.example.accessiblevideoeditor.ui.AccessibilityUtils.isAccessibilityEnabled(context)) {
                    try {
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    } catch (_: Exception) {}
                }
            }
            lastAnnouncedOverlayIds = activeIds.toMutableSet()
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (textOverlays.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val activeOverlays = textOverlays.filter {
            currentPositionMs >= it.startTimeMs && currentPositionMs <= it.endTimeMs
        }

        for (overlay in activeOverlays) {
            val text = overlay.text
            if (text.isBlank()) continue

            // Parse HTML to Spanned
            val spannedText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(text)
            }
            val plainText = spannedText.toString()

            // Scale text size proportional to view height
            val scaledTextSize = (overlay.fontSize / 100f) * viewHeight * 0.15f
            val finalSize = maxOf(scaledTextSize, 28f)

            // Determine base text color
            val textColor = try {
                Color.parseColor(overlay.colorHex)
            } catch (_: Exception) {
                Color.WHITE
            }

            textPaint.color = textColor
            textPaint.textSize = finalSize
            textPaint.style = Paint.Style.FILL

            strokePaint.textSize = finalSize
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 8f
            strokePaint.color = Color.BLACK
            strokePaint.clearShadowLayer()

            // Calculate layout constraint width (padded from side boundaries)
            val maxLayoutWidth = maxOf(10, viewWidth.toInt() - 80)

            // Strip ForegroundColorSpans from spanned text for the outline pass so the outline is purely black
            val outlineSpanned = SpannableStringBuilder(spannedText)
            val colorSpans = outlineSpanned.getSpans(0, outlineSpanned.length, ForegroundColorSpan::class.java)
            for (span in colorSpans) {
                outlineSpanned.removeSpan(span)
            }

            // Create layouts
            val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spannedText, 0, spannedText.length, textPaint, maxLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spannedText, textPaint, maxLayoutWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)
            }

            val outlineLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(outlineSpanned, 0, outlineSpanned.length, strokePaint, maxLayoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(outlineSpanned, strokePaint, maxLayoutWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)
            }

            // Calculate precise text dimensions to fit background box
            var maxLineWidth = 0f
            for (line in 0 until staticLayout.lineCount) {
                maxLineWidth = maxOf(maxLineWidth, staticLayout.getLineWidth(line))
            }
            val actualTextWidth = maxLineWidth.toInt()
            val textHeight = staticLayout.height

            // Absolute center coordinates
            val x = overlay.xPosPercent * viewWidth
            val y = overlay.yPosPercent * viewHeight

            val startX = x - (maxLayoutWidth / 2f)
            val startY = y - (textHeight / 2f)

            // Draw Background Box (Padded Box Backdrop with Rounded Corners)
            if (overlay.hasBackdrop) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B3000000")
                    style = Paint.Style.FILL
                }
                val boxPaddingH = 24f
                val boxPaddingV = 12f
                val bgRect = RectF(
                    x - actualTextWidth / 2f - boxPaddingH,
                    y - textHeight / 2f - boxPaddingV,
                    x + actualTextWidth / 2f + boxPaddingH,
                    y + textHeight / 2f + boxPaddingV
                )
                canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)
            }

            // Draw text outline then fill layout
            canvas.save()
            canvas.translate(startX, startY)
            outlineLayout.draw(canvas)
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }
}
