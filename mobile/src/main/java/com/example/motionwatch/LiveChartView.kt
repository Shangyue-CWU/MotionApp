package com.example.motionwatch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
// Removed: com.google.android.gms.common.util.CollectionUtils.listOf
//   → Kotlin stdlib's listOf() is always available; no import needed.
// Removed: kotlinx.coroutines.internal.synchronized
//   → That is an internal coroutines API. Kotlin's built-in synchronized()
//     is a top-level function in kotlin.jvm; no import is required.
// Removed: android.util.Pair
//   → The `to` infix already produces a kotlin.Pair, which also exposes
//     .first / .second. No Android Pair import is needed.

/**
 * A scrolling real-time line chart for 6-axis IMU data:
 * AX / AY / AZ + GX / GY / GZ.
 */
class LiveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DisplayMode {
        ACC, GYRO, ALL
    }

    var displayMode: DisplayMode = DisplayMode.ACC
        set(value) {
            field = value
            invalidate()
        }

    private val maxSamples = 300

    private val bufAX = ArrayDeque<Float>(maxSamples)
    private val bufAY = ArrayDeque<Float>(maxSamples)
    private val bufAZ = ArrayDeque<Float>(maxSamples)
    private val bufGX = ArrayDeque<Float>(maxSamples)
    private val bufGY = ArrayDeque<Float>(maxSamples)
    private val bufGZ = ArrayDeque<Float>(maxSamples)

    private val lock = Any()

    private fun push(buffer: ArrayDeque<Float>, value: Float) {
        if (buffer.size >= maxSamples) {
            buffer.removeFirst()
        }
        buffer.addLast(value)
    }

    fun addSample(
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float,
        gy: Float,
        gz: Float
    ) {
        synchronized(lock) {
            push(bufAX, ax)
            push(bufAY, ay)
            push(bufAZ, az)
            push(bufGX, gx)
            push(bufGY, gy)
            push(bufGZ, gz)
        }
        postInvalidate()
    }

    fun clear() {
        synchronized(lock) {
            listOf(bufAX, bufAY, bufAZ, bufGX, bufGY, bufGZ).forEach { it.clear() }
        }
        invalidate()
    }

    fun lastAX(): Float = synchronized(lock) { bufAX.lastOrNull() ?: 0f }
    fun lastAY(): Float = synchronized(lock) { bufAY.lastOrNull() ?: 0f }
    fun lastAZ(): Float = synchronized(lock) { bufAZ.lastOrNull() ?: 0f }
    fun lastGX(): Float = synchronized(lock) { bufGX.lastOrNull() ?: 0f }
    fun lastGY(): Float = synchronized(lock) { bufGY.lastOrNull() ?: 0f }
    fun lastGZ(): Float = synchronized(lock) { bufGZ.lastOrNull() ?: 0f }

    private val colAX = Color.parseColor("#4A6CF7")
    private val colAY = Color.parseColor("#66BB6A")
    private val colAZ = Color.parseColor("#FFA726")
    private val colGX = Color.parseColor("#EF5350")
    private val colGY = Color.parseColor("#AB47BC")
    private val colGZ = Color.parseColor("#26C6DA")

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEEFF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val paintCursor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A3FBF")
        alpha = 90
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }

    private fun linePaint(colorValue: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorValue
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    private val drawPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Horizontal grid lines at 25 / 50 / 75 %
        for (i in 1..3) {
            canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, paintGrid)
        }

        // Build series list using Kotlin Pair (produced by the `to` infix)
        val series: List<Pair<ArrayDeque<Float>, Int>> = when (displayMode) {
            DisplayMode.ACC -> listOf(
                bufAX to colAX,
                bufAY to colAY,
                bufAZ to colAZ
            )
            DisplayMode.GYRO -> listOf(
                bufGX to colGX,
                bufGY to colGY,
                bufGZ to colGZ
            )
            DisplayMode.ALL -> listOf(
                bufAX to colAX,
                bufAY to colAY,
                bufAZ to colAZ,
                bufGX to colGX,
                bufGY to colGY,
                bufGZ to colGZ
            )
        }

        synchronized(lock) {
            series.forEach { (buffer, colorValue) ->
                val values = buffer.toList()
                if (values.size < 2) return@forEach

                val minValue = values.minOrNull() ?: return@forEach
                val maxValue = values.maxOrNull() ?: return@forEach

                val range      = (maxValue - minValue).coerceAtLeast(0.01f)
                val step       = w / (values.size - 1).toFloat()
                val drawHeight = h * 0.8f
                val topPadding = h * 0.1f

                drawPath.reset()
                values.forEachIndexed { index, value ->
                    val x = index * step
                    val y = topPadding + drawHeight - ((value - minValue) / range) * drawHeight
                    if (index == 0) drawPath.moveTo(x, y) else drawPath.lineTo(x, y)
                }
                canvas.drawPath(drawPath, linePaint(colorValue))
            }
        }

        // Live cursor at right edge
        if (synchronized(lock) { bufAX.isNotEmpty() }) {
            canvas.drawLine(w - 3f, 0f, w - 3f, h, paintCursor)
        }
    }
}