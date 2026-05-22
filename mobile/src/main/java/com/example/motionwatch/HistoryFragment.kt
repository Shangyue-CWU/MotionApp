package com.example.motionwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AbsListView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fragment that displays a list of recorded CSV data logs.
 * Supports filtering by source (Watch/Phone) and month.
 */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var listView: ListView
    private lateinit var tvHint: TextView
    private lateinit var btnFilter: ImageButton
    private lateinit var tvActiveFilters: TextView

    private lateinit var adapter: android.widget.ArrayAdapter<String>

    // Lists to manage the files and their display text
    private val files = mutableListOf<File>()
    private val displayNames = mutableListOf<String>()

    // Current filter state
    private var filterSource = "ALL" // "ALL", "WATCH", or "PHONE"
    private var filterMonth = -1      // -1 for all months, 0-11 for Jan-Dec

    /**
     * Receiver to refresh the list when a new file is received via Wearable Data Layer.
     */
    private val fileReceivedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadLogs()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listView = view.findViewById(R.id.list_logs)
        tvHint = view.findViewById(R.id.tv_hint)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvActiveFilters = view.findViewById(R.id.tv_active_filters)

        // Setup the list adapter
        adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.item_history_file,
            displayNames
        )
        listView.adapter = adapter
        listView.setItemsCanFocus(false)

        // Multi-selection setup for batch actions
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        listView.setMultiChoiceModeListener(historyMultiChoiceListener)

        // Click a file to show a preview dialog
        listView.setOnItemClickListener { _, _, position, _ ->
            if (listView.checkedItemCount == 0) {
                val f = files.getOrNull(position) ?: return@setOnItemClickListener
                showPreviewDialog(f)
            }
        }

        // Filter button click listener
        btnFilter.setOnClickListener {
            showFilterDialog()
        }

        loadLogs()
    }

    override fun onStart() {
        super.onStart()
        // Register receiver for new data notifications
        val filter = IntentFilter(FileReceiverService.ACTION_FILE_RECEIVED)
        ContextCompat.registerReceiver(
            requireContext(),
            fileReceivedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            requireContext().unregisterReceiver(fileReceivedReceiver)
        } catch (_: Exception) { }
    }

    /**
     * Loads logs from the app's log directories and applies the current filters.
     */
    private fun loadLogs() {
        files.clear()
        displayNames.clear()

        val base = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        // Collect all potential files
        val all = mutableListOf<File>()
        if (phoneDir.exists()) all += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) all += watchDir.listFiles()?.toList().orEmpty()

        // Filter by extension, source, and date
        val filtered = all.filter { file ->
            if (!file.isFile || !file.name.lowercase(Locale.US).endsWith(".csv")) return@filter false

            // 1. Source Filter
            val isWatch = file.absolutePath.contains("${File.separator}watch${File.separator}")
            if (filterSource == "WATCH" && !isWatch) return@filter false
            if (filterSource == "PHONE" && isWatch) return@filter false

            // 2. Month Filter
            if (filterMonth != -1) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = file.lastModified()
                if (cal.get(Calendar.MONTH) != filterMonth) return@filter false
            }

            true
        }.sortedByDescending { it.lastModified() }

        files.addAll(filtered)

        // Prepare display strings
        for (f in filtered) {
            val source = if (f.absolutePath.contains("${File.separator}watch${File.separator}")) "WATCH" else "PHONE"
            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
            displayNames.add("[$source] ${f.name}\n$dateStr • ${formatSize(f.length())}")
        }

        updateFilterLabel()
        adapter.notifyDataSetChanged()
        clearAllSelections()

        // Show empty message if no files match
        tvHint.text = if (files.isEmpty()) {
            if (filterSource != "ALL" || filterMonth != -1) "No logs match the current filters."
            else "No logs yet.\nStart recording data to see files here."
        } else {
            "Tap to preview • Long-press to select"
        }
    }

    /**
     * Updates the UI label showing what filters are currently active.
     */
    private fun updateFilterLabel() {
        val monthName = if (filterMonth == -1) "All Months" 
                        else SimpleDateFormat("MMMM", Locale.US).format(Calendar.getInstance().apply { set(Calendar.MONTH, filterMonth) }.time)
        val sourceName = when(filterSource) {
            "WATCH" -> "Watch Only"
            "PHONE" -> "Phone Only"
            else -> "All Sources"
        }
        tvActiveFilters.text = "Filtering: $sourceName • $monthName"
    }

    /**
     * Shows a dialog allowing the user to select source and month filters.
     */
    private fun showFilterDialog() {
        val months = arrayOf("All Months", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val sources = arrayOf("All Sources", "Watch Only", "Phone Only")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Filter Logs")

        // We'll use a custom view or sequential dialogs. For simplicity, let's use a two-step dialog approach
        builder.setItems(arrayOf("Select Source ($filterSource)", "Select Month")) { _, which ->
            if (which == 0) {
                // Source sub-selection
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Source")
                    .setItems(sources) { _, sWhich ->
                        filterSource = when(sWhich) {
                            1 -> "WATCH"
                            2 -> "PHONE"
                            else -> "ALL"
                        }
                        loadLogs()
                    }.show()
            } else {
                // Month sub-selection
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Month")
                    .setItems(months) { _, mWhich ->
                        filterMonth = mWhich - 1 // -1 is "All Months"
                        loadLogs()
                    }.show()
            }
        }
        builder.setNeutralButton("Reset All") { _, _ ->
            filterSource = "ALL"
            filterMonth = -1
            loadLogs()
        }
        builder.setPositiveButton("Close", null)
        builder.show()
    }

    // ---------------------------------------------------------
    // Multi-select Contextual Action Bar (CAB) logic
    // ---------------------------------------------------------
    private val historyMultiChoiceListener = object : AbsListView.MultiChoiceModeListener {

        override fun onCreateActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
            menu.add(0, MENU_SELECT_ALL, 0, "Select All")
            menu.add(0, MENU_CLEAR, 1, "Clear")
            menu.add(0, MENU_SHARE, 2, "Share")
            menu.add(0, MENU_DELETE, 3, "Delete")
            updateCabTitle(mode)
            return true
        }

        override fun onPrepareActionMode(mode: android.view.ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: android.view.ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                MENU_SELECT_ALL -> {
                    selectAll()
                    updateCabTitle(mode)
                    true
                }
                MENU_CLEAR -> {
                    clearAllSelections()
                    if (listView.checkedItemCount == 0) mode.finish()
                    true
                }
                MENU_SHARE -> {
                    val selected = getSelectedFiles()
                    if (selected.isNotEmpty()) shareCsvFiles(selected)
                    true
                }
                MENU_DELETE -> {
                    val selected = getSelectedFiles()
                    if (selected.isNotEmpty()) confirmDelete(selected) {
                        mode.finish()
                        loadLogs()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: android.view.ActionMode) {
            clearAllSelections()
        }

        override fun onItemCheckedStateChanged(mode: android.view.ActionMode, position: Int, id: Long, checked: Boolean) {
            updateCabTitle(mode)
        }

        private fun updateCabTitle(mode: android.view.ActionMode) {
            val n = listView.checkedItemCount
            mode.title = if (n > 0) "$n selected" else "Select files"
        }
    }

    private fun selectAll() {
        for (i in 0 until adapter.count) listView.setItemChecked(i, true)
    }

    private fun clearAllSelections() {
        for (i in 0 until adapter.count) listView.setItemChecked(i, false)
    }

    private fun getSelectedFiles(): List<File> {
        val selected = mutableListOf<File>()
        val checked = listView.checkedItemPositions
        for (i in 0 until checked.size()) {
            val position = checked.keyAt(i)
            if (checked.valueAt(i)) {
                files.getOrNull(position)?.let { selected.add(it) }
            }
        }
        return selected
    }

    /**
     * Shows a confirmation dialog before deleting selected files.
     */
    private fun confirmDelete(selected: List<File>, onDone: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete files")
            .setMessage("Delete ${selected.size} CSV file(s)? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                var deleted = 0
                selected.forEach { f ->
                    try { if (f.exists() && f.delete()) deleted++ } catch (_: Exception) { }
                }
                Toast.makeText(requireContext(), "Deleted $deleted file(s)", Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shows a dialog with the first few lines of a CSV file for quick verification.
     */
    private fun showPreviewDialog(file: File) {
        val text = readFirstLines(file, 50)
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setMessage(text)
            .setPositiveButton("OK", null)
            .setNeutralButton("Share") { _, _ -> shareCsvFiles(listOf(file)) }
            .show()
    }

    /**
     * Triggers the system share sheet for the selected CSV files.
     */
    private fun shareCsvFiles(filesToShare: List<File>) {
        try {
            val uris = ArrayList<Uri>()
            filesToShare.forEach { file ->
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
                uris.add(uri)
            }
            val share = Intent().apply {
                action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            startActivity(Intent.createChooser(share, "Share CSV"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Reads the start of a file to display in the preview dialog.
     */
    private fun readFirstLines(file: File, maxLines: Int): String {
        val sb = StringBuilder()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
                var count = 0
                while (count < maxLines) {
                    val line = br.readLine() ?: break
                    sb.append(line).append('\n')
                    count++
                }
                if (count == maxLines) sb.append("...\n")
            }
        } catch (e: Exception) { return "Failed to read: ${e.message}" }
        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    companion object {
        private const val MENU_SELECT_ALL = 1001
        private const val MENU_CLEAR = 1002
        private const val MENU_SHARE = 1003
        private const val MENU_DELETE = 1004
    }
}
