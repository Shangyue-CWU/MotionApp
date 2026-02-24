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

class HomeFragment : Fragment() {

    private var tvAccX: TextView? = null
    private var tvAccY: TextView? = null
    private var tvAccZ: TextView? = null

    private var tvGyroPitch: TextView? = null
    private var tvGyroRoll: TextView? = null
    private var tvGyroYaw: TextView? = null

    private var tvLastMotion: TextView? = null
    private var btnStart: MaterialButton? = null

    private var isLogging = false
    private var currentSessionId: String = ""

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {

                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started = intent.getBooleanExtra("started", false)
                    isLogging = started
                    btnStart?.text = if (started) "STOP" else "START"
                }

                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    // EXACT extras used by teacher SensorLoggerService.java
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

                    // Optional: update "last tracked motion" label (placeholder)
                    tvLastMotion?.text = "Monitoring…"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvAccX = view.findViewById(R.id.tvAccX)
        tvAccY = view.findViewById(R.id.tvAccY)
        tvAccZ = view.findViewById(R.id.tvAccZ)

        tvGyroPitch = view.findViewById(R.id.tvGyroPitch)
        tvGyroRoll  = view.findViewById(R.id.tvGyroRoll)
        tvGyroYaw   = view.findViewById(R.id.tvGyroYaw)

        tvLastMotion = view.findViewById(R.id.tvLastMotion)
        btnStart = view.findViewById(R.id.btnStart)

        setIdleText()

        btnStart?.setOnClickListener {
            if (!isLogging) {
                // NEW session each start (like teacher behavior)
                currentSessionId = System.currentTimeMillis().toString()
                startPhoneLogging(
                    sessionId = currentSessionId,
                    label = "epilepsy_monitor" // change label if you want different filenames
                )
            } else {
                stopPhoneLogging()
            }
        }

        return view
    }

    override fun onStart() {
        super.onStart()

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
        try {
            requireContext().unregisterReceiver(logReceiver)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        // Optional: if you want to stop logging when leaving the dashboard, uncomment:
        // if (isLogging) stopPhoneLogging()
    }

    private fun startPhoneLogging(sessionId: String, label: String) {
        val startIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START
            putExtra("sessionId", sessionId)                 // teacher service reads this
            putExtra("label", label)                         // teacher service reads this
            putExtra("startEpochMs", System.currentTimeMillis()) // teacher service reads this
        }

        // Foreground service on newer Android versions is safer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(startIntent)
        } else {
            requireContext().startService(startIntent)
        }

        // UI feels instant
        btnStart?.text = "STOP"
        tvLastMotion?.text = "Monitoring…"
    }

    private fun stopPhoneLogging() {
        val stopIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_STOP
        }
        requireContext().startService(stopIntent)

        // UI feels instant; service will also broadcast LOG_STATE
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