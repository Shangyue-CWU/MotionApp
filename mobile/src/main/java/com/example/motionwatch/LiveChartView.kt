package com.example.motionwatch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * LiveChartView — scrolling real-time IMU line chart.
 *
 * Display modes:
 *   ACC   → phone AX / AY / AZ
 *   GYRO  → phone GX / GY / GZ
 *   ALL   → all 6 phone axes
 *   WACC  → watch AX / AY / AZ  (deep-orange palette)
 *   WGYRO → watch GX / GY / GZ  (deep-orange palette)
 *
 * Phone data: addSample(ax, ay, az, gx, gy, gz)
 * Watch data: addWatchSample(ax, ay, az, gx, gy, gz)
 */
class LiveChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DisplayMode { ACC, GYRO, ALL, WACC, WGYRO }

    var displayMode: DisplayMode = DisplayMode.ACC
        set(value) { field = value; invalidate() }

    private val maxSamples = 300
    private val lock        = Any()

    // ── Phone buffers ─────────────────────────────────────────────────────────
    private val bufAX = ArrayDeque<Float>(maxSamples)
    private val bufAY = ArrayDeque<Float>(maxSamples)
    private val bufAZ = ArrayDeque<Float>(maxSamples)
    private val bufGX = ArrayDeque<Float>(maxSamples)
    private val bufGY = ArrayDeque<Float>(maxSamples)
    private val bufGZ = ArrayDeque<Float>(maxSamples)

    // ── Watch buffers ─────────────────────────────────────────────────────────
    private val wBufAX = ArrayDeque<Float>(maxSamples)
    private val wBufAY = ArrayDeque<Float>(maxSamples)
    private val wBufAZ = ArrayDeque<Float>(maxSamples)
    private val wBufGX = ArrayDeque<Float>(maxSamples)
    private val wBufGY = ArrayDeque<Float>(maxSamples)
    private val wBufGZ = ArrayDeque<Float>(maxSamples)

    private fun push(buf: ArrayDeque<Float>, v: Float) {
        if (buf.size >= maxSamples) buf.removeFirst()
        buf.addLast(v)
    }

    /** Add one phone sensor sample. */
    fun addSample(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        synchronized(lock) {
            push(bufAX, ax); push(bufAY, ay); push(bufAZ, az)
            push(bufGX, gx); push(bufGY, gy); push(bufGZ, gz)
        }
        postInvalidate()
    }

    /** Add one watch sensor sample. */
    fun addWatchSample(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        synchronized(lock) {
            push(wBufAX, ax); push(wBufAY, ay); push(wBufAZ, az)
            push(wBufGX, gx); push(wBufGY, gy); push(wBufGZ, gz)
        }
        postInvalidate()
    }

    fun clear() {
        synchronized(lock) {
            listOf(bufAX, bufAY, bufAZ, bufGX, bufGY, bufGZ,
                wBufAX, wBufAY, wBufAZ, wBufGX, wBufGY, wBufGZ).forEach { it.clear() }
        }
        invalidate()
    }

    // ── Last-value accessors (for axis pills) ─────────────────────────────────
    fun lastAX(): Float  = synchronized(lock) { bufAX.lastOrNull()  ?: 0f }
    fun lastAY(): Float  = synchronized(lock) { bufAY.lastOrNull()  ?: 0f }
    fun lastAZ(): Float  = synchronized(lock) { bufAZ.lastOrNull()  ?: 0f }
    fun lastGX(): Float  = synchronized(lock) { bufGX.lastOrNull()  ?: 0f }
    fun lastGY(): Float  = synchronized(lock) { bufGY.lastOrNull()  ?: 0f }
    fun lastGZ(): Float  = synchronized(lock) { bufGZ.lastOrNull()  ?: 0f }
    fun lastWAX(): Float = synchronized(lock) { wBufAX.lastOrNull() ?: 0f }
    fun lastWAY(): Float = synchronized(lock) { wBufAY.lastOrNull() ?: 0f }
    fun lastWAZ(): Float = synchronized(lock) { wBufAZ.lastOrNull() ?: 0f }
    fun lastWGX(): Float = synchronized(lock) { wBufGX.lastOrNull() ?: 0f }
    fun lastWGY(): Float = synchronized(lock) { wBufGY.lastOrNull() ?: 0f }
    fun lastWGZ(): Float = synchronized(lock) { wBufGZ.lastOrNull() ?: 0f }

    // ── Colours ───────────────────────────────────────────────────────────────
    // Phone axes
    private val colAX = Color.parseColor("#4A6CF7")  // blue
    private val colAY = Color.parseColor("#66BB6A")  // green
    private val colAZ = Color.parseColor("#FFA726")  // orange
    private val colGX = Color.parseColor("#EF5350")  // red
    private val colGY = Color.parseColor("#AB47BC")  // purple
    private val colGZ = Color.parseColor("#26C6DA")  // cyan

    // Watch axes — deep-orange family to distinguish from phone
    private val colWAX = Color.parseColor("#FF7043")  // deep-orange
    private val colWAY = Color.parseColor("#FF8A65")  // deep-orange light
    private val colWAZ = Color.parseColor("#FFAB91")  // deep-orange lighter
    private val colWGX = Color.parseColor("#F4511E")  // deep-orange dark
    private val colWGY = Color.parseColor("#BF360C")  // deep-orange darker
    private val colWGZ = Color.parseColor("#E64A19")  // deep-orange medium

    // ── Paints ────────────────────────────────────────────────────────────────
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#ECEEFF")
        strokeWidth = 2f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val paintCursor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#2A3FBF")
        alpha       = 90
        strokeWidth = 2f
        style       = Paint.Style.STROKE
        pathEffect  = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private fun linePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color  = color
        strokeWidth = 3.5f
        style       = Paint.Style.STROKE
        strokeCap   = Paint.Cap.ROUND
        strokeJoin  = Paint.Join.ROUND
    }

    private val drawPath = Path()

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (i in 1..3) canvas.drawLine(0f, h * i / 4f, w, h * i / 4f, paintGrid)

        val series: List<Pair<ArrayDeque<Float>, Int>> = when (displayMode) {
            DisplayMode.ACC   -> listOf(bufAX  to colAX,  bufAY  to colAY,  bufAZ  to colAZ)
            DisplayMode.GYRO  -> listOf(bufGX  to colGX,  bufGY  to colGY,  bufGZ  to colGZ)
            DisplayMode.ALL   -> listOf(bufAX  to colAX,  bufAY  to colAY,  bufAZ  to colAZ,
                bufGX  to colGX,  bufGY  to colGY,  bufGZ  to colGZ)
            DisplayMode.WACC  -> listOf(wBufAX to colWAX, wBufAY to colWAY, wBufAZ to colWAZ)
            DisplayMode.WGYRO -> listOf(wBufGX to colWGX, wBufGY to colWGY, wBufGZ to colWGZ)
        }

        synchronized(lock) {
            series.forEach { (buf, color) ->
                val values = buf.toList()
                if (values.size < 2) return@forEach

                val min   = values.minOrNull() ?: return@forEach
                val max   = values.maxOrNull() ?: return@forEach
                val range = (max - min).coerceAtLeast(0.01f)
                val step  = w / (values.size - 1).toFloat()
                val drawH = h * 0.8f
                val padT  = h * 0.1f

                drawPath.reset()
                values.forEachIndexed { i, v ->
                    val x = i * step
                    val y = padT + drawH - ((v - min) / range) * drawH
                    if (i == 0) drawPath.moveTo(x, y) else drawPath.lineTo(x, y)
                }
                canvas.drawPath(drawPath, linePaint(color))
            }
        }

        val hasData = synchronized(lock) {
            when (displayMode) {
                DisplayMode.WACC, DisplayMode.WGYRO -> wBufAX.isNotEmpty()
                else                                -> bufAX.isNotEmpty()
            }
        }
        if (hasData) canvas.drawLine(w - 3f, 0f, w - 3f, h, paintCursor)
    }
}