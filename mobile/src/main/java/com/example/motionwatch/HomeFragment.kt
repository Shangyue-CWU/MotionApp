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
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.util.Locale
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Calendar

/**
 * HomeFragment: This is the main dashboard alex.
 * 
 * It does a few things:
 * 1. Shows live sensor data as it happens.
 * 2. Counts how many falls, tremors, and abnormal activities are in the logs.
 * 3. Lets you start and stop monitoring with one button.
 */
class HomeFragment : Fragment() {

    // These show the live sensor numbers
    private var tvAccX: TextView? = null
    private var tvAccY: TextView? = null
    private var tvAccZ: TextView? = null

    private var tvGyroPitch: TextView? = null
    private var tvGyroRoll: TextView? = null
    private var tvGyroYaw: TextView? = null

    private var tvLastMotion: TextView? = null
    private var btnStart: MaterialButton? = null

    // These show the total counts on the screen
    private var tvFallsCount: TextView? = null
    private var tvTremorsCount: TextView? = null
    private var tvAbnormalCount: TextView? = null

    private var isLogging = false
    private var currentSessionId: String = ""

    // This handles messages from the sensor service
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                // Service tells us if it started or stopped
                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started = intent.getBooleanExtra("started", false)
                    isLogging = started
                    btnStart?.text = if (started) "STOP" else "START"
                }

                // Service sends live sensor samples
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
                    tvGyroRoll?.text  = "Roll: ${fmt(gy)}"
                    tvGyroYaw?.text   = "Yaw: ${fmt(gz)}"

                    tvLastMotion?.text = "Monitoring…"
                }
                
                // When recording is done, we update the totals on the dashboard
                SensorLoggerService.ACTION_LOG_DONE -> {
                    updateDashboardTotals()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Load the dashboard layout
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Find all the text views and buttons in the layout
        tvAccX = view.findViewById(R.id.tvAccX)
        tvAccY = view.findViewById(R.id.tvAccY)
        tvAccZ = view.findViewById(R.id.tvAccZ)

        tvGyroPitch = view.findViewById(R.id.tvGyroPitch)
        tvGyroRoll  = view.findViewById(R.id.tvGyroRoll)
        tvGyroYaw   = view.findViewById(R.id.tvGyroYaw)

        tvLastMotion = view.findViewById(R.id.tvLastMotion)
        btnStart = view.findViewById(R.id.btnStart)

        tvFallsCount = view.findViewById(R.id.tvFallsCount)
        tvTremorsCount = view.findViewById(R.id.tvTremorsCount)
        tvAbnormalCount = view.findViewById(R.id.tvAbnormalCount)

        // Start with blank numbers and then load real ones from files
        setIdleText()
        updateDashboardTotals()

        // Toggle button logic
        btnStart?.setOnClickListener {
            if (!isLogging) {
                currentSessionId = System.currentTimeMillis().toString()
                startPhoneLogging(
                    sessionId = currentSessionId,
                    label = "epilepsy_monitor" 
                )
            } else {
                stopPhoneLogging()
            }
        }

        return view
    }

    /**
     * updateDashboardTotals: Finds the logs and updates the counts on the screen.
     */
    private fun updateDashboardTotals() {
        val context = context ?: return
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        // Grab all files from phone and watch folders
        val allFiles = mutableListOf<File>()
        if (phoneDir.exists()) allFiles += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) allFiles += watchDir.listFiles()?.toList().orEmpty()

        var falls = 0
        var tremors = 0
        var abnormal = 0

        // Count based on the name of the file
        allFiles.filter { it.isFile && it.name.lowercase(Locale.US).endsWith(".csv") }.forEach { file ->
            val label = extractLabel(file.name).uppercase(Locale.US)
            when (label) {
                "FALLING" -> falls++
                "TREMOR" -> tremors++
                "ABNORMAL", "RUNNING" -> abnormal++ 
            }
        }

        // Show the totals
        tvFallsCount?.text = falls.toString()
        tvTremorsCount?.text = tremors.toString()
        tvAbnormalCount?.text = abnormal.toString()
    }

    /**
     * extractLabel: Gets the activity name from the file name.
     */
    private fun extractLabel(filename: String): String {
        val parts = filename.split("_")
        return if (parts.size >= 5) parts[3] else "UNKNOWN"
    }

    override fun onStart() {
        super.onStart()
        // Listen for sensor service updates
        val filter = IntentFilter().apply {
            addAction(SensorLoggerService.ACTION_LOG_STATE)
            addAction(SensorLoggerService.ACTION_LIVE_SAMPLE)
            addAction(SensorLoggerService.ACTION_LOG_DONE)
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
        // Stop listening when we leave
        try {
            requireContext().unregisterReceiver(logReceiver)
        } catch (_: Exception) {}
    }

    private fun startPhoneLogging(sessionId: String, label: String) {
        val startIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START
            putExtra("sessionId", sessionId)
            putExtra("label", label)
            putExtra("startEpochMs", System.currentTimeMillis())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(startIntent)
        } else {
            requireContext().startService(startIntent)
        }
        btnStart?.text = "STOP"
        tvLastMotion?.text = "Monitoring…"
    }

    private fun stopPhoneLogging() {
        val stopIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_STOP
        }
        requireContext().startService(stopIntent)
        btnStart?.text = "START"
        tvLastMotion?.text = "Idle"
    }

    private fun setIdleText() {
        tvAccX?.text = "X-Axis: --"
        tvAccY?.text = "Y-Axis: --"
        tvAccZ?.text = "Z-Axis: --"
        tvGyroPitch?.text = "Pitch: --"
        tvGyroRoll?.text = "Roll: --"
        tvGyroYaw?.text = "Yaw: --"
        tvLastMotion?.text = "Idle"
        btnStart?.text = "START"
    }

    private fun fmt(v: Float): String {
        return if (v.isNaN()) "--" else String.format(Locale.US, "%.2f", v)
    }
}