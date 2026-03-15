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

/**
 * SessionsFragment: This screen shows the totals for each activity detected this month.
 * 
 * It does a few simple things:
 * 1. Finds the log files saved by the phone and the watch.
 * 2. Picks only the files from the current month.
 * 3. Counts how many times each activity (like walking or running) happened.
 * 4. Shows those counts on the screen.
 */
class SessionsFragment : Fragment() {

    // Text views where the totals will be shown
    private lateinit var tvMonth: TextView
    private lateinit var tvRunningTotal: TextView
    private lateinit var tvStandingTotal: TextView
    private lateinit var tvTremorTotal: TextView
    private lateinit var tvFallingTotal: TextView
    private lateinit var tvWalkingTotal: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Load the layout
        val view = inflater.inflate(R.layout.fragment_sessions, container, false)

        // Connect the code to the items in the layout
        tvMonth = view.findViewById(R.id.tvMonth)
        tvRunningTotal = view.findViewById(R.id.tvRunningTotal)
        tvStandingTotal = view.findViewById(R.id.tvStandingTotal)
        tvTremorTotal = view.findViewById(R.id.tvTremorTotal)
        tvFallingTotal = view.findViewById(R.id.tvFallingTotal)
        tvWalkingTotal = view.findViewById(R.id.tvWalkingTotal)

        // Show the current month and year at the top
        val monthStr = SimpleDateFormat("MMMM yyyy", Locale.US).format(Calendar.getInstance().time)
        tvMonth.text = monthStr.uppercase(Locale.US)

        // Go through the files and update the numbers on the screen
        updateDetectionTotals()

        // When the HISTORY button is clicked, go to the history page
        view.findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host, HistoryPageFragment())
                .addToBackStack("history") 
                .commit()
        }

        return view
    }

    /**
     * updateDetectionTotals: This looks at the log files and counts the activities.
     */
    private fun updateDetectionTotals() {
        // Find where the logs are stored
        val base = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        // Put all files from both folders into one list
        val allFiles = mutableListOf<File>()
        if (phoneDir.exists()) allFiles += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) allFiles += watchDir.listFiles()?.toList().orEmpty()

        // Get today's month and year
        val currentCal = Calendar.getInstance()
        val currentMonth = currentCal.get(Calendar.MONTH)
        val currentYear = currentCal.get(Calendar.YEAR)

        // Start everything at 0
        val counts = mutableMapOf(
            "RUNNING" to 0, 
            "STANDING" to 0, 
            "TREMOR" to 0, 
            "FALLING" to 0, 
            "WALKING" to 0
        )

        // Check every file
        allFiles.filter { it.isFile && it.name.lowercase(Locale.US).endsWith(".csv") }.forEach { file ->
            val fileCal = Calendar.getInstance()
            fileCal.timeInMillis = file.lastModified()

            // Only count it if it's from this month and year
            if (fileCal.get(Calendar.MONTH) == currentMonth && fileCal.get(Calendar.YEAR) == currentYear) {
                // Get the activity name from the file name
                val label = extractLabel(file.name).uppercase(Locale.US)
                
                // Add 1 to the count for that activity
                if (counts.containsKey(label)) {
                    counts[label] = counts[label]!! + 1
                }
            }
        }

        // Show the final counts on the screen
        tvRunningTotal.text = counts["RUNNING"].toString()
        tvStandingTotal.text = counts["STANDING"].toString()
        tvTremorTotal.text = counts["TREMOR"].toString()
        tvFallingTotal.text = counts["FALLING"].toString()
        tvWalkingTotal.text = counts["WALKING"].toString()
    }

    /**
     * extractLabel: Gets the activity type from the file name.
     */
    private fun extractLabel(filename: String): String {
        val parts = filename.split("_")
        // The activity name is usually the 4th part of the name
        return if (parts.size >= 5) parts[3] else "UNKNOWN"
    }
}
