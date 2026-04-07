package com.example.motionwatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SessionsFragment : Fragment() {

    private lateinit var tvMonth: TextView
    private lateinit var tvRunningTotal: TextView
    private lateinit var tvStandingTotal: TextView
    private lateinit var tvTremorTotal: TextView
    private lateinit var tvFallsTotal: TextView
    private lateinit var tvWalkingTotal: TextView
    private lateinit var tvSittingTotal: TextView
    private lateinit var tvJerkingTotal: TextView
    private lateinit var tvTonicTotal: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_sessions, container, false)

        tvMonth = view.findViewById(R.id.tvMonth)
        tvRunningTotal = view.findViewById(R.id.tvRunningTotal)
        tvStandingTotal = view.findViewById(R.id.tvStandingTotal)
        tvTremorTotal = view.findViewById(R.id.tvTremorTotal)
        tvFallsTotal = view.findViewById(R.id.tvFallsTotal)
        tvWalkingTotal = view.findViewById(R.id.tvWalkingTotal)
        tvSittingTotal = view.findViewById(R.id.tvSittingTotal)
        tvJerkingTotal = view.findViewById(R.id.tvJerkingTotal)
        tvTonicTotal = view.findViewById(R.id.tvTonicTotal)

        val monthStr = SimpleDateFormat("MMMM yyyy", Locale.US).format(Calendar.getInstance().time)
        tvMonth.text = monthStr.uppercase(Locale.US)

        updateDetectionTotals()

        view.findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, HistoryPageFragment())
                .addToBackStack("history")
                .commit()
        }

        return view
    }

    private fun updateDetectionTotals() {
        val base = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        val allFiles = mutableListOf<File>()
        if (phoneDir.exists()) allFiles += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) allFiles += watchDir.listFiles()?.toList().orEmpty()

        val currentCal = Calendar.getInstance()
        val currentMonth = currentCal.get(Calendar.MONTH)
        val currentYear = currentCal.get(Calendar.YEAR)

        val counts = mutableMapOf(
            "RUNNING" to 0,
            "STANDING" to 0,
            "TREMOR" to 0,
            "FALLS" to 0,
            "WALKING" to 0,
            "SITTING" to 0,
            "JERKING" to 0,
            "TONIC" to 0
        )

        allFiles
            .filter { it.isFile && it.name.lowercase(Locale.US).endsWith(".csv") }
            .forEach { file ->
                val fileCal = Calendar.getInstance()
                fileCal.timeInMillis = file.lastModified()

                if (fileCal.get(Calendar.MONTH) == currentMonth &&
                    fileCal.get(Calendar.YEAR) == currentYear
                ) {
                    val label = extractLabel(file.name).uppercase(Locale.US)
                    if (counts.containsKey(label)) {
                        counts[label] = counts[label]!! + 1
                    }
                }
            }

        tvRunningTotal.text = counts["RUNNING"].toString()
        tvStandingTotal.text = counts["STANDING"].toString()
        tvTremorTotal.text = counts["TREMOR"].toString()
        tvFallsTotal.text = counts["FALLS"].toString()
        tvWalkingTotal.text = counts["WALKING"].toString()
        tvSittingTotal.text = counts["SITTING"].toString()
        tvJerkingTotal.text = counts["JERKING"].toString()
        tvTonicTotal.text = counts["TONIC"].toString()
    }

    private fun extractLabel(filename: String): String {
        val parts = filename.split("_")
        return if (parts.size >= 5) parts[3] else "UNKNOWN"
    }
}