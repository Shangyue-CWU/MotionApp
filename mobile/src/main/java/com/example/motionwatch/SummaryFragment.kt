package com.example.motionwatch

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * SummaryFragment — full-screen Session Summary page.
 *
 * Matches HTML mockup Screen 4:
 *   - Page heading
 *   - Meta row  (Date / Duration / Samples / Events)
 *   - Donut chart  + legend  (Motion Breakdown)
 *   - Detection log  rows
 *   - Export CSV + Back to Live buttons
 *
 * Navigation: shown via a direct fragment transaction on android.R.id.content
 * from AnalyticsFragment.openSummary(). No nav graph entry required.
 * The back button (hardware and ← button) pops this fragment off.
 *
 * When no motion has been detected, the entire session is shown as
 * "UNKNOWN / IDLE: 100%".
 */
class SummaryFragment : Fragment() {

    // ── Arguments ────────────────────────────────────────────────────────────

    private val sessionId   by lazy { arguments?.getString(ARG_SESSION_ID)   ?: "—" }
    private val duration    by lazy { arguments?.getString(ARG_DURATION)      ?: "0:00" }
    private val sampleCount by lazy { arguments?.getInt(ARG_SAMPLES)         ?: 0 }
    private val eventCount  by lazy { arguments?.getInt(ARG_EVENTS)          ?: 0 }
    private val filePath    by lazy { arguments?.getString(ARG_FILE_PATH)     ?: "" }

    @Suppress("UNCHECKED_CAST")
    private val labelCounts by lazy {
        (arguments?.getSerializable(ARG_LABEL_COUNTS) as? HashMap<String, Int>)
            ?: hashMapOf()
    }

    // ── Label config ─────────────────────────────────────────────────────────

    private val labelColor = mapOf(
        "TREMOR"  to Color.parseColor("#4A6CF7"),
        "JERKING" to Color.parseColor("#FFA726"),
        "FALLS"   to Color.parseColor("#EF5350"),
        "TONIC"   to Color.parseColor("#AB47BC"),
        "UNKNOWN" to Color.parseColor("#9090C0")
    )

    // Display order for the donut and detection log
    private val labelOrder = listOf("TREMOR", "JERKING", "FALLS", "TONIC", "UNKNOWN")

