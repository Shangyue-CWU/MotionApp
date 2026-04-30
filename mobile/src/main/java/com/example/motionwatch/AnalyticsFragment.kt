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
 * Chart tabs:
 *   ACC   → phone accelerometer
 *   GYRO  → phone gyroscope
 *   ALL   → all 6 phone axes
 *   WACC  → watch accelerometer  (deep-orange palette)
 *   WGYRO → watch gyroscope      (deep-orange palette)
 *
 * Sensor data sources:
 *   Phone: SensorLoggerService → ACTION_LIVE_SAMPLE → liveChart.addSample()
 *   Watch: WearCommandService  → ACTION_SENSOR_DATA → liveChart.addWatchSample()
 *
 * Watch remote control:
 *   WearCommandService → ACTION_LIVE_CMD (cmd=start|stop) drives this fragment.
 */
class AnalyticsFragment : Fragment() {

    // ── State ─────────────────────────────────────────────────────────────────

    private enum class SessionState { IDLE, RECORDING, ENDED }
    private var state = SessionState.IDLE

    // ── Session metadata ──────────────────────────────────────────────────────

    private var sessionId       = ""
    private var sessionStartMs  = 0L
    private val SAMPLES_PER_BROADCAST = 5
    private var sampleCount     = 0
    private var eventCount      = 0
    private var lastLabel       = ""
    private var sessionFilePath = ""
    private val labelCounts     = mutableMapOf<String, Int>()

    // ── Prediction window ─────────────────────────────────────────────────────

    private val predWindow       = ArrayDeque<FloatArray>()
    private val PRED_WINDOW_SIZE = 128
    private var broadcastCounter = 0
    private val PREDICT_EVERY_N  = 10

    // ── Duration timer ────────────────────────────────────────────────────────

    private val durationHandler  = Handler(Looper.getMainLooper())
    private val durationRunnable: Runnable = object : Runnable {
        override fun run() {
            if (state == SessionState.RECORDING) {
                val s = (System.currentTimeMillis() - sessionStartMs) / 1000L
                tvDuration.text = "%d:%02d".format(s / 60, s % 60)
                durationHandler.postDelayed(this, 1000L)
            }
        }
    }

    // ── Views ─────────────────────────────────────────────────────────────────

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
    private lateinit var tvChartLabel:    TextView
    private lateinit var liveChart:       LiveChartView

    // Tab TextViews
    private lateinit var tabAcc:   TextView
    private lateinit var tabGyro:  TextView
    private lateinit var tabAll:   TextView
    private lateinit var tabWAcc:  TextView
    private lateinit var tabWGyro: TextView

    // Phone pill rows
    private lateinit var pillsAcc:  LinearLayout
    private lateinit var pillsGyro: LinearLayout
    private lateinit var tvAX: TextView; private lateinit var tvAY: TextView
    private lateinit var tvAZ: TextView; private lateinit var tvGX: TextView
    private lateinit var tvGY: TextView; private lateinit var tvGZ: TextView

    // Watch pill rows (static in XML, shown only for WACC/WGYRO tabs)
    private lateinit var pillsWAcc:  LinearLayout
    private lateinit var pillsWGyro: LinearLayout
    private lateinit var tvWAX: TextView; private lateinit var tvWAY: TextView
    private lateinit var tvWAZ: TextView; private lateinit var tvWGX: TextView
    private lateinit var tvWGY: TextView; private lateinit var tvWGZ: TextView

    private lateinit var btnStartStop: Button
    private lateinit var btnClear:     LinearLayout
    private lateinit var btnExportCsv: Button
    private lateinit var btnSummary:   Button

    // ── Broadcast receiver ────────────────────────────────────────────────────

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                // Phone sensor reading (~20 Hz from SensorLoggerService)
                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    if (state != SessionState.RECORDING) return
                    val ax = intent.getFloatExtra("ax", 0f)
                    val ay = intent.getFloatExtra("ay", 0f)
                    val az = intent.getFloatExtra("az", 0f)
                    val gx = intent.getFloatExtra("gx", 0f)
                    val gy = intent.getFloatExtra("gy", 0f)
                    val gz = intent.getFloatExtra("gz", 0f)

                    liveChart.addSample(ax, ay, az, gx, gy, gz)

                    // Phone ACC pills
                    tvAX.text = "X: ${fmt(ax)}";  tvAY.text = "Y: ${fmt(ay)}";  tvAZ.text = "Z: ${fmt(az)}"
                    // Phone GYRO pills
                    tvGX.text = "GX: ${fmt(gx)}"; tvGY.text = "GY: ${fmt(gy)}"; tvGZ.text = "GZ: ${fmt(gz)}"

