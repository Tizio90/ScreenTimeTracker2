package com.screentime.tracker.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        refreshUI()

        binding.btnRename.setOnClickListener {
            val meta = repo.getAppMeta(packageName)
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
                    refreshUI()
                    Toast.makeText(this, "Name saved!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null).show()
        }

        binding.btnCategory.setOnClickListener {
            val categories = repo.getAllCategories().toTypedArray()
            val meta = repo.getAppMeta(packageName)
            val current = categories.indexOf(meta.category).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Set Category")
                .setSingleChoiceItems(categories, current) { dialog, which ->
                    repo.saveAppMeta(packageName, repo.getAppMeta(packageName).customName, categories[which])
                    refreshUI()
                    Toast.makeText(this, "Category saved!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null).show()
        }

        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = HistoryAdapter(repo.getUsageForPackage(packageName), repo)
    }

    private fun refreshUI() {
        val meta = repo.getAppMeta(packageName)
        val displayName = meta.customName?.takeIf { it.isNotBlank() } ?: originalName
        supportActionBar?.title = displayName

        val records = repo.getUsageForPackage(packageName)
        val totalMinutes = records.sumOf { it.totalMinutes }
        val avgMinutes = if (records.isNotEmpty()) totalMinutes / records.size else 0L

        binding.summaryText.text =
            "📦 $packageName\n" +
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