    // ── Inflate ───────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_summary, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Push content below the system status bar so the top bar is
        // never clipped — handles any screen density or Android version.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        // Hardware back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { navigateBack() }
            }
        )

        // ← button in top bar
        view.findViewById<TextView>(R.id.btnBack).setOnClickListener { navigateBack() }

        // Populate metadata
        view.findViewById<TextView>(R.id.tvSumDate).text     = todayDate()
        view.findViewById<TextView>(R.id.tvSumDuration).text = duration
        view.findViewById<TextView>(R.id.tvSumSamples).text  = fmtCount(sampleCount)
        view.findViewById<TextView>(R.id.tvSumEvents).text   = "$eventCount"

        // Build effective counts (fall back to UNKNOWN = 100% if nothing detected)
        val counts: Map<String, Int> = if (labelCounts.isEmpty()) {
            mapOf("UNKNOWN" to maxOf(sampleCount, 1))
        } else {
            labelCounts
        }

        // Donut chart + legend
        buildDonut(view, counts)

        // Detection log rows
        buildDetectionLog(view, counts)

        // Export button
        val btnExport = view.findViewById<Button>(R.id.btnSumExportCsv)
        if (filePath.isNotEmpty()) {
            btnExport.isEnabled = true
            btnExport.alpha     = 1f
            btnExport.setOnClickListener { exportCsv() }
        }

        // Back to Live button
        view.findViewById<Button>(R.id.btnBackToLive).setOnClickListener { navigateBack() }
    }

    // ── Donut chart + legend ─────────────────────────────────────────────────

    private fun buildDonut(root: View, counts: Map<String, Int>) {
        val total = counts.values.sum().coerceAtLeast(1)

        // Sort by labelOrder, then by count descending for unknowns
        val sorted = counts.entries.sortedWith(
            compareBy { idx -> labelOrder.indexOf(idx.key).let { if (it == -1) 99 else it } }
        )

        // Feed slices to DonutChartView
        val slices = sorted.map { (label, count) ->
            DonutChartView.Slice(
                color    = labelColor[label] ?: Color.parseColor("#9090C0"),
                fraction = count.toFloat() / total
            )
        }
        root.findViewById<DonutChartView>(R.id.donutChart)
            .setSlices(slices, eventCount)

        // Build legend (right side of card)
        val legend = root.findViewById<LinearLayout>(R.id.legendContainer)
        legend.removeAllViews()

        sorted.forEach { (label, count) ->
            val pct   = (count * 100f / total).toInt()
            val color = labelColor[label] ?: Color.parseColor("#9090C0")

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (6 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }

            // Colour square
            val dot = View(requireContext()).apply {
                val size = (10 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = (6 * resources.displayMetrics.density).toInt()
                }
                background = requireContext().getDrawable(R.drawable.bg_tab_active)?.mutate()
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            }

            // Label name
            val tvLabel = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text         = label.lowercase().replaceFirstChar { it.uppercase() }
                textSize     = 11f
                setTextColor(Color.parseColor("#1A1A3E"))
            }

            // Percentage
            val tvPct = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "$pct%"
                textSize = 11f
                setTextColor(color)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val padPx = (12 * resources.displayMetrics.density).toInt()
                setPadding(padPx, 0, 0, 0)
            }

            row.addView(dot)
            row.addView(tvLabel)
            row.addView(tvPct)
            legend.addView(row)
        }
    }

    // ── Detection log rows ───────────────────────────────────────────────────

    private fun buildDetectionLog(root: View, counts: Map<String, Int>) {
        val container = root.findViewById<LinearLayout>(R.id.detectionLogContainer)
        container.removeAllViews()

        val maxCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val sorted   = counts.entries.sortedWith(
            compareBy { idx -> labelOrder.indexOf(idx.key).let { if (it == -1) 99 else it } }
        )
        val density = resources.displayMetrics.density

        sorted.forEachIndexed { index, (label, count) ->
            val color = labelColor[label] ?: Color.parseColor("#9090C0")

            // Row container
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin    = (8 * density).toInt()
                lp.bottomMargin = (8 * density).toInt()
                layoutParams = lp
            }

            // Colour dot + label
            val labelBox = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    (110 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val dot = View(requireContext()).apply {
                val size = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = (8 * density).toInt()
                }
                background = requireContext().getDrawable(R.drawable.bg_dot_axis)?.mutate()
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            }
            val tvLabel = TextView(requireContext()).apply {
                text     = label
                textSize = 12f
                setTextColor(Color.parseColor("#1A1A3E"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            labelBox.addView(dot)
            labelBox.addView(tvLabel)

            // Proportional bar
            val barBg = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, (5 * density).toInt(), 1f).also {
                    it.marginStart = (8 * density).toInt()
                    it.marginEnd   = (10 * density).toInt()
                }
                background = requireContext().getDrawable(R.drawable.bg_conf_bar_track)
            }
            val barFill = View(requireContext()).apply {
                val fraction = count.toFloat() / maxCount
                layoutParams = FrameLayout.LayoutParams(
                    (fraction * 1000).toInt(),   // placeholder; set in post
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = requireContext().getDrawable(R.drawable.bg_conf_bar_fill)?.mutate()
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                val frac = count.toFloat() / maxCount
                barBg.post {
                    val lp    = layoutParams
                    lp.width  = (barBg.width * frac).toInt()
                    layoutParams = lp
                }
            }
            barBg.addView(barFill)

            // Count label
            val tvCount = TextView(requireContext()).apply {
                text     = "${count}×"
                textSize = 12f
                setTextColor(Color.parseColor("#2A3FBF"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            row.addView(labelBox)
            row.addView(barBg)
            row.addView(tvCount)
            container.addView(row)

            // Divider (except after last item)
            if (index < sorted.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    )
                    setBackgroundColor(Color.parseColor("#F0F0FF"))
                }
                container.addView(divider)
            }
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private fun exportCsv() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val src = File(filePath)
                if (!src.exists()) {
                    toast("File not found."); return@launch
                }
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                downloads.mkdirs()
                src.copyTo(File(downloads, src.name), overwrite = true)
                toast("Saved to Downloads/${src.name}")
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
    }

    private suspend fun toast(msg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateBack() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fmtCount(n: Int) = if (n < 1000) "$n" else "%.1fk".format(n / 1000f)

    private fun todayDate(): String =
        SimpleDateFormat("MMM d", Locale.US).format(Date())

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "SummaryFragment"

        private const val ARG_SESSION_ID   = "sessionId"
        private const val ARG_DURATION     = "duration"
        private const val ARG_SAMPLES      = "sampleCount"
        private const val ARG_EVENTS       = "eventCount"
        private const val ARG_FILE_PATH    = "filePath"
        private const val ARG_LABEL_COUNTS = "labelCounts"

        fun newInstance(
            sessionId:   String,
            duration:    String,
            sampleCount: Int,
            eventCount:  Int,
            filePath:    String,
            labelCounts: HashMap<String, Int>
        ): SummaryFragment = SummaryFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SESSION_ID,    sessionId)
                putString(ARG_DURATION,      duration)
                putInt(ARG_SAMPLES,          sampleCount)
                putInt(ARG_EVENTS,           eventCount)
                putString(ARG_FILE_PATH,     filePath)
                putSerializable(ARG_LABEL_COUNTS, labelCounts)
            }
        }
    }
}