package com.screentime.tracker.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.tracker.data.DatabaseHelper
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ActivityDetailBinding

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var binding: ActivityDetailBinding
    private lateinit var repo: UsageRepository
    private lateinit var packageName: String
    private lateinit var originalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        originalName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        repo = UsageRepository(this)

        val meta = repo.getAppMeta(packageName)
        val displayName = meta.customName?.takeIf { it.isNotBlank() } ?: originalName

        supportActionBar?.apply { title = displayName; setDisplayHomeAsUpEnabled(true) }

        val records = repo.getUsageForPackage(packageName)
        val totalMinutes = records.sumOf { it.totalMinutes }
        val avgMinutes = if (records.isNotEmpty()) totalMinutes / records.size else 0L

        binding.summaryText.text =
            "📦 Package: $packageName\n" +
            "📁 Category: ${meta.category}\n" +
            "📅 Days tracked: ${records.size}\n" +
            "⏱ Total: ${repo.formatMinutes(totalMinutes)}\n" +
            "📊 Daily avg: ${repo.formatMinutes(avgMinutes)}"

        // Rename button
        binding.btnRename.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                setText(meta.customName ?: originalName)
                hint = "Custom display name"
            }
            AlertDialog.Builder(this)
                .setTitle("Rename App")
                .setMessage("This name will be used everywhere including CSV exports.")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString().trim()
                    repo.saveAppMeta(packageName, newName.ifEmpty { null }, meta.category)
                    supportActionBar?.title = newName.ifEmpty { originalName }
                    Toast.makeText(this, "Name saved!", Toast.LENGTH_SHORT).show()
                    refreshSummary()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Category button
        binding.btnCategory.setOnClickListener {
            val categories = DatabaseHelper.CATEGORIES.toTypedArray()
            val current = categories.indexOf(meta.category).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Set Category")
                .setSingleChoiceItems(categories, current) { dialog, which ->
                    val currentMeta = repo.getAppMeta(packageName)
                    repo.saveAppMeta(packageName, currentMeta.customName, categories[which])
                    Toast.makeText(this, "Category saved!", Toast.LENGTH_SHORT).show()
                    refreshSummary()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = HistoryAdapter(records, repo)
    }

    private fun refreshSummary() {
        val meta = repo.getAppMeta(packageName)
        val records = repo.getUsageForPackage(packageName)
        val totalMinutes = records.sumOf { it.totalMinutes }
        val avgMinutes = if (records.isNotEmpty()) totalMinutes / records.size else 0L
        binding.summaryText.text =
            "📦 Package: $packageName\n" +
            "📁 Category: ${meta.category}\n" +
            "📅 Days tracked: ${records.size}\n" +
            "⏱ Total: ${repo.formatMinutes(totalMinutes)}\n" +
            "📊 Daily avg: ${repo.formatMinutes(avgMinutes)}"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
