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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var tvDate: TextView? = null
    private var tvAccX: TextView? = null
    private var tvAccY: TextView? = null
    private var tvAccZ: TextView? = null
    private var tvGyroPitch: TextView? = null
    private var tvGyroRoll: TextView? = null
    private var tvGyroYaw: TextView? = null
    private var tvLastMotion: TextView? = null
    private var imgTopMotion: ImageView? = null

    // Individual detection totals
    private var tvFallsCount: TextView? = null
    private var tvTremorsCount: TextView? = null
    private var tvJerkingCount: TextView? = null
    private var tvTonicCount: TextView? = null
    private var tvWalkingCount: TextView? = null
    private var tvRunningCount: TextView? = null
    private var tvSittingCount: TextView? = null
    private var tvStandingCount: TextView? = null

    private var spinnerLabel: Spinner? = null
    private var btnStart: MaterialButton? = null

    private var isLogging = false
    private var currentSessionId: String = ""
    private var selectedLabel: String = "TREMOR"

    private val labels = listOf("JERKING", "TONIC", "FALLS", "TREMOR", "RUNNING", "WALKING", "SITTING", "STANDING")

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                WearCommandService.ACTION_UI_CMD -> {
                    val cmd = intent.getStringExtra("cmd") ?: return
                    if (cmd.equals("start", ignoreCase = true)) {
                        currentSessionId = intent.getStringExtra("sessionId") ?: "unknown"
                        isLogging = true
                        btnStart?.text = "STOP"
                        applyReceivedLabel(intent.getStringExtra("label") ?: "UNKNOWN")
                    } else if (cmd.equals("stop", ignoreCase = true)) {
                        isLogging = false
                        btnStart?.text = "START"
                        setIdleUI()
                    }
                }
                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started = intent.getBooleanExtra("started", false)
                    isLogging = started
                    btnStart?.text = if (started) "STOP" else "START"
                    if (started) {
                        currentSessionId = intent.getStringExtra("sessionId") ?: ""
                        applyReceivedLabel(intent.getStringExtra("label") ?: "")
                    } else {
                        setIdleUI()
                    }
                }
                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    tvAccX?.text = "X-Axis: ${fmt(intent.getFloatExtra("ax", Float.NaN))}"
                    tvAccY?.text = "Y-Axis: ${fmt(intent.getFloatExtra("ay", Float.NaN))}"
                    tvAccZ?.text = "Z-Axis: ${fmt(intent.getFloatExtra("az", Float.NaN))}"
                    tvGyroPitch?.text = "Pitch: ${fmt(intent.getFloatExtra("gx", Float.NaN))}"
                    tvGyroRoll?.text = "Roll: ${fmt(intent.getFloatExtra("gy", Float.NaN))}"
                    tvGyroYaw?.text = "Yaw: ${fmt(intent.getFloatExtra("gz", Float.NaN))}"
                    tvLastMotion?.text = selectedLabel
                    updateTopIconForLabel(selectedLabel)
                }
                SensorLoggerService.ACTION_LOG_DONE -> {
                    updateDashboardTotals()
                    setIdleUI()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        tvDate = view.findViewById(R.id.tvDate)
        tvAccX = view.findViewById(R.id.tvAccX); tvAccY = view.findViewById(R.id.tvAccY); tvAccZ = view.findViewById(R.id.tvAccZ)
        tvGyroPitch = view.findViewById(R.id.tvGyroPitch); tvGyroRoll = view.findViewById(R.id.tvGyroRoll); tvGyroYaw = view.findViewById(R.id.tvGyroYaw)
        tvLastMotion = view.findViewById(R.id.tvLastMotion); imgTopMotion = view.findViewById(R.id.imgTopMotion)

        // Totals
        tvFallsCount = view.findViewById(R.id.tvFallsCount)
        tvTremorsCount = view.findViewById(R.id.tvTremorsCount)
        tvJerkingCount = view.findViewById(R.id.tvJerkingCount)
        tvTonicCount = view.findViewById(R.id.tvTonicCount)
        tvWalkingCount = view.findViewById(R.id.tvWalkingCount)
        tvRunningCount = view.findViewById(R.id.tvRunningCount)
        tvSittingCount = view.findViewById(R.id.tvSittingCount)
        tvStandingCount = view.findViewById(R.id.tvStandingCount)

        spinnerLabel = view.findViewById(R.id.spinnerLabel); btnStart = view.findViewById(R.id.btnStart)

        setCurrentDate()
        setIdleUI()
        updateDashboardTotals()
        setupLabelDropdown()

        btnStart?.setOnClickListener {
            if (!isLogging) {
                currentSessionId = System.currentTimeMillis().toString()
                startPhoneLogging(currentSessionId, selectedLabel)
            } else {
                stopPhoneLogging()
            }
        }
        return view
    }

    private fun setCurrentDate() {
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)
        tvDate?.text = dateFormat.format(Calendar.getInstance().time).uppercase(Locale.US)
    }

    private fun setupLabelDropdown() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLabel?.adapter = adapter
        spinnerLabel?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (!isLogging) {
                    selectedLabel = labels[position]
                    tvLastMotion?.text = selectedLabel
                    updateTopIconForLabel(selectedLabel)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun applyReceivedLabel(label: String) {
        val normalized = label.uppercase(Locale.US)
        if (normalized.isEmpty()) return
        selectedLabel = normalized
        val index = labels.indexOf(normalized)
        if (index >= 0) spinnerLabel?.setSelection(index, false)
        tvLastMotion?.text = normalized
        updateTopIconForLabel(normalized)
    }

    private fun updateDashboardTotals() {
        val base = context?.getExternalFilesDir(null) ?: context?.filesDir ?: return
        val allFiles = (File(base, "logs").listFiles()?.toList().orEmpty() + File(base, "logs/watch").listFiles()?.toList().orEmpty())
        
        val counts = mutableMapOf<String, Int>().apply {
            labels.forEach { put(it, 0) }
        }

        allFiles.filter { it.isFile && it.name.endsWith(".csv", true) }.forEach { file ->
            val label = extractLabel(file.name).uppercase(Locale.US)
            if (counts.containsKey(label)) {
                counts[label] = (counts[label] ?: 0) + 1
            }
        }

        tvFallsCount?.text = (counts["FALLS"] ?: 0).toString()
        tvTremorsCount?.text = (counts["TREMOR"] ?: 0).toString()
        tvJerkingCount?.text = (counts["JERKING"] ?: 0).toString()
        tvTonicCount?.text = (counts["TONIC"] ?: 0).toString()
        tvWalkingCount?.text = (counts["WALKING"] ?: 0).toString()
        tvRunningCount?.text = (counts["RUNNING"] ?: 0).toString()
        tvSittingCount?.text = (counts["SITTING"] ?: 0).toString()
        tvStandingCount?.text = (counts["STANDING"] ?: 0).toString()
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
        ContextCompat.registerReceiver(requireContext(), logReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        try { requireContext().unregisterReceiver(logReceiver) } catch (_: Exception) {}
    }

    private fun startPhoneLogging(sessionId: String, label: String) {
        val intent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START
            putExtra("sessionId", sessionId)
            putExtra("label", label.uppercase(Locale.US))
            putExtra("startEpochMs", System.currentTimeMillis())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireContext().startForegroundService(intent)
        else requireContext().startService(intent)
    }

    private fun stopPhoneLogging() {
        requireContext().startService(Intent(requireContext(), SensorLoggerService::class.java).apply { action = SensorLoggerService.ACTION_STOP })
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
            else -> R.drawable.ic_idle
        }
        imgTopMotion?.setImageResource(iconRes)
    }

    private fun setIdleUI() {
        tvAccX?.text = "X-Axis: --"; tvAccY?.text = "Y-Axis: --"; tvAccZ?.text = "Z-Axis: --"
        tvGyroPitch?.text = "Pitch: --"; tvGyroRoll?.text = "Roll: --"; tvGyroYaw?.text = "Yaw: --"
        tvLastMotion?.text = "Idle"
        updateTopIconForLabel("IDLE")
        btnStart?.text = "START"
    }

    private fun fmt(v: Float) = if (v.isNaN()) "--" else String.format(Locale.US, "%.2f", v)
}
