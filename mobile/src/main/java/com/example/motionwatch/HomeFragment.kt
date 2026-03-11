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
import java.util.Locale

class HomeFragment : Fragment() {

    // UI
    private lateinit var imgTopMotion: ImageView
    private lateinit var tvLastMotion: TextView

    private lateinit var spinnerLabel: Spinner
    private lateinit var btnStart: MaterialButton

    private lateinit var tvAccX: TextView
    private lateinit var tvAccY: TextView
    private lateinit var tvAccZ: TextView

    private lateinit var tvGyroPitch: TextView
    private lateinit var tvGyroRoll: TextView
    private lateinit var tvGyroYaw: TextView

    // State
    private var isLogging = false
    private var sessionId: String = ""
    private var selectedLabel: String = "Walking"

    private val labels = listOf(
        "Walking",
        "Running",
        "Sitting",
        "Falling"
    )

    // Receiver from teacher service
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {

                SensorLoggerService.ACTION_LOG_STATE -> {
                    val started = intent.getBooleanExtra("started", false)
                    isLogging = started
                    btnStart.text = if (started) "STOP" else "START"
                    tvLastMotion.text = if (started) selectedLabel else "Idle"
                    updateTopIconForLabel(if (started) selectedLabel else "Idle")
                }

                SensorLoggerService.ACTION_LIVE_SAMPLE -> {
                    val ax = intent.getFloatExtra("ax", Float.NaN)
                    val ay = intent.getFloatExtra("ay", Float.NaN)
                    val az = intent.getFloatExtra("az", Float.NaN)

                    val gx = intent.getFloatExtra("gx", Float.NaN)
                    val gy = intent.getFloatExtra("gy", Float.NaN)
                    val gz = intent.getFloatExtra("gz", Float.NaN)

                    tvAccX.text = "X-Axis: ${fmt(ax)}"
                    tvAccY.text = "Y-Axis: ${fmt(ay)}"
                    tvAccZ.text = "Z-Axis: ${fmt(az)}"

                    tvGyroPitch.text = "Pitch: ${fmt(gx)}"
                    tvGyroRoll.text  = "Roll: ${fmt(gy)}"
                    tvGyroYaw.text   = "Yaw: ${fmt(gz)}"
                }

                SensorLoggerService.ACTION_LOG_DONE -> {
                    // file already saved by service into logs folder
                    // history will pick it up automatically
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        imgTopMotion = view.findViewById(R.id.imgTopMotion)
        tvLastMotion = view.findViewById(R.id.tvLastMotion)

        spinnerLabel = view.findViewById(R.id.spinnerLabel)
        btnStart = view.findViewById(R.id.btnStart)

        tvAccX = view.findViewById(R.id.tvAccX)
        tvAccY = view.findViewById(R.id.tvAccY)
        tvAccZ = view.findViewById(R.id.tvAccZ)

        tvGyroPitch = view.findViewById(R.id.tvGyroPitch)
        tvGyroRoll  = view.findViewById(R.id.tvGyroRoll)
        tvGyroYaw   = view.findViewById(R.id.tvGyroYaw)

        // init UI
        tvLastMotion.text = "Idle"
        updateTopIconForLabel("Idle")
        resetSensorText()
        btnStart.text = "START"

        // Setup dropdown
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLabel.adapter = adapter
        spinnerLabel.setSelection(labels.indexOf(selectedLabel).coerceAtLeast(0))

        spinnerLabel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {

                // Don’t allow changing label mid-log
                if (isLogging) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Label locked")
                        .setMessage("Stop logging before changing the label.")
                        .setPositiveButton("OK", null)
                        .show()

                    // revert selection to current label
                    val currentIndex = labels.indexOf(selectedLabel).coerceAtLeast(0)
                    spinnerLabel.setSelection(currentIndex)
                    return
                }

                selectedLabel = labels[position]
                // update top icon immediately (even while idle)
                updateTopIconForLabel(selectedLabel)
                tvLastMotion.text = selectedLabel
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // START / STOP
        btnStart.setOnClickListener {
            if (!isLogging) startLogging() else stopLogging()
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

    private fun startLogging() {
        sessionId = System.currentTimeMillis().toString()

        val startIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_START
            putExtra("sessionId", sessionId)
            putExtra("label", selectedLabel)
            putExtra("startEpochMs", System.currentTimeMillis())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(startIntent)
        } else {
            requireContext().startService(startIntent)
        }

        // quick UI feedback
        isLogging = true
        btnStart.text = "STOP"
        tvLastMotion.text = selectedLabel
        updateTopIconForLabel(selectedLabel)
    }

    private fun stopLogging() {
        val stopIntent = Intent(requireContext(), SensorLoggerService::class.java).apply {
            action = SensorLoggerService.ACTION_STOP
        }
        requireContext().startService(stopIntent)

        isLogging = false
        btnStart.text = "START"
        tvLastMotion.text = "Idle"
        updateTopIconForLabel("Idle")
    }

    private fun updateTopIconForLabel(label: String) {
        val iconRes = when (label) {
            "Walking" -> R.drawable.ic_walk
            "Running" -> R.drawable.ic_running
            "Sitting" -> R.drawable.ic_sitting
            "Falling" -> R.drawable.ic_falling
            "Idle" -> R.drawable.ic_idle
            else -> R.drawable.ic_idle
        }
        imgTopMotion.setImageResource(iconRes)
    }

    private fun resetSensorText() {
        tvAccX.text = "X-Axis: --"
        tvAccY.text = "Y-Axis: --"
        tvAccZ.text = "Z-Axis: --"
        tvGyroPitch.text = "Pitch: --"
        tvGyroRoll.text = "Roll: --"
        tvGyroYaw.text = "Yaw: --"
    }

    private fun fmt(v: Float): String {
        return if (v.isNaN()) "--" else String.format(Locale.US, "%.2f", v)
    }
}