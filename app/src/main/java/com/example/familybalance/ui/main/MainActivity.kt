package com.example.familybalance.ui.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.familybalance.R
import com.example.familybalance.data.models.Entry
import com.example.familybalance.data.repository.FirebaseRepository
import com.example.familybalance.ui.adapter.EntryAdapter
import com.example.familybalance.utils.PreferencesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val LOAD_ENTRY: Int = 10

    private lateinit var repository: FirebaseRepository
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: EntryAdapter

    private lateinit var btnViewMore: Button
    private lateinit var tvEntryCounter: TextView
    private lateinit var rvEntries: RecyclerView
    private lateinit var tvBalance: TextView
    private lateinit var fabAdd: MaterialButton

    private var currentDeviceRole: Int = PreferencesManager.ROLE_MOM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager(this)
        currentDeviceRole = prefs.getRole()

        if (currentDeviceRole == PreferencesManager.ROLE_MOM) {
            setTheme(R.style.Theme_FamilyBalance_Mom)
        } else {
            setTheme(R.style.Theme_FamilyBalance)
        }

        setContentView(R.layout.activity_main)

        // Initialize Tools and Identity
        repository = FirebaseRepository()

        // Bind Layout Views
        btnViewMore = findViewById(R.id.btn_view_more)
        tvEntryCounter = findViewById(R.id.tv_entry_counter)
        rvEntries = findViewById(R.id.rv_entries)
        tvBalance = findViewById(R.id.tv_balance)
        fabAdd = findViewById(R.id.fab_add)

        // Set up the Double-Underline Accounting Style Background
        tvBalance.setBackgroundResource(R.drawable.double_underline)

        // Configure RecyclerView
        adapter = EntryAdapter(currentDeviceRole) { clickedEntry ->
            showEntryDialog(clickedEntry) // Handle detail viewing, editing, or deleting
        }

        val layoutManager = LinearLayoutManager(this)
        // Stacks items starting at the bottom to mirror chat systems smoothly
        layoutManager.stackFromEnd = true
        rvEntries.layoutManager = layoutManager
        rvEntries.adapter = adapter

        // Connect Functional Triggers
        btnViewMore.setOnClickListener { loadOlderPage() }
        fabAdd.setOnClickListener { showEntryDialog(null) }

        // Execute Realtime Synchronization Engines
        loadInitialPage()
        startBalanceTracking()
    }

    /**
     * Pulls the first 5 entries on initialization
     */
    private fun loadInitialPage() {
        repository.fetchEntriesPage(null, LOAD_ENTRY, object : FirebaseRepository.DataCallback<List<Entry>> {
            override fun onSuccess(result: List<Entry>) {
                adapter.setEntries(result)
                // Instantly smooth-scroll to the bottom row to see latest entries
                if (result.isNotEmpty()) {
                    rvEntries.smoothScrollToPosition(result.size - 1)
                    updateEntryCounterText()
                }
            }
            override fun onError(error: com.google.firebase.database.DatabaseError) {
                Toast.makeText(this@MainActivity, "${getString(R.string.main_err)}: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateEntryCounterText() {
        val currentlyLoaded = adapter.itemCount

        repository.getTotalEntryCount { totalAvailable ->
            runOnUiThread {
                tvEntryCounter.text = "$currentlyLoaded / $totalAvailable"
            }
        }
    }

    /**
     * Pagination: Fetches the next 5 historical entries up the list
     */
    private fun loadOlderPage() {
        val oldestId = adapter.getOldestLoadedId()
        if (oldestId == null || oldestId <= 1) {
            Toast.makeText(this, getString(R.string.msg_oldest_data), Toast.LENGTH_SHORT).show()
            return
        }

        repository.fetchEntriesPage(oldestId, LOAD_ENTRY, object : FirebaseRepository.DataCallback<List<Entry>> {
            override fun onSuccess(result: List<Entry>) {
                if (result.isEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.msg_no_older_data), Toast.LENGTH_SHORT).show()
                } else {
                    adapter.appendOlderEntries(result)
                    updateEntryCounterText()
                }
            }
            override fun onError(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    /**
     * Calculates and updates the bottom Balance banner style instantly on change
     */
    @SuppressLint("DefaultLocale")
    private fun startBalanceTracking() {
        repository.listenToBalance { netBalance ->
            val formatted = "$ ${String.format("%.1f", netBalance)}"
            tvBalance.text = formatted

            if (netBalance >= 0) {
                // Surplus budget left over (Mom has credit background priority)
                tvBalance.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                // Daughter spent more money than given
                tvBalance.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }
    }

    /**
     * DYNAMIC DIALOG MANAGER
     * Handles adding new records (entry == null) or editing existing ones (entry != null).
     */
    private fun showEntryDialog(entry: Entry?) {
        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_entry, null)
        builder.setView(view)

        // Map Dialog layout elements
        val tvHeader = view.findViewById<TextView>(R.id.dialog_header)
        val tilTitle = view.findViewById<TextInputLayout>(R.id.til_title)
        val etTitle = view.findViewById<TextInputEditText>(R.id.et_title)
        val etDate = view.findViewById<TextInputEditText>(R.id.et_date)
        val tilDetail = view.findViewById<TextInputLayout>(R.id.til_detail)
        val etDetail = view.findViewById<TextInputEditText>(R.id.et_detail)
        val etAmount = view.findViewById<TextInputEditText>(R.id.et_amount)

        val btnDelete = view.findViewById<Button>(R.id.btn_dialog_delete)
        val btnCancel = view.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnSave = view.findViewById<Button>(R.id.btn_dialog_save)

        val dialog = builder.create()

        // Establish operational context states
        val isNew = entry == null
        val targetEntry = entry ?: Entry()

        // Define layout visual behavior depending on user identity
        if (currentDeviceRole == PreferencesManager.ROLE_MOM) {
            etTitle.setText(getString(R.string.add_reload))
            tilTitle.isEnabled = false // Mom's transaction text is restricted
            tilDetail.visibility = View.GONE // Details not accessible for Mom
        }

        // Set default date fallback context to Current Calendar Day
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (isNew) {
            tvHeader.text = getString(R.string.add_title)
            etDate.setText(todayStr)
            btnDelete.visibility = View.GONE
        } else {
            tvHeader.text = getString(R.string.main_view_record)
            etTitle.setText(targetEntry.title)
            etDate.setText(targetEntry.date)
            etDetail.setText(targetEntry.detail)
            etAmount.setText(targetEntry.amount.toString())

            // EVALUATE PERMISSION BOUNDARIES: Read access vs Write Access
            if (targetEntry.role == currentDeviceRole) {
                // You created it: access granted to alter or delete
                btnDelete.visibility = View.VISIBLE
                btnSave.visibility = View.VISIBLE
                etTitle.isEnabled = (currentDeviceRole != PreferencesManager.ROLE_MOM)
                etDate.isEnabled = true
                etDetail.isEnabled = true
                etAmount.isEnabled = true
            } else {
                // Cross-party reading constraint: Freeze operations
                btnDelete.visibility = View.GONE
                btnSave.visibility = View.GONE

                // Disable inputs
                etTitle.isEnabled = false
                etDate.isEnabled = false
                etDetail.isEnabled = false
                etAmount.isEnabled = false

                // --- FIX: Force fields to display crisp black text over white backgrounds ---
                val blackColor = ContextCompat.getColor(this, android.R.color.black)
                val whiteColor = ContextCompat.getColor(this, android.R.color.white)

                // Force clear black font visibility
                etTitle.setTextColor(blackColor)
                etDate.setTextColor(blackColor)
                etDetail.setTextColor(blackColor)
                etAmount.setTextColor(blackColor)

                // Clear background tints back to solid clean white
                etTitle.setBackgroundColor(whiteColor)
                etDate.setBackgroundColor(whiteColor)
                etDetail.setBackgroundColor(whiteColor)
                etAmount.setBackgroundColor(whiteColor)
            }
        }

        // --- BUTTON ACTIONS ---

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            // Launch a quick intermediate confirmation block
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.del_title))
                .setMessage(getString(R.string.del_body))
                .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                    // Execute the repository deletion if they confirm
                    repository.deleteEntry(targetEntry.id,
                        callback = {
                        Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show()
                        loadInitialPage() // Re-sync the view layout
                        dialog.dismiss()  // Close the main entry dialog
                },
            onError = { errorMessage ->
                // If it fails, this popup will tell us exactly what Firebase said
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_err_db))
                    .setMessage(errorMessage)
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show()
            }
            )
        }
                .setNegativeButton(getString(R.string.btn_cancel), null) // Do nothing on cancel
                .show()
        }

        btnSave.setOnClickListener {
            val titleInput = etTitle.text.toString().trim()
            val dateInput = etDate.text.toString().trim()
            val detailInput = etDetail.text.toString().trim()
            val amountInput = etAmount.text.toString().trim()

            // Strict Validation checks
            if (titleInput.isEmpty() || amountInput.isEmpty() || dateInput.isEmpty()) {
                Toast.makeText(this, getString(R.string.main_err_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val parsedAmount = amountInput.toDoubleOrNull()
            if (parsedAmount == null || parsedAmount <= 0) {
                Toast.makeText(this, getString(R.string.main_err_positive), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Update local object instances
            targetEntry.title = titleInput
            targetEntry.date = dateInput
            targetEntry.detail = if (currentDeviceRole == PreferencesManager.ROLE_MOM) "" else detailInput
            targetEntry.amount = parsedAmount
            if (isNew) {
                targetEntry.role = currentDeviceRole
            }

            repository.saveEntry(targetEntry, isNew, label = getString(R.string.main_summary)) {
                Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
                loadInitialPage() // Refresh list elements
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}