package com.example.motionwatch

import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * AnalyticsFragment — "Live Monitor" section (UR-L1 through UR-L6).
 *
 * ── Sensor pipeline ──────────────────────────────────────────────────────────
 * This fragment does NOT register SensorManager directly. Instead it delegates
 * entirely to SensorLoggerService, which already handles sensor registration,
 * file writing, and live broadcasting:
 *
 *   START  →  startForegroundService(SensorLoggerService ACTION_START)
 *   STOP   →  startService(SensorLoggerService ACTION_STOP)
 *
 * Sensor data arrives via three global broadcasts from SensorLoggerService:
 *   ACTION_LIVE_SAMPLE  — throttled live readings (~20 Hz) for chart + pills
 *   ACTION_LOG_STATE    — start/stop confirmation from the service
 *   ACTION_LOG_DONE     — session ended + absolute path of the written CSV
 *
 * Watch IMU data arrives via a fourth broadcast from WearCommandService:
 *   ACTION_SENSOR_DATA  — forwarded from /data/sensor Wearable message path
 *
 * ── CSV export ───────────────────────────────────────────────────────────────
 * SensorLoggerService writes the CSV to:
 *   getExternalFilesDir(null)/logs/SESSION_{id}_PHONE_LIVE_{ts}.csv
 * The absolute path arrives in ACTION_LOG_DONE → "filePath" extra.
 * Export copies that file into the public Downloads folder.
 *
 * ── Motion classifier ────────────────────────────────────────────────────────
 * classifyWindow() is a stub. Replace it with TFLite inference once
 * best_cnn.h5 / best_cnn_lstm.h5 is converted (see Phase1_Modelling_Roadmap).
 * Input expected: float[1][128][6], z-scored with scaler_params.json values.
 *
 * ── State machine ────────────────────────────────────────────────────────────
 *   IDLE ──[START]──▶ RECORDING ──[STOP / LOG_DONE]──▶ ENDED
 *   ENDED ──[NEW SESSION]──▶ IDLE
 *   Any state ──[CLEAR]──▶ IDLE
 */
class AnalyticsFragment : Fragment() {

    // ── Session state ────────────────────────────────────────────────────────

    private enum class SessionState { IDLE, RECORDING, ENDED }
    private var state = SessionState.IDLE

    // ── Session metadata ─────────────────────────────────────────────────────

    private var sessionId       = ""
    private var sessionStartMs  = 0L
    // Service throttles live broadcasts to every 5th sensor event (uiEveryN=5).
    // Multiply broadcast count by this to approximate real sample count.
    private val SAMPLES_PER_BROADCAST = 5
    private var sampleCount    = 0
    private var eventCount     = 0
    private var lastLabel      = ""
    // Tracks prediction label distribution for the Summary screen
    private val labelCounts    = mutableMapOf<String, Int>()
    // Absolute path of the CSV written by SensorLoggerService.
    // Populated when ACTION_LOG_DONE is received; used by exportCsv().
    private var sessionFilePath = ""

    // ── Prediction rolling window ────────────────────────────────────────────

    private val predWindow = ArrayDeque<FloatArray>()   // each entry = [ax,ay,az,gx,gy,gz]
    private val PRED_WINDOW_SIZE = 128
    // Run classifier every N broadcasts (~every 0.5 s at 20 Hz broadcast rate)
    private var broadcastCounter = 0
    private val PREDICT_EVERY_N = 10

    // ── Duration timer ───────────────────────────────────────────────────────

    private val durationHandler = Handler(Looper.getMainLooper())
    private val durationRunnable: Runnable = object : Runnable {
        override fun run() {
            if (state == SessionState.RECORDING) {
                val s = (System.currentTimeMillis() - sessionStartMs) / 1000L
                tvDuration.text = "%d:%02d".format(s / 60, s % 60)
                durationHandler.postDelayed(this, 1000L)
            }
        }
    }

    // ── Views ────────────────────────────────────────────────────────────────