                    sampleCount += SAMPLES_PER_BROADCAST
                    tvSamples.text = fmtCount(sampleCount)

                    if (predWindow.size >= PRED_WINDOW_SIZE) predWindow.removeFirst()
                    predWindow.addLast(floatArrayOf(ax, ay, az, gx, gy, gz))
                    broadcastCounter++
                    if (broadcastCounter % PREDICT_EVERY_N == 0) {
                        val r = classifyWindow()
                        renderPrediction(r.label, r.confidence)
                    }
                }

                // Watch IMU data (10 Hz from WearCommandService)
                WearCommandService.ACTION_SENSOR_DATA -> {
                    if (state != SessionState.RECORDING) return
                    val ax = intent.getFloatExtra(WearCommandService.EXTRA_AX, 0f)
                    val ay = intent.getFloatExtra(WearCommandService.EXTRA_AY, 0f)
                    val az = intent.getFloatExtra(WearCommandService.EXTRA_AZ, 0f)
                    val gx = intent.getFloatExtra(WearCommandService.EXTRA_GX, 0f)
                    val gy = intent.getFloatExtra(WearCommandService.EXTRA_GY, 0f)
                    val gz = intent.getFloatExtra(WearCommandService.EXTRA_GZ, 0f)

                    // Route to the watch-specific chart buffers
                    liveChart.addWatchSample(ax, ay, az, gx, gy, gz)

                    // Update WACC and WGYRO pill values regardless of current tab
                    tvWAX.text = "WX: ${fmt(ax)}";   tvWAY.text = "WY: ${fmt(ay)}";  tvWAZ.text = "WZ: ${fmt(az)}"
                    tvWGX.text = "WGX: ${fmt(gx)}";  tvWGY.text = "WGY: ${fmt(gy)}"; tvWGZ.text = "WGZ: ${fmt(gz)}"
                }

                // Watch remote control (Live mode: watch taps Start or Stop)
                WearCommandService.ACTION_LIVE_CMD -> {
                    when (intent.getStringExtra("cmd")) {
                        "start" -> if (state == SessionState.IDLE)      startRecording()
                        "stop"  -> if (state == SessionState.RECORDING) stopRecording()
                    }
                }

                // Service state confirmation
                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started      = intent.getBooleanExtra("started", false)
                    val svcSessionId = intent.getStringExtra("sessionId") ?: ""
                    if (svcSessionId == sessionId && started && state != SessionState.RECORDING) {
                        applyState(SessionState.RECORDING)
                    }
                }

                // Session ended — CSV path available
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
            addAction(WearCommandService.ACTION_LIVE_CMD)
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

    // ── View binding ──────────────────────────────────────────────────────────

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
        tvChartLabel    = v.findViewById(R.id.tvChartLabel)
        liveChart       = v.findViewById(R.id.liveChart)
        tabAcc          = v.findViewById(R.id.tabAcc)
        tabGyro         = v.findViewById(R.id.tabGyro)
        tabAll          = v.findViewById(R.id.tabAll)
        tabWAcc         = v.findViewById(R.id.tabWAcc)
        tabWGyro        = v.findViewById(R.id.tabWGyro)
        pillsAcc        = v.findViewById(R.id.pillsAcc)
        pillsGyro       = v.findViewById(R.id.pillsGyro)
        pillsWAcc       = v.findViewById(R.id.pillsWAcc)
        pillsWGyro      = v.findViewById(R.id.pillsWGyro)
        tvAX  = v.findViewById(R.id.tvAX);  tvAY  = v.findViewById(R.id.tvAY)
        tvAZ  = v.findViewById(R.id.tvAZ);  tvGX  = v.findViewById(R.id.tvGX)
        tvGY  = v.findViewById(R.id.tvGY);  tvGZ  = v.findViewById(R.id.tvGZ)
        tvWAX = v.findViewById(R.id.tvWAX); tvWAY = v.findViewById(R.id.tvWAY)
        tvWAZ = v.findViewById(R.id.tvWAZ); tvWGX = v.findViewById(R.id.tvWGX)
        tvWGY = v.findViewById(R.id.tvWGY); tvWGZ = v.findViewById(R.id.tvWGZ)
        btnStartStop    = v.findViewById(R.id.btnStartStop)
        btnClear        = v.findViewById(R.id.btnClear)
        btnExportCsv    = v.findViewById(R.id.btnExportCsv)
        btnSummary      = v.findViewById(R.id.btnSummary)
    }

    // ── Chart tabs ────────────────────────────────────────────────────────────

    private fun setupChartTabs() {
        tabAcc.setOnClickListener   { selectTab(LiveChartView.DisplayMode.ACC)   }
        tabGyro.setOnClickListener  { selectTab(LiveChartView.DisplayMode.GYRO)  }
        tabAll.setOnClickListener   { selectTab(LiveChartView.DisplayMode.ALL)   }
        tabWAcc.setOnClickListener  { selectTab(LiveChartView.DisplayMode.WACC)  }
        tabWGyro.setOnClickListener { selectTab(LiveChartView.DisplayMode.WGYRO) }
    }

    private val allTabs get() = listOf(tabAcc, tabGyro, tabAll, tabWAcc, tabWGyro)

    private fun selectTab(mode: LiveChartView.DisplayMode) {
        liveChart.displayMode = mode

        // Tab backgrounds and text colours
        val active   = R.drawable.bg_tab_active
        val inactive = R.drawable.bg_tab_inactive
        allTabs.forEach { it.setBackgroundResource(inactive); it.setTextColor(0xFF6B6B9A.toInt()) }
        val activeTab = when (mode) {
            LiveChartView.DisplayMode.ACC   -> tabAcc
            LiveChartView.DisplayMode.GYRO  -> tabGyro
            LiveChartView.DisplayMode.ALL   -> tabAll
            LiveChartView.DisplayMode.WACC  -> tabWAcc
            LiveChartView.DisplayMode.WGYRO -> tabWGyro
        }
        activeTab.setBackgroundResource(active)
        activeTab.setTextColor(0xFFFFFFFF.toInt())

        // Watch tabs use a green active colour to reinforce the source
        if (mode == LiveChartView.DisplayMode.WACC || mode == LiveChartView.DisplayMode.WGYRO) {
            activeTab.setBackgroundResource(R.drawable.bg_btn_start)  // green pill
        }

        // Pill row visibility
        pillsAcc.visibility   = if (mode == LiveChartView.DisplayMode.ACC  ||
            mode == LiveChartView.DisplayMode.ALL) View.VISIBLE else View.GONE
        pillsGyro.visibility  = if (mode == LiveChartView.DisplayMode.GYRO ||
            mode == LiveChartView.DisplayMode.ALL) View.VISIBLE else View.GONE
        pillsWAcc.visibility  = if (mode == LiveChartView.DisplayMode.WACC)  View.VISIBLE else View.GONE
        pillsWGyro.visibility = if (mode == LiveChartView.DisplayMode.WGYRO) View.VISIBLE else View.GONE

        // Section label
        tvChartLabel.text = when (mode) {
            LiveChartView.DisplayMode.ACC   -> "ACCELEROMETER"
            LiveChartView.DisplayMode.GYRO  -> "GYROSCOPE"
            LiveChartView.DisplayMode.ALL   -> "ALL AXES"
            LiveChartView.DisplayMode.WACC  -> "WATCH ACCELEROMETER"
            LiveChartView.DisplayMode.WGYRO -> "WATCH GYROSCOPE"
        }
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

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

    // ── Session control ───────────────────────────────────────────────────────

    private fun startRecording() {
        sessionId        = UUID.randomUUID().toString().replace("-", "").take(12)
        sessionStartMs   = System.currentTimeMillis()
        sampleCount      = 0; eventCount = 0; broadcastCounter = 0
        lastLabel        = ""; sessionFilePath = ""
        labelCounts.clear(); predWindow.clear()
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
        applyState(SessionState.RECORDING)
        durationHandler.post(durationRunnable)
    }

    private fun stopRecording() {
        requireContext().startService(
            Intent(requireContext(), SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_STOP
            }
        )
        durationHandler.removeCallbacks(durationRunnable)
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
        liveChart.clear(); predWindow.clear()
        sampleCount = 0; eventCount = 0; broadcastCounter = 0
        lastLabel = ""; sessionFilePath = ""; labelCounts.clear()
        tvDuration.text = "0:00"; tvSamples.text = "0"; tvEvents.text = "0"
        resetPredictionUi()
        applyState(SessionState.IDLE)
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private fun applyState(newState: SessionState) {
        state = newState
        when (newState) {
            SessionState.IDLE -> {
                tvStatus.text = "IDLE"; tvStatus.setTextColor(0xFF757575.toInt())
                statusDot.animate().cancel(); statusDot.alpha = 1f
                statusDot.setBackgroundResource(R.drawable.bg_dot_idle)
                statusBadge.setBackgroundResource(R.drawable.bg_badge_idle)
                btnStartStop.text = "▶  START SESSION"
                btnStartStop.setBackgroundResource(R.drawable.bg_btn_start)
                sessionStatsRow.visibility = View.GONE
                setExportEnabled(false)
            }
            SessionState.RECORDING -> {
                tvStatus.text = "RECORDING"; tvStatus.setTextColor(0xFF2E7D32.toInt())
                statusDot.setBackgroundResource(R.drawable.bg_dot_live)
                statusBadge.setBackgroundResource(R.drawable.bg_badge_live)
                pulseDot()
                btnStartStop.text = "■  STOP SESSION"
                btnStartStop.setBackgroundResource(R.drawable.bg_btn_stop)
                sessionStatsRow.visibility = View.VISIBLE
                setExportEnabled(false)
            }
            SessionState.ENDED -> {
                tvStatus.text = "STOPPED"; tvStatus.setTextColor(0xFFEF5350.toInt())
                statusDot.animate().cancel(); statusDot.alpha = 1f
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
        btnExportCsv.isEnabled = enabled; btnSummary.isEnabled = enabled
        btnExportCsv.alpha = if (enabled) 1f else 0.4f
        btnSummary.alpha   = if (enabled) 1f else 0.4f
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

    // ── Prediction rendering ──────────────────────────────────────────────────

    private fun renderPrediction(label: String, confidence: Float) {
        tvPrediction.text = label
        tvPrediction.setTextColor(labelColor(label))
        tvConfidence.text = "${(confidence * 100).roundToInt()}%"
        tvConfidence.setTextColor(if (label == LABEL_UNKNOWN) 0xFF9090C0.toInt() else 0xFF2A3FBF.toInt())
        setConfBarWidth(confidence)
        if (label != LABEL_UNKNOWN) labelCounts[label] = (labelCounts[label] ?: 0) + 1
        if (label != LABEL_UNKNOWN && label != lastLabel) {
            eventCount++; lastLabel = label; tvEvents.text = "$eventCount"
        }
    }

    private fun resetPredictionUi() {
        tvPrediction.text = "Waiting…"; tvPrediction.setTextColor(0xFF9090C0.toInt())
        tvConfidence.text = "--%";      tvConfidence.setTextColor(0xFF9090C0.toInt())
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

    // ── Classifier stub ───────────────────────────────────────────────────────

    data class PredictionResult(val label: String, val confidence: Float)

    private fun classifyWindow(): PredictionResult {
        if (predWindow.size < PRED_WINDOW_SIZE) return PredictionResult(LABEL_UNKNOWN, 0f)
        // Replace with TFLite inference — see Phase1_Modelling_Roadmap §6
        return PredictionResult(LABEL_UNKNOWN, 0f)
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private fun exportCsv() {
        if (sessionFilePath.isEmpty()) {
            Toast.makeText(requireContext(), "Session file not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val src = File(sessionFilePath)
                if (!src.exists()) { toast("File not found: $sessionFilePath"); return@launch }
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                downloads.mkdirs()
                src.copyTo(File(downloads, src.name), overwrite = true)
                toast("Saved to Downloads/${src.name}")
            } catch (e: Exception) { toast("Export failed: ${e.message}") }
        }
    }

    private suspend fun toast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    private fun openSummary() {
        val s   = (System.currentTimeMillis() - sessionStartMs) / 1000L
        val dur = "%d:%02d".format(s / 60, s % 60)
        val counts = if (labelCounts.isEmpty()) hashMapOf(LABEL_UNKNOWN to maxOf(sampleCount, 1))
        else HashMap(labelCounts)

        requireActivity().supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left, android.R.anim.slide_out_right,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right
            )
            .add(android.R.id.content,
                SummaryFragment.newInstance(
                    sessionId   = sessionId.ifEmpty { "—" },
                    duration    = dur,
                    sampleCount = sampleCount,
                    eventCount  = eventCount,
                    filePath    = sessionFilePath,
                    labelCounts = counts
                ), SummaryFragment.TAG
            )
            .addToBackStack(SummaryFragment.TAG)
            .commit()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fmt(v: Float)    = "%.2f".format(v)
    private fun fmtCount(n: Int) = if (n < 1000) "$n" else "%.1fk".format(n / 1000f)

    companion object {
        const val LABEL_LIVE    = "LIVE"
        const val LABEL_UNKNOWN = "UNKNOWN"
        const val NUM_CLASSES   = 11
    }
}