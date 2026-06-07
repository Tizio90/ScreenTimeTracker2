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
import androidx.appcompat.app.AppCompatDelegate
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
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adapter = UsageAdapter(viewModel.repo) { record ->
            startActivity(Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_PACKAGE, record.packageName)
                putExtra(DetailActivity.EXTRA_APP_NAME, record.appName)
            })
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.isNestedScrollingEnabled = false

        viewModel.dailyList.observe(this) { records ->
            adapter.submitList(records)
            binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.totalMinutes.observe(this) { total ->
            val h = total / 60; val m = total % 60
            binding.totalTimeText.text = if (h > 0) "${h}h ${m}m" else "${m}m"
        }
        viewModel.selectedDate.observe(this) { date ->
            binding.dateText.text = date
        }
        viewModel.peakHour.observe(this) { hour ->
            val dayStart = viewModel.repo.prefs.dayStartHour
            val peakStr = if (hour != null) "  ·  Peak ${viewModel.repo.formatHour(hour)}" else ""
            binding.peakHourText.text = "Starts ${viewModel.repo.formatHour(dayStart)}$peakStr"
        }
        viewModel.availableDates.observe(this) { dates ->
            if (dates.isEmpty()) {
                Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show()
                return@observe
            }
            AlertDialog.Builder(this)
                .setTitle("Select Date")
                .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, dates)) { _, which ->
                    viewModel.loadDate(dates[which])
                }.show()
        }

        binding.fabRefresh.setOnClickListener {
            if (!viewModel.repo.hasUsagePermission()) showPermissionDialog()
            else { viewModel.refreshNow() }
        }
        binding.btnToday.setOnClickListener { viewModel.loadToday() }
        binding.btnPickDate.setOnClickListener { viewModel.loadAvailableDates() }
        binding.btnHourly.setOnClickListener {
            startActivity(Intent(this, HourlyActivity::class.java).apply {
                putExtra(HourlyActivity.EXTRA_DATE, viewModel.selectedDate.value ?: viewModel.repo.getTodayDate())
            })
        }
        binding.btnWeekly.setOnClickListener {
            startActivity(Intent(this, SummaryActivity::class.java).apply {
                putExtra(SummaryActivity.EXTRA_MODE, SummaryActivity.MODE_WEEKLY)
            })
        }
        binding.btnMonthly.setOnClickListener {
            startActivity(Intent(this, SummaryActivity::class.java).apply {
                putExtra(SummaryActivity.EXTRA_MODE, SummaryActivity.MODE_MONTHLY)
            })
        }

        if (!viewModel.repo.hasUsagePermission()) showPermissionDialog()
        else { UsageCollectorWorker.schedule(this); viewModel.refreshNow() }
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
                val sessions = viewModel.repo.getAllSessions()
                if (sessions.isEmpty()) Toast.makeText(this, "No data to export yet", Toast.LENGTH_SHORT).show()
                else {
                    val intent = CsvExporter.export(this, sessions, viewModel.repo)
                    if (intent != null) startActivity(Intent.createChooser(intent, "Export CSV"))
                    else Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_day_start -> { showDayStartPicker(); true }
            R.id.action_dark_mode -> {
                val next = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
                    AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
                AppCompatDelegate.setDefaultNightMode(next); true
            }
            R.id.action_categories -> { startActivity(Intent(this, CategoriesActivity::class.java)); true }
            R.id.action_permission -> { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDayStartPicker() {
        val currentHour = viewModel.repo.prefs.dayStartHour
        val hours = (0..23).map { h ->
            val label = viewModel.repo.formatHour(h)
            if (h == 0) "$label (midnight — default)" else label
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Day Starts At...")
            .setSingleChoiceItems(hours, currentHour) { dialog, which ->
                viewModel.repo.prefs.dayStartHour = which
                viewModel.refreshNow()
                Toast.makeText(this, "Day now starts at ${viewModel.repo.formatHour(which)}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs Usage Access permission.\n\nTap OK → find 'Screen Time Tracker' → enable it.")
            .setPositiveButton("Open Settings") { _, _ -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .setNegativeButton("Cancel", null).show()
    }
}
