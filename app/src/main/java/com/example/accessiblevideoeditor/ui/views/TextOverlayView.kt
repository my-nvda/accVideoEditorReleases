package com.example.accessiblevideoeditor.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.BLACK
    }

    fun setOverlays(overlays: List<TextOverlayConfig>) {
        textOverlays.clear()
        textOverlays.addAll(overlays)
        invalidate()
    }

    fun updatePosition(positionMs: Long) {
        if (currentPositionMs == positionMs) return
        currentPositionMs = positionMs

        val activeOverlays = textOverlays.filter {
            currentPositionMs >= it.startTimeMs && currentPositionMs <= it.endTimeMs
        }
        val activeIds = activeOverlays.map { it.id }.toSet()

        // Announce newly visible text overlay to screen readers for accessibility
        if (activeIds != lastAnnouncedOverlayIds) {
            val newlyActive = activeOverlays.filter { !lastAnnouncedOverlayIds.contains(it.id) }
            if (newlyActive.isNotEmpty()) {
                val announcement = newlyActive.joinToString(", ") { "Subtitle: ${it.text}" }
                contentDescription = announcement
                sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
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

            // Determine text color
            val textColor = try {
                Color.parseColor(overlay.colorHex)
            } catch (_: Exception) {
                Color.WHITE
            }

            // Scale text size proportional to view height
            val scaledTextSize = (overlay.fontSize / 100f) * viewHeight * 0.15f
            val finalSize = maxOf(scaledTextSize, 28f)

            textPaint.color = textColor
            textPaint.textSize = finalSize

            strokePaint.textSize = finalSize

            // Position calculation based on percentages
            val x = overlay.xPosPercent * viewWidth
            val y = overlay.yPosPercent * viewHeight

            // Draw padded backdrop box if enabled
            if (overlay.hasBackdrop) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B3000000")
                    style = Paint.Style.FILL
                }
                val textWidth = textPaint.measureText(text)
                val fontMetrics = textPaint.fontMetrics
                val boxPaddingH = 24f
                val boxPaddingV = 12f
                val bgRect = android.graphics.RectF(
                    x - textWidth / 2f - boxPaddingH,
                    y + fontMetrics.ascent - boxPaddingV,
                    x + textWidth / 2f + boxPaddingH,
                    y + fontMetrics.descent + boxPaddingV
                )
                canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)
            }

            // Draw text outline for legibility over any video background
            canvas.drawText(text, x, y, strokePaint)
            // Draw filled text
            canvas.drawText(text, x, y, textPaint)
        }
    }
}
