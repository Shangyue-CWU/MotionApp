package com.example.motionwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
 * HistoryPageFragment: Displays a list of recorded sensor data logs (CSV files).
 * This fragment supports:
 * - Viewing logs in a modern card-based layout.
 * - Filtering logs by source (Phone/Watch) and month.
 * - Previewing file content.
 * - Sharing files (which also allows downloading/saving).
 * - Renaming and deleting files.
 */
class HistoryPageFragment : Fragment(R.layout.fragment_history_page) {

    // UI Components
    private lateinit var listView: ListView
    private lateinit var tvHint: TextView
    private lateinit var btnFilter: ImageButton
    private lateinit var tvActiveFilters: TextView
    private lateinit var btnBack: ImageButton

    // Data Management
    private lateinit var adapter: HistoryCardAdapter
    private val files = mutableListOf<File>()

    // Filter State
    private var filterSource = "ALL" // Options: ALL, WATCH, PHONE
    private var filterMonth = -1      // -1 means all months, 0-11 for specific months

    /**
     * Receiver that triggers a list refresh whenever a new file is received (e.g., from the watch).
     */
    private val fileReceivedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadLogs()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize UI hooks
        listView = view.findViewById(R.id.list_logs)
        tvHint = view.findViewById(R.id.tv_hint)
        btnFilter = view.findViewById(R.id.btn_filter)
        tvActiveFilters = view.findViewById(R.id.tv_active_filters)
        btnBack = view.findViewById(R.id.btnBack)

        // Handle back navigation
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Setup the custom adapter for the card layout
        adapter = HistoryCardAdapter(requireContext(), files)
        listView.adapter = adapter
        
        // Enable multi-selection for batch actions (Share/Delete/etc)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
        listView.setMultiChoiceModeListener(historyMultiChoiceListener)

        // Single click logic: Open the preview dialog
        listView.setOnItemClickListener { _, _, position, _ ->
            if (listView.checkedItemCount == 0) {
                val f = files.getOrNull(position) ?: return@setOnItemClickListener
                showPreviewDialog(f)
            }
        }

        // Setup filter dialog trigger
        btnFilter.setOnClickListener {
            showFilterDialog()
        }

