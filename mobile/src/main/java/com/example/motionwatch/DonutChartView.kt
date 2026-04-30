package com.example.motionwatch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * DonutChartView
 *
 * Canvas-drawn donut chart matching the HTML mockup Screen 4.
 * Accepts a list of (color, fraction) slices via [setSlices].
 * Center text shows "Total / N / events".
 *
 * Usage:
 *   donutChart.setSlices(listOf(
 *       DonutChartView.Slice(Color.parseColor("#4A6CF7"), 0.55f),
 *       DonutChartView.Slice(Color.parseColor("#FFA726"), 0.22f),
 *   ), totalEvents = 14)
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val color: Int, val fraction: Float)

    private var slices:      List<Slice> = emptyList()
    private var totalEvents: Int         = 0

    private val strokeWidth = 48f   // donut ring thickness in px
    private val gap         = 3f    // small gap between slices (degrees)

    // Paints
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@DonutChartView.strokeWidth
        strokeCap   = Paint.Cap.BUTT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = this@DonutChartView.strokeWidth
        color       = Color.parseColor("#ECEEFF")
        strokeCap   = Paint.Cap.BUTT
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = Color.parseColor("#6B6B9A")
    }
    private val paintCount = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign   = Paint.Align.CENTER
        color       = Color.parseColor("#1A1A3E")
        isFakeBoldText = true
    }
    private val paintSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color     = Color.parseColor("#8888BB")
    }

    private val oval = RectF()

    fun setSlices(slices: List<Slice>, totalEvents: Int) {
        this.slices      = slices
        this.totalEvents = totalEvents
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w   = width.toFloat()
        val h   = height.toFloat()
        val cx  = w / 2f
        val cy  = h / 2f
        val pad = strokeWidth / 2f + 4f
        oval.set(pad, pad, w - pad, h - pad)

        if (slices.isEmpty()) {
            // Empty state: full grey track
            canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        } else {
            // Draw coloured slices
            val totalDeg = 360f - (gap * slices.size)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.fraction * totalDeg).coerceAtLeast(0f)
                slicePaint.color = slice.color
                canvas.drawArc(oval, startAngle, sweep, false, slicePaint)
                startAngle += sweep + gap
            }
        }

        // Center text (matching HTML mockup)
        val density = resources.displayMetrics.density
        paintLabel.textSize = 11f * density
        paintCount.textSize = 16f * density
        paintSub.textSize   = 9f  * density

        canvas.drawText("Total",        cx, cy - 12f * density, paintLabel)
        canvas.drawText("$totalEvents", cx, cy +  5f * density, paintCount)
        canvas.drawText("events",       cx, cy + 16f * density, paintSub)
    }
}