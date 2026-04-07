package com.example.motionwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale

class HomeFragment : Fragment() {

    // Live sensor UI
    private var tvAccX: TextView? = null
    private var tvAccY: TextView? = null
    private var tvAccZ: TextView? = null

    private var tvGyroPitch: TextView? = null
    private var tvGyroRoll: TextView? = null
    private var tvGyroYaw: TextView? = null

    // Top motion card
    private var tvLastMotion: TextView? = null
    private var imgTopMotion: ImageView? = null

    // Dashboard totals
    private var tvFallsCount: TextView? = null
    private var tvTremorsCount: TextView? = null
    private var tvAbnormalCount: TextView? = null

    // Controls
    private var spinnerLabel: Spinner? = null
    private var btnStart: MaterialButton? = null

    private var isLogging = false
    private var currentSessionId: String = ""
    private var selectedLabel: String = "TREMOR"

    private val labels = listOf(
        "JERKING",
        "TONIC",
        "FALLS",
        "TREMOR",
        "RUNNING",
        "WALKING",
        "SITTING",
        "STANDING"
    )

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                WearCommandService.ACTION_UI_CMD -> {
                    val cmd = intent.getStringExtra("cmd") ?: return

                    when (cmd.lowercase(Locale.US)) {
                        "start" -> {
                            val label = intent.getStringExtra("label") ?: "UNKNOWN"
                            val sessionId = intent.getStringExtra("sessionId") ?: "unknown"

                            currentSessionId = sessionId
                            isLogging = true
                            btnStart?.text = "STOP"

                            applyReceivedLabel(label)
                        }

                        "stop" -> {
                            isLogging = false
                            btnStart?.text = "START"
                            tvLastMotion?.text = "Idle"
                            updateTopIconForLabel("Idle")
                        }
                    }
                }

                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started = intent.getBooleanExtra("started", false)
                    val label = intent.getStringExtra("label")
                    val sessionId = intent.getStringExtra("sessionId")

                    isLogging = started
                    btnStart?.text = if (started) "STOP" else "START"

                    if (!sessionId.isNullOrBlank()) {
                        currentSessionId = sessionId
                    }

                    if (started && !label.isNullOrBlank()) {
                        applyReceivedLabel(label)
                    }

