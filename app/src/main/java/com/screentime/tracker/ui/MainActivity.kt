package com.screentime.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
        supportActionBar?.title = "Screen Time Tracker"

        adapter = UsageAdapter { record ->
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_PACKAGE, record.packageName)
                putExtra(DetailActivity.EXTRA_APP_NAME, record.appName)
            })
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.usageList.observe(this) { records ->
            adapter.submitList(records)
            binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.totalMinutes.observe(this) { total ->
            binding.totalTimeText.text = "Total today: ${viewModel.formatMinutes(total)}"
        }
        viewModel.selectedDate.observe(this) { date ->
            binding.dateText.text = date
        }
        viewModel.availableDates.observe(this) { dates ->
            if (dates.isEmpty()) {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
                return@observe
            }
            val arr = ArrayAdapter(this, android.R.layout.simple_list_item_1, dates)
            AlertDialog.Builder(this)
                .setTitle("Select Date")
                .setAdapter(arr) { _, which -> viewModel.loadDate(dates[which]) }
                .show()
        }

        binding.fabRefresh.setOnClickListener {
            if (!viewModel.repo.hasUsagePermission()) {
                showPermissionDialog()
            } else {
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
                viewModel.refreshNow()
            }
        }
        binding.btnToday.setOnClickListener { viewModel.loadToday() }
        binding.btnPickDate.setOnClickListener { viewModel.loadAvailableDates() }

        if (!viewModel.repo.hasUsagePermission()) {
            showPermissionDialog()
        } else {
            UsageCollectorWorker.schedule(this)
            viewModel.refreshNow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.repo.hasUsagePermission()) {
            UsageCollectorWorker.schedule(this)
            viewModel.refreshNow()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                val records = viewModel.repo.getAllRecords()
                if (records.isEmpty()) {
                    Toast.makeText(this, "No data to export yet", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = CsvExporter.export(this, records)
                    if (intent != null) startActivity(Intent.createChooser(intent, "Export CSV"))
                    else Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_permission -> {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs Usage Access permission to track screen time.\n\nTap OK to open Settings, find 'Screen Time Tracker' and enable it.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