    private lateinit var statusBadge:     LinearLayout
    private lateinit var statusDot:       View
    private lateinit var tvStatus:        TextView
    private lateinit var tvPrediction:    TextView
    private lateinit var tvConfidence:    TextView
    private lateinit var confBarFill:     View
    private lateinit var sessionStatsRow: LinearLayout
    private lateinit var tvDuration:      TextView
    private lateinit var tvSamples:       TextView
    private lateinit var tvEvents:        TextView
    private lateinit var liveChart:       LiveChartView
    private lateinit var tabAcc:          TextView
    private lateinit var tabGyro:         TextView
    private lateinit var tabAll:          TextView
    private lateinit var pillsAcc:        LinearLayout
    private lateinit var pillsGyro:       LinearLayout
    private lateinit var tvAX:            TextView
    private lateinit var tvAY:            TextView
    private lateinit var tvAZ:            TextView
    private lateinit var tvGX:            TextView
    private lateinit var tvGY:            TextView
    private lateinit var tvGZ:            TextView
    private lateinit var btnStartStop:    Button
    private lateinit var btnClear:        LinearLayout
    private lateinit var btnExportCsv:    Button
    private lateinit var btnSummary:      Button

    // ── Broadcast receiver ───────────────────────────────────────────────────
    //
    // SensorLoggerService and WearCommandService both use global sendBroadcast
    // (NOT LocalBroadcastManager), so we use a standard Context receiver here.
    // All intents carry setPackage(packageName), so RECEIVER_NOT_EXPORTED is
    // correct on Android 13+.

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                // ── Phone sensor reading (throttled ~20 Hz) ──────────────────
                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    if (state != SessionState.RECORDING) return

                    val ax = intent.getFloatExtra("ax", 0f)
                    val ay = intent.getFloatExtra("ay", 0f)
                    val az = intent.getFloatExtra("az", 0f)
                    val gx = intent.getFloatExtra("gx", 0f)
                    val gy = intent.getFloatExtra("gy", 0f)
                    val gz = intent.getFloatExtra("gz", 0f)

                    liveChart.addSample(ax, ay, az, gx, gy, gz)

                    tvAX.text = "X: ${fmt(ax)}";   tvAY.text = "Y: ${fmt(ay)}";  tvAZ.text = "Z: ${fmt(az)}"
                    tvGX.text = "GX: ${fmt(gx)}";  tvGY.text = "GY: ${fmt(gy)}"; tvGZ.text = "GZ: ${fmt(gz)}"

                    sampleCount += SAMPLES_PER_BROADCAST
                    tvSamples.text = fmtCount(sampleCount)

                    // Roll prediction window
                    if (predWindow.size >= PRED_WINDOW_SIZE) predWindow.removeFirst()
                    predWindow.addLast(floatArrayOf(ax, ay, az, gx, gy, gz))