                    if (!started) {
                        tvLastMotion?.text = "Idle"
                        updateTopIconForLabel("Idle")
                    }
                }

                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    val ax = intent.getFloatExtra("ax", Float.NaN)
                    val ay = intent.getFloatExtra("ay", Float.NaN)
                    val az = intent.getFloatExtra("az", Float.NaN)

                    val gx = intent.getFloatExtra("gx", Float.NaN)
                    val gy = intent.getFloatExtra("gy", Float.NaN)
                    val gz = intent.getFloatExtra("gz", Float.NaN)

                    tvAccX?.text = "X-Axis: ${fmt(ax)}"
                    tvAccY?.text = "Y-Axis: ${fmt(ay)}"
                    tvAccZ?.text = "Z-Axis: ${fmt(az)}"

                    tvGyroPitch?.text = "Pitch: ${fmt(gx)}"
                    tvGyroRoll?.text = "Roll: ${fmt(gy)}"
                    tvGyroYaw?.text = "Yaw: ${fmt(gz)}"

                    tvLastMotion?.text = selectedLabel
                    updateTopIconForLabel(selectedLabel)
                }

                SensorLoggerService.ACTION_LOG_DONE -> {
                    updateDashboardTotals()
                    tvLastMotion?.text = "Idle"
                    updateTopIconForLabel("Idle")
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Sensor text
        tvAccX = view.findViewById(R.id.tvAccX)
        tvAccY = view.findViewById(R.id.tvAccY)
        tvAccZ = view.findViewById(R.id.tvAccZ)

        tvGyroPitch = view.findViewById(R.id.tvGyroPitch)
        tvGyroRoll = view.findViewById(R.id.tvGyroRoll)
        tvGyroYaw = view.findViewById(R.id.tvGyroYaw)

        // Top motion card
        tvLastMotion = view.findViewById(R.id.tvLastMotion)
        imgTopMotion = view.findViewById(R.id.imgTopMotion)

        // Totals
        tvFallsCount = view.findViewById(R.id.tvFallsCount)
        tvTremorsCount = view.findViewById(R.id.tvTremorsCount)
        tvAbnormalCount = view.findViewById(R.id.tvAbnormalCount)

        // Controls
        spinnerLabel = view.findViewById(R.id.spinnerLabel)
        btnStart = view.findViewById(R.id.btnStart)

        setIdleText()
        updateDashboardTotals()
        setupLabelDropdown()

        btnStart?.setOnClickListener {
            if (!isLogging) {
                currentSessionId = System.currentTimeMillis().toString()
                startPhoneLogging(
                    sessionId = currentSessionId,
                    label = selectedLabel
                )
            } else {
                stopPhoneLogging()
            }
        }

        return view
    }

    private fun setupLabelDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLabel?.adapter = adapter
        spinnerLabel?.setSelection(labels.indexOf(selectedLabel).coerceAtLeast(0), false)

        spinnerLabel?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isLogging) {
                    // silently revert selection
                    val currentIndex = labels.indexOf(selectedLabel).coerceAtLeast(0)
                    if (spinnerLabel?.selectedItemPosition != currentIndex) {
                        spinnerLabel?.setSelection(currentIndex, false)
                    }
                    return
                }

                selectedLabel = labels[position]
                tvLastMotion?.text = selectedLabel
                updateTopIconForLabel(selectedLabel)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun applyReceivedLabel(label: String) {
        val normalized = label.uppercase(Locale.US)
        selectedLabel = normalized

        val index = labels.indexOf(normalized)
        if (index >= 0 && spinnerLabel?.selectedItemPosition != index) {
            spinnerLabel?.setSelection(index, false)
        }

        tvLastMotion?.text = normalized
        updateTopIconForLabel(normalized)
    }

    private fun updateDashboardTotals() {
        val context = context ?: return
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        val allFiles = mutableListOf<File>()
        if (phoneDir.exists()) allFiles += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) allFiles += watchDir.listFiles()?.toList().orEmpty()

        var falls = 0
        var tremors = 0
        var abnormal = 0

        allFiles
            .filter { it.isFile && it.name.lowercase(Locale.US).endsWith(".csv") }
            .forEach { file ->
                val label = extractLabel(file.name).uppercase(Locale.US)
                when (label) {
                    "FALLS" -> falls++
                    "TREMOR" -> tremors++
                    "JERKING", "TONIC" -> abnormal++
                }
            }

        tvFallsCount?.text = falls.toString()
        tvTremorsCount?.text = tremors.toString()
        tvAbnormalCount?.text = abnormal.toString()
    }

    private fun extractLabel(filename: String): String {
        val parts = filename.split("_")
        return if (parts.size >= 5) parts[3] else "UNKNOWN"
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(SensorLoggerService.ACTION_LOG_STATE)
            addAction(SensorLoggerService.ACTION_LIVE_SAMPLE)
            addAction(SensorLoggerService.ACTION_LOG_DONE)
            addAction(WearCommandService.ACTION_UI_CMD)
        }
        ContextCompat.registerReceiver(
            requireContext(),
            logReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            requireContext().unregisterReceiver(logReceiver)
        } catch (_: Exception) {
        }
    }

    private fun startPhoneLogging(sessionId: String, label: String) {
        val normalized = label.uppercase(Locale.US)

        val startIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START
            putExtra("sessionId", sessionId)
            putExtra("label", normalized)
            putExtra("startEpochMs", System.currentTimeMillis())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(startIntent)
        } else {
            requireContext().startService(startIntent)
        }

        btnStart?.text = "STOP"
        applyReceivedLabel(normalized)
    }

    private fun stopPhoneLogging() {
        val stopIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_STOP
        }
        requireContext().startService(stopIntent)

        btnStart?.text = "START"
        tvLastMotion?.text = "Idle"
        updateTopIconForLabel("Idle")
    }

    private fun updateTopIconForLabel(label: String) {
        val iconRes = when (label.uppercase(Locale.US)) {
            "WALKING" -> R.drawable.ic_walk
            "RUNNING" -> R.drawable.ic_running
            "SITTING" -> R.drawable.ic_sitting
            "STANDING" -> R.drawable.ic_standing
            "FALLS" -> R.drawable.ic_falling
            "TREMOR" -> R.drawable.ic_tremor
            "JERKING" -> R.drawable.ic_jerking
            "TONIC" -> R.drawable.ic_tonic
            "IDLE" -> R.drawable.ic_idle
            else -> R.drawable.ic_idle
        }
        imgTopMotion?.setImageResource(iconRes)
    }

    private fun setIdleText() {
        tvAccX?.text = "X-Axis: --"
        tvAccY?.text = "Y-Axis: --"
        tvAccZ?.text = "Z-Axis: --"
        tvGyroPitch?.text = "Pitch: --"
        tvGyroRoll?.text = "Roll: --"
        tvGyroYaw?.text = "Yaw: --"
        tvLastMotion?.text = "Idle"
        updateTopIconForLabel("Idle")
        btnStart?.text = "START"
    }

    private fun fmt(v: Float): String {
        return if (v.isNaN()) "--" else String.format(Locale.US, "%.2f", v)
    }
}