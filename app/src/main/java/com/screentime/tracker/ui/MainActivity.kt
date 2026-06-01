package com.screentime.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.tracker.R
import com.screentime.tracker.databinding.ActivityMainBinding
import com.screentime.tracker.service.UsageCollectorWorker
import com.screentime.tracker.utils.CsvExporter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: UsageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        if (!viewModel.repo.hasUsagePermission()) {
            showPermissionDialog()
        } else {
            UsageCollectorWorker.schedule(this)
            viewModel.refreshNow()
        }
    }

    private fun setupRecyclerView() {
        adapter = UsageAdapter { record ->
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_PACKAGE, record.packageName)
                putExtra(DetailActivity.EXTRA_APP_NAME, record.appName)
            }
            startActivity(intent)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupObservers() {
        viewModel.usageList.observe(this) { records ->
            adapter.submitList(records)
            binding.emptyView.visibility =
                if (records.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.totalMinutes.observe(this) { total ->
            binding.totalTimeText.text = "Total today: ${viewModel.formatMinutes(total)}"
        }

        viewModel.selectedDate.observe(this) { date ->
            binding.dateText.text = date
        }

        viewModel.availableDates.observe(this) { dates ->
            if (dates.isEmpty()) return@observe
            val dialogAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dates)
            AlertDialog.Builder(this)
                .setTitle("Select Date")
                .setAdapter(dialogAdapter) { _, which ->
                    viewModel.loadDate(dates[which])
                }
                .show()
        }
    }

    private fun setupListeners() {
        binding.fabRefresh.setOnClickListener {
            Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
            viewModel.refreshNow()
        }
        binding.btnToday.setOnClickListener { viewModel.loadToday() }
        binding.btnPickDate.setOnClickListener { viewModel.loadAvailableDates() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { exportCsv(); true }
            R.id.action_permission -> {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportCsv() {
        val records = viewModel.repo.getAllRecords()
        if (records.isEmpty()) {
            Toast.makeText(this, "No data to export yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = CsvExporter.export(this, records)
        if (intent != null) {
            startActivity(Intent.createChooser(intent, "Export Screen Time CSV"))
        } else {
            Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "Screen Time Tracker needs the 'Usage Access' permission to monitor which apps you use.\n\n" +
                "Tap OK to open Settings, then find 'Screen Time Tracker' and enable it."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.repo.hasUsagePermission()) {
            UsageCollectorWorker.schedule(this)
            viewModel.refreshNow()
        }
    }
}
