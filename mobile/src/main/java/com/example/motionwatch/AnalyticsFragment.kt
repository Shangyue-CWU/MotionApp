package com.example.motionwatch

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.Locale

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var tvCurrentLabel: TextView
    private lateinit var tvConfidence: TextView

    private lateinit var btnStartStop: Button
    private lateinit var btnSaveCsv: Button
    private lateinit var btnShareCsv: Button

    private lateinit var tvSessionState: TextView
    private lateinit var tvSessionTimer: TextView
    private lateinit var tvSessionFile: TextView

    private lateinit var tvLivePrediction: TextView
    private lateinit var tvLiveScore: TextView
    private lateinit var tvLiveHint: TextView

    private lateinit var btnClear: Button

    private var isRunning = false
    private var sessionStartTime = 0L
    private var latestOutputFileName: String? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val elapsedMs = System.currentTimeMillis() - sessionStartTime
            tvSessionTimer.text = "Duration: ${formatElapsed(elapsedMs)}"
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupButtons()
        renderIdleState()
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacks(timerRunnable)
        super.onDestroyView()
    }

    private fun bindViews(view: View) {
        tvCurrentLabel = view.findViewById(R.id.tvCurrentLabel)
        tvConfidence = view.findViewById(R.id.tvConfidence)

        btnStartStop = view.findViewById(R.id.btnStartStop)
        btnSaveCsv = view.findViewById(R.id.btnSaveCsv)
        btnShareCsv = view.findViewById(R.id.btnShareCsv)
        btnClear = view.findViewById(R.id.btnClear)

        tvSessionState = view.findViewById(R.id.tvSessionState)
        tvSessionTimer = view.findViewById(R.id.tvSessionTimer)
        tvSessionFile = view.findViewById(R.id.tvSessionFile)

        tvLivePrediction = view.findViewById(R.id.tvLivePrediction)
        tvLiveScore = view.findViewById(R.id.tvLiveScore)
        tvLiveHint = view.findViewById(R.id.tvLiveHint)
    }

    private fun setupButtons() {
        btnStartStop.setOnClickListener {
            if (isRunning) {
                stopRecognitionSession()
            } else {
                startRecognitionSession()
            }
        }

        btnSaveCsv.setOnClickListener {
            val fileName = latestOutputFileName ?: "--"
            Toast.makeText(
                requireContext(),
                "Save CSV clicked: $fileName",
                Toast.LENGTH_SHORT
            ).show()

            // TODO: connect to actual CSV export/save logic
        }

        btnShareCsv.setOnClickListener {
            val fileName = latestOutputFileName ?: "--"
            Toast.makeText(
                requireContext(),
                "Share CSV clicked: $fileName",
                Toast.LENGTH_SHORT
            ).show()

            // TODO: connect to actual CSV share logic
        }

        btnClear.setOnClickListener {
            clearAllData()
        }
    }

    private fun startRecognitionSession() {
        isRunning = true
        sessionStartTime = System.currentTimeMillis()
        latestOutputFileName = "analytics_${sessionStartTime}.csv"

        btnStartStop.text = "STOP"
        btnStartStop.backgroundTintList =
            ColorStateList.valueOf(0xFFB07A7A.toInt())

        btnSaveCsv.isEnabled = false
        btnShareCsv.isEnabled = false
        btnSaveCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())
        btnShareCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())

        tvSessionState.text = "State: Running"
        tvSessionTimer.text = "Duration: 00:00"
        tvSessionFile.text = "Output File: $latestOutputFileName"

        tvLivePrediction.text = "Prediction: Initializing..."
        tvLiveScore.text = "Score: --"
        tvLiveHint.text = "Recognition session started."

        tvCurrentLabel.text = "Listening"
        tvConfidence.text = "Confidence: --"

        uiHandler.removeCallbacks(timerRunnable)
        uiHandler.post(timerRunnable)

        // TODO:
        // 1. Start backend recognition session
        // 2. Register broadcast receiver / callback
        // 3. Begin analytics CSV writing

        Toast.makeText(requireContext(), "Recognition started.", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecognitionSession() {
        isRunning = false
        uiHandler.removeCallbacks(timerRunnable)

        btnStartStop.text = "START"
        btnStartStop.backgroundTintList =
            ColorStateList.valueOf(0xFF8EA9A0.toInt())

        btnSaveCsv.isEnabled = true
        btnShareCsv.isEnabled = true
        btnSaveCsv.backgroundTintList =
            ColorStateList.valueOf(0xFF7C96B2.toInt())
        btnShareCsv.backgroundTintList =
            ColorStateList.valueOf(0xFF7C96B2.toInt())

        tvSessionState.text = "State: Completed"
        tvLiveHint.text = "Recognition session finished. CSV is ready."
        tvCurrentLabel.text = "Idle"
        tvConfidence.text = "Confidence: --"

        // TODO:
        // 1. Stop backend recognition session
        // 2. Finalize analytics CSV
        // 3. Save real file path / URI

        Toast.makeText(requireContext(), "Recognition stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun renderIdleState() {
        tvCurrentLabel.text = "Idle"
        tvConfidence.text = "Confidence: --"

        tvSessionState.text = "State: Idle"
        tvSessionTimer.text = "Duration: 00:00"
        tvSessionFile.text = "Output File: --"

        tvLivePrediction.text = "Prediction: Idle"
        tvLiveScore.text = "Score: --"
        tvLiveHint.text = "Waiting for recognition session to start."

        btnStartStop.text = "START"
        btnStartStop.backgroundTintList =
            ColorStateList.valueOf(0xFF8EA9A0.toInt())

        btnSaveCsv.isEnabled = false
        btnShareCsv.isEnabled = false
        btnSaveCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())
        btnShareCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())
    }

    /**
     * Call this later from your service / receiver when ML output arrives.
     */
    fun updateRecognitionResult(label: String, confidence: Float) {
        tvCurrentLabel.text = label
        tvConfidence.text = "Confidence: ${formatConfidence(confidence)}"

        tvLivePrediction.text = "Prediction: $label"
        tvLiveScore.text = "Score: ${formatConfidence(confidence)}"
        tvLiveHint.text = "Real-time recognition is active."
    }

    /**
     * Optional helper for when backend finishes and returns a real CSV file name.
     */
    fun updateOutputFile(fileName: String) {
        latestOutputFileName = fileName
        tvSessionFile.text = "Output File: $fileName"
    }

    private fun clearAllData() {

        // If running → force stop first (important)
        if (isRunning) {
            stopRecognitionSession()
        }

        // Reset internal state
        isRunning = false
        sessionStartTime = 0L
        latestOutputFileName = null

        uiHandler.removeCallbacks(timerRunnable)

        // Reset UI
        tvCurrentLabel.text = "Idle"
        tvConfidence.text = "Confidence: --"

        tvSessionState.text = "State: Idle"
        tvSessionTimer.text = "Duration: 00:00"
        tvSessionFile.text = "Output File: --"

        tvLivePrediction.text = "Prediction: Idle"
        tvLiveScore.text = "Score: --"
        tvLiveHint.text = "All data cleared."

        // Reset buttons
        btnStartStop.text = "START"
        btnStartStop.backgroundTintList =
            ColorStateList.valueOf(0xFF8EA9A0.toInt())

        btnSaveCsv.isEnabled = false
        btnShareCsv.isEnabled = false

        btnSaveCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())
        btnShareCsv.backgroundTintList =
            ColorStateList.valueOf(0xFFB0B7C3.toInt())

        Toast.makeText(requireContext(), "All data cleared.", Toast.LENGTH_SHORT).show()

        // TODO (IMPORTANT for next step):
        // - Tell SensorLoggerService to reset session
        // - Clear any cached ML buffers (Analysis.java)
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun formatConfidence(confidence: Float): String {
        return String.format(Locale.US, "%.2f%%", confidence * 100f)
    }
}