        // Initial load of logs
        loadLogs()
    }

    override fun onStart() {
        super.onStart()
        // Register the file update receiver based on Android version requirements
        val filter = IntentFilter(FileReceiverService.ACTION_FILE_RECEIVED)
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(fileReceivedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            requireContext().registerReceiver(fileReceivedReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unregister to prevent memory leaks
        try {
            requireContext().unregisterReceiver(fileReceivedReceiver)
        } catch (_: Exception) { }
    }

    /**
     * loadLogs: Scans both Phone and Watch log directories.
     * Filters files based on the user's selection and sorts them by most recent.
     */
    private fun loadLogs() {
        files.clear()
        val base = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
        val phoneDir = File(base, "logs")
        val watchDir = File(base, "logs/watch")

        // Collect files from both sources
        val all = mutableListOf<File>()
        if (phoneDir.exists()) all += phoneDir.listFiles()?.toList().orEmpty()
        if (watchDir.exists()) all += watchDir.listFiles()?.toList().orEmpty()

        // Apply filters (Extension, Source, Month)
        val filtered = all.filter { file ->
            if (!file.isFile || !file.name.lowercase(Locale.US).endsWith(".csv")) return@filter false
            val isWatch = file.absolutePath.contains("${File.separator}watch${File.separator}")
            if (filterSource == "WATCH" && !isWatch) return@filter false
            if (filterSource == "PHONE" && isWatch) return@filter false
            if (filterMonth != -1) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = file.lastModified()
                if (cal.get(Calendar.MONTH) != filterMonth) return@filter false
            }
            true
        }.sortedByDescending { it.lastModified() }

        files.addAll(filtered)
        updateFilterLabel()
        adapter.notifyDataSetChanged()
        
        // Update helper text based on results
        tvHint.text = if (files.isEmpty()) {
            if (filterSource != "ALL" || filterMonth != -1) "No logs match the current filters."
            else "No logs yet."
        } else {
            "Tap to preview • Long-press to select"
        }
    }

    /**
     * updateFilterLabel: Dynamically updates the subtitle text to show active filters.
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
     * showFilterDialog: Displays a dialog to choose Source or Month filters.
     */
    private fun showFilterDialog() {
        val months = arrayOf("All Months", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val sources = arrayOf("All Sources", "Watch Only", "Phone Only")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Filter Logs")
        builder.setItems(arrayOf("Select Source ($filterSource)", "Select Month")) { _, which ->
            if (which == 0) {
                AlertDialog.Builder(requireContext()).setTitle("Select Source").setItems(sources) { _, sWhich ->
                    filterSource = when(sWhich) { 1 -> "WATCH"; 2 -> "PHONE"; else -> "ALL" }
                    loadLogs()
                }.show()
            } else {
                AlertDialog.Builder(requireContext()).setTitle("Select Month").setItems(months) { _, mWhich ->
                    filterMonth = mWhich - 1
                    loadLogs()
                }.show()
            }
        }
        builder.setNeutralButton("Reset All") { _, _ -> filterSource = "ALL"; filterMonth = -1; loadLogs() }
        builder.setPositiveButton("Close", null)
        builder.show()
    }

    /**
     * MultiChoiceModeListener: Handles the Contextual Action Bar (CAB) when multiple items are selected.
     * Supports batch select-all, share, rename, and delete.
     */
    private val historyMultiChoiceListener = object : AbsListView.MultiChoiceModeListener {
        override fun onCreateActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
            menu.add(0, MENU_SELECT_ALL, 0, "Select All")
            menu.add(0, MENU_CLEAR, 1, "Clear")
            menu.add(0, MENU_SHARE, 2, "Share")
            menu.add(0, MENU_RENAME, 3, "Rename")
            menu.add(0, MENU_DELETE, 4, "Delete")
            updateCabTitle(mode)
            return true
        }

        override fun onPrepareActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
            val renameItem = menu.findItem(MENU_RENAME)
            renameItem?.isVisible = listView.checkedItemCount == 1 // Only allow rename for single selection
            return true
        }

        override fun onActionItemClicked(mode: android.view.ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                MENU_SELECT_ALL -> { for (i in 0 until adapter.count) listView.setItemChecked(i, true); updateCabTitle(mode); true }
                MENU_CLEAR -> { for (i in 0 until adapter.count) listView.setItemChecked(i, false); if (listView.checkedItemCount == 0) mode.finish(); true }
                MENU_SHARE -> { val selected = getSelectedFiles(); if (selected.isNotEmpty()) shareCsvFiles(selected); true }
                MENU_RENAME -> {
                    val selected = getSelectedFiles()
                    if (selected.size == 1) {
                        showRenameDialog(selected[0]) { mode.finish(); loadLogs() }
                    }
                    true
                }
                MENU_DELETE -> {
                    val selected = getSelectedFiles()
                    if (selected.isNotEmpty()) confirmDelete(selected) { mode.finish(); loadLogs() }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        override fun onItemCheckedStateChanged(mode: android.view.ActionMode, position: Int, id: Long, checked: Boolean) {
            mode.invalidate()
            updateCabTitle(mode)
        }

        private fun updateCabTitle(mode: android.view.ActionMode) {
            val n = listView.checkedItemCount
            mode.title = if (n > 0) "$n selected" else "Select files"
        }
    }

    /**
     * Helper to retrieve currently checked files from the ListView.
     */
    private fun getSelectedFiles(): List<File> {
        val selected = mutableListOf<File>()
        val checked = listView.checkedItemPositions
        for (i in 0 until checked.size()) {
            val position = checked.keyAt(i)
            if (checked.valueAt(i)) { files.getOrNull(position)?.let { selected.add(it) } }
        }
        return selected
    }

    /**
     * showRenameDialog: Displays an input field to rename a specific file.
     */
    private fun showRenameDialog(file: File, onDone: () -> Unit) {
        val input = EditText(requireContext())
        input.setText(file.name)
        input.setSelectAllOnFocus(true)

        AlertDialog.Builder(requireContext())
            .setTitle("Rename File")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    val newFile = File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        Toast.makeText(requireContext(), "Renamed to $newName", Toast.LENGTH_SHORT).show()
                        onDone()
                    } else {
                        Toast.makeText(requireContext(), "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * confirmDelete: Standard confirmation dialog before permanent file deletion.
     */
    private fun confirmDelete(selected: List<File>, onDone: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete files")
            .setMessage("Delete ${selected.size} CSV file(s)?")
            .setPositiveButton("Delete") { _, _ ->
                selected.forEach { f -> try { f.delete() } catch (_: Exception) { } }
                onDone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * showPreviewDialog: Opens a dialog showing the first few lines of the CSV.
     * Includes direct "Share" and "Delete" actions for ease of use.
     */
    private fun showPreviewDialog(file: File) {
        val text = readFirstLines(file, 50)
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setMessage(text)
            .setPositiveButton("Share") { _, _ -> shareCsvFiles(listOf(file)) } // Share also serves as Download
            .setNegativeButton("Delete") { _, _ -> confirmDelete(listOf(file)) { loadLogs() } }
            .setNeutralButton("Cancel", null)
            .show()
    }

    /**
     * shareCsvFiles: Uses Android's Intent.ACTION_SEND to share CSV files via FileProvider.
     * This allows users to email files, upload to cloud storage, or "Download" to their local device.
     */
    private fun shareCsvFiles(filesToShare: List<File>) {
        try {
            val uris = ArrayList<Uri>()
            filesToShare.forEach { file ->
                // Generate secure URIs using FileProvider
                uris.add(FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file))
            }
            val share = Intent().apply {
                action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            startActivity(Intent.createChooser(share, "Share CSV"))
        } catch (e: Exception) { Toast.makeText(requireContext(), "Share failed", Toast.LENGTH_SHORT).show() }
    }

    /**
     * readFirstLines: Utility to read the beginning of a file without loading the entire thing into memory.
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
            }
        } catch (e: Exception) { return "Error reading file" }
        return sb.toString()
    }

    /**
     * HistoryCardAdapter: Binds file metadata to the item_history_card layout.
     * Handles source badge styling and activity label extraction.
     */
    private inner class HistoryCardAdapter(context: Context, private val data: List<File>) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = data.size
        override fun getItem(position: Int): File = data[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_history_card, parent, false)
            val file = getItem(position)

            val tvDate: TextView = view.findViewById(R.id.tv_card_date)
            val tvTime: TextView = view.findViewById(R.id.tv_card_time)
            val tvSource: TextView = view.findViewById(R.id.tv_card_source)
            val tvLabel: TextView = view.findViewById(R.id.tv_card_label)
            val ivIcon: ImageView = view.findViewById(R.id.iv_history_icon)

            val isWatch = file.absolutePath.contains("${File.separator}watch${File.separator}")
            
            // Format Date and Time using file metadata
            tvDate.text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(file.lastModified()))
            tvTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(file.lastModified()))

            // Dynamic Source Badge Styling
            if (isWatch) {
                tvSource.text = "WATCH"
                tvSource.setBackgroundResource(R.drawable.bg_badge_watch)
            } else {
                tvSource.text = "PHONE"
                tvSource.setBackgroundResource(R.drawable.bg_badge_phone)
            }

            // Extract activity label from filename (e.g., RUN, WALK, etc.)
            val label = extractLabel(file.name)
            tvLabel.text = label.uppercase(Locale.getDefault())

            // Color the history clock icon based on the app's primary theme color
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))

            return view
        }

        /**
         * extractLabel: Parses the standardized filename to find the activity type.
         * Expected format: SESSION_{ID}_{SOURCE}_{LABEL}_{TIMESTAMP}.csv
         */
        private fun extractLabel(filename: String): String {
            val parts = filename.split("_")
            return if (parts.size >= 5) parts[3] else "ACTIVITY"
        }
    }

    companion object {
        // Unique IDs for contextual menu items
        private const val MENU_SELECT_ALL = 1001
        private const val MENU_CLEAR = 1002
        private const val MENU_SHARE = 1003
        private const val MENU_DELETE = 1004
        private const val MENU_RENAME = 1005
    }
}