                    broadcastCounter++
                    if (broadcastCounter % PREDICT_EVERY_N == 0) {
                        val r = classifyWindow()
                        renderPrediction(r.label, r.confidence)
                    }
                }

                // ── Watch IMU data forwarded by WearCommandService ───────────
                WearCommandService.ACTION_SENSOR_DATA -> {
                    if (state != SessionState.RECORDING) return
                    liveChart.addSample(
                        intent.getFloatExtra(WearCommandService.EXTRA_AX, 0f),
                        intent.getFloatExtra(WearCommandService.EXTRA_AY, 0f),
                        intent.getFloatExtra(WearCommandService.EXTRA_AZ, 0f),
                        intent.getFloatExtra(WearCommandService.EXTRA_GX, 0f),
                        intent.getFloatExtra(WearCommandService.EXTRA_GY, 0f),
                        intent.getFloatExtra(WearCommandService.EXTRA_GZ, 0f)
                    )
                }

                // ── Service state confirmation ────────────────────────────────
                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started      = intent.getBooleanExtra("started", false)
                    val svcSessionId = intent.getStringExtra("sessionId") ?: ""
                    if (svcSessionId == sessionId && started && state != SessionState.RECORDING) {
                        applyState(SessionState.RECORDING)
                    }
                }

                // ── Session ended — file is flushed and ready ────────────────
                SensorLoggerService.ACTION_LOG_DONE -> {
                    val svcSessionId = intent.getStringExtra("sessionId") ?: ""
                    if (svcSessionId != sessionId) return
                    sessionFilePath = intent.getStringExtra("filePath") ?: ""
                    durationHandler.removeCallbacks(durationRunnable)
                    applyState(SessionState.ENDED)
                }
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_analytics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupChartTabs()
        setupButtons()
        applyState(SessionState.IDLE)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(SensorLoggerService.ACTION_LIVE_SAMPLE)
            addAction(SensorLoggerService.ACTION_LOG_STATE)
            addAction(SensorLoggerService.ACTION_LOG_DONE)
            addAction(WearCommandService.ACTION_SENSOR_DATA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(serviceReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(serviceReceiver)
        if (state == SessionState.RECORDING) stopRecording()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        durationHandler.removeCallbacks(durationRunnable)
    }

    // ── View binding ─────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        statusBadge     = v.findViewById(R.id.statusBadge)
        statusDot       = v.findViewById(R.id.statusDot)
        tvStatus        = v.findViewById(R.id.tvStatus)
        tvPrediction    = v.findViewById(R.id.tvPrediction)
        tvConfidence    = v.findViewById(R.id.tvConfidence)
        confBarFill     = v.findViewById(R.id.confBarFill)
        sessionStatsRow = v.findViewById(R.id.sessionStatsRow)
        tvDuration      = v.findViewById(R.id.tvDuration)
        tvSamples       = v.findViewById(R.id.tvSamples)
        tvEvents        = v.findViewById(R.id.tvEvents)
        liveChart       = v.findViewById(R.id.liveChart)
        tabAcc          = v.findViewById(R.id.tabAcc)
        tabGyro         = v.findViewById(R.id.tabGyro)
        tabAll          = v.findViewById(R.id.tabAll)
        pillsAcc        = v.findViewById(R.id.pillsAcc)
        pillsGyro       = v.findViewById(R.id.pillsGyro)
        tvAX            = v.findViewById(R.id.tvAX)
        tvAY            = v.findViewById(R.id.tvAY)
        tvAZ            = v.findViewById(R.id.tvAZ)
        tvGX            = v.findViewById(R.id.tvGX)
        tvGY            = v.findViewById(R.id.tvGY)
        tvGZ            = v.findViewById(R.id.tvGZ)
        btnStartStop    = v.findViewById(R.id.btnStartStop)
        btnClear        = v.findViewById(R.id.btnClear)
        btnExportCsv    = v.findViewById(R.id.btnExportCsv)
        btnSummary      = v.findViewById(R.id.btnSummary)
    }

    // ── Chart tab logic ──────────────────────────────────────────────────────

    private fun setupChartTabs() {
        tabAcc.setOnClickListener  { selectTab(LiveChartView.DisplayMode.ACC)  }
        tabGyro.setOnClickListener { selectTab(LiveChartView.DisplayMode.GYRO) }
        tabAll.setOnClickListener  { selectTab(LiveChartView.DisplayMode.ALL)  }
    }

    private fun selectTab(mode: LiveChartView.DisplayMode) {
        liveChart.displayMode = mode
        val activeRes   = R.drawable.bg_tab_active
        val inactiveRes = R.drawable.bg_tab_inactive
        val activeCol   = 0xFFFFFFFF.toInt()
        val inactiveCol = 0xFF6B6B9A.toInt()

        tabAcc.setBackgroundResource( if (mode == LiveChartView.DisplayMode.ACC)  activeRes else inactiveRes)
        tabGyro.setBackgroundResource(if (mode == LiveChartView.DisplayMode.GYRO) activeRes else inactiveRes)
        tabAll.setBackgroundResource( if (mode == LiveChartView.DisplayMode.ALL)  activeRes else inactiveRes)
        tabAcc.setTextColor( if (mode == LiveChartView.DisplayMode.ACC)  activeCol else inactiveCol)
        tabGyro.setTextColor(if (mode == LiveChartView.DisplayMode.GYRO) activeCol else inactiveCol)
        tabAll.setTextColor( if (mode == LiveChartView.DisplayMode.ALL)  activeCol else inactiveCol)

        pillsAcc.visibility  = if (mode == LiveChartView.DisplayMode.GYRO) View.GONE else View.VISIBLE
        pillsGyro.visibility = if (mode != LiveChartView.DisplayMode.ACC)  View.VISIBLE else View.GONE
    }

    // ── Button wiring ────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnStartStop.setOnClickListener {
            when (state) {
                SessionState.IDLE      -> startRecording()
                SessionState.RECORDING -> stopRecording()
                SessionState.ENDED     -> clearSession()
            }
        }
        btnClear.setOnClickListener     { clearSession() }
        btnExportCsv.setOnClickListener { exportCsv()   }
        btnSummary.setOnClickListener   { openSummary() }
    }

    // ── Session control ──────────────────────────────────────────────────────

    private fun startRecording() {
        sessionId        = UUID.randomUUID().toString().replace("-", "").take(12)
        sessionStartMs   = System.currentTimeMillis()
        sampleCount      = 0
        eventCount       = 0
        broadcastCounter = 0
        lastLabel        = ""
        sessionFilePath  = ""
        labelCounts.clear()
        predWindow.clear()
        liveChart.clear()
        resetPredictionUi()

        requireContext().startForegroundService(
            Intent(requireContext(), SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_START
                putExtra("label",        LABEL_LIVE)
                putExtra("sessionId",    sessionId)
                putExtra("startEpochMs", sessionStartMs)
            }
        )

        applyState(SessionState.RECORDING)   // immediate UI response
        durationHandler.post(durationRunnable)
    }

    private fun stopRecording() {
        requireContext().startService(
            Intent(requireContext(), SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_STOP
            }
        )
        durationHandler.removeCallbacks(durationRunnable)
        // ACTION_LOG_DONE will confirm ENDED and populate sessionFilePath.
        // Apply ENDED now for instant visual feedback.
        applyState(SessionState.ENDED)
    }

    private fun clearSession() {
        if (state == SessionState.RECORDING) {
            requireContext().startService(
                Intent(requireContext(), SensorLoggerService::class.java).apply {
                    action = SensorLoggerService.ACTION_STOP
                }
            )
        }
        durationHandler.removeCallbacks(durationRunnable)
        liveChart.clear()
        predWindow.clear()
        sampleCount = 0; eventCount = 0; broadcastCounter = 0
        lastLabel = ""; sessionFilePath = ""; labelCounts.clear()
        tvDuration.text = "0:00"; tvSamples.text = "0"; tvEvents.text = "0"
        resetPredictionUi()
        applyState(SessionState.IDLE)
    }

    // ── State rendering ──────────────────────────────────────────────────────

    private fun applyState(newState: SessionState) {
        state = newState
        when (newState) {

            SessionState.IDLE -> {
                tvStatus.text = "IDLE"
                tvStatus.setTextColor(0xFF757575.toInt())
                statusDot.animate().cancel()
                statusDot.alpha = 1f
                statusDot.setBackgroundResource(R.drawable.bg_dot_idle)
                statusBadge.setBackgroundResource(R.drawable.bg_badge_idle)
                btnStartStop.text = "▶  START SESSION"
                btnStartStop.setBackgroundResource(R.drawable.bg_btn_start)
                sessionStatsRow.visibility = View.GONE
                setExportEnabled(false)
            }

            SessionState.RECORDING -> {
                tvStatus.text = "RECORDING"
                tvStatus.setTextColor(0xFF2E7D32.toInt())
                statusDot.setBackgroundResource(R.drawable.bg_dot_live)
                statusBadge.setBackgroundResource(R.drawable.bg_badge_live)
                pulseDot()
                btnStartStop.text = "■  STOP SESSION"
                btnStartStop.setBackgroundResource(R.drawable.bg_btn_stop)
                sessionStatsRow.visibility = View.VISIBLE
                setExportEnabled(false)
            }

            SessionState.ENDED -> {
                tvStatus.text = "STOPPED"
                tvStatus.setTextColor(0xFFEF5350.toInt())
                statusDot.animate().cancel()
                statusDot.alpha = 1f
                statusDot.setBackgroundResource(R.drawable.bg_dot_stopped)
                statusBadge.setBackgroundResource(R.drawable.bg_badge_idle)
                btnStartStop.text = "▶  NEW SESSION"
                btnStartStop.setBackgroundResource(R.drawable.bg_btn_start)
                sessionStatsRow.visibility = View.VISIBLE
                setExportEnabled(sessionFilePath.isNotEmpty())
            }
        }
    }

    private fun setExportEnabled(enabled: Boolean) {
        btnExportCsv.isEnabled = enabled
        btnSummary.isEnabled   = enabled
        btnExportCsv.alpha     = if (enabled) 1f else 0.4f
        btnSummary.alpha       = if (enabled) 1f else 0.4f
    }

    private fun pulseDot() {
        statusDot.animate().cancel()
        val pulse = object : Runnable {
            override fun run() {
                if (state != SessionState.RECORDING) return
                statusDot.animate().alpha(0.2f).setDuration(600).withEndAction {
                    statusDot.animate().alpha(1f).setDuration(600).withEndAction {
                        durationHandler.postDelayed(this, 0)
                    }.start()
                }.start()
            }
        }
        durationHandler.post(pulse)
    }

    // ── Prediction rendering ─────────────────────────────────────────────────

    private fun renderPrediction(label: String, confidence: Float) {
        tvPrediction.text = label
        tvPrediction.setTextColor(labelColor(label))
        tvConfidence.text = "${(confidence * 100).roundToInt()}%"
        tvConfidence.setTextColor(if (label == LABEL_UNKNOWN) 0xFF9090C0.toInt() else 0xFF2A3FBF.toInt())
        setConfBarWidth(confidence)

        // Track count of each detected label for the Summary screen
        if (label != LABEL_UNKNOWN) {
            labelCounts[label] = (labelCounts[label] ?: 0) + 1
        }
        if (label != LABEL_UNKNOWN && label != lastLabel) {
            eventCount++
            lastLabel = label
            tvEvents.text = "$eventCount"
        }
    }

    private fun resetPredictionUi() {
        tvPrediction.text = "Waiting…"
        tvPrediction.setTextColor(0xFF9090C0.toInt())
        tvConfidence.text = "--%"
        tvConfidence.setTextColor(0xFF9090C0.toInt())
        setConfBarWidth(0f)
    }

    private fun setConfBarWidth(fraction: Float) {
        val parent = confBarFill.parent as? View ?: return
        parent.post {
            val lp = confBarFill.layoutParams
            lp.width = (parent.width * fraction.coerceIn(0f, 1f)).toInt()
            confBarFill.layoutParams = lp
        }
    }

    private fun labelColor(label: String) = when (label) {
        "TREMOR"  -> 0xFF2A3FBF.toInt()
        "JERKING" -> 0xFFE65100.toInt()
        "TONIC"   -> 0xFF6A1B9A.toInt()
        "FALLS"   -> 0xFFC62828.toInt()
        else      -> 0xFF9090C0.toInt()
    }

    // ── Motion classifier stub ───────────────────────────────────────────────
    //
    // To wire in the real model after Phase 1 training:
    //   1. Convert best_cnn.h5 to .tflite, place in app/src/main/assets/
    //   2. Load scaler_params.json from assets
    //   3. Build inputBuffer[1][128][6] from predWindow:
    //        for each of the 128 entries, z-score each axis:
    //        (value - axis_mean) / axis_std  (values from scaler_params.json)
    //   4. Run: tfliteInterpreter.run(inputBuffer, outputBuffer)
    //        outputBuffer shape: [1][NUM_CLASSES]
    //   5. Return argmax index → label via labelEncoder + softmax score

    data class PredictionResult(val label: String, val confidence: Float)

    private fun classifyWindow(): PredictionResult {
        if (predWindow.size < PRED_WINDOW_SIZE) return PredictionResult(LABEL_UNKNOWN, 0f)
        // ── STUB: returns UNKNOWN until TFLite model is integrated ───────
        return PredictionResult(LABEL_UNKNOWN, 0f)
    }

    // ── CSV export (UR-L4) ───────────────────────────────────────────────────

    private fun exportCsv() {
        if (sessionFilePath.isEmpty()) {
            Toast.makeText(requireContext(), "Session file not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val src = File(sessionFilePath)
                if (!src.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "File not found: $sessionFilePath", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                downloads.mkdirs()
                val dst = File(downloads, src.name)
                src.copyTo(dst, overwrite = true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Saved to Downloads/${src.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Summary (UR-L6) ──────────────────────────────────────────────────────
    //
    // Replace the Toast below with NavController navigation once SummaryFragment
    // is added to your navigation graph. Pass these as Bundle arguments:
    //   sessionId, sessionFilePath, duration string, sampleCount, eventCount

    private fun openSummary() {
        val elapsedSec  = if (sessionStartMs > 0L)
            (System.currentTimeMillis() - sessionStartMs) / 1000L else 0L
        val durationStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

        // Build label distribution; if nothing detected show 100% UNKNOWN
        val counts = if (labelCounts.isEmpty()) {
            hashMapOf(LABEL_UNKNOWN to maxOf(sampleCount, 1))
        } else {
            HashMap(labelCounts)
        }

        val fragment = SummaryFragment.newInstance(
            sessionId   = sessionId.ifEmpty { "—" },
            duration    = durationStr,
            sampleCount = sampleCount,
            eventCount  = eventCount,
            filePath    = sessionFilePath,
            labelCounts = counts
        )

        // Overlay full-screen on android.R.id.content so no nav graph entry
        // is needed. The hardware back button and the ← button both call
        // supportFragmentManager.popBackStack() inside SummaryFragment.
        requireActivity().supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .add(android.R.id.content, fragment, SummaryFragment.TAG)
            .addToBackStack(SummaryFragment.TAG)
            .commit()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun fmt(v: Float)    = "%.2f".format(v)
    private fun fmtCount(n: Int) = if (n < 1000) "$n" else "%.1fk".format(n / 1000f)

    companion object {
        const val LABEL_LIVE    = "LIVE"
        const val LABEL_UNKNOWN = "UNKNOWN"
        const val NUM_CLASSES   = 11
    }
}