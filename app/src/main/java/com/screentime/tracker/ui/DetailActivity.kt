package com.screentime.tracker.ui

import android.os.Bundle
import android.view.MenuItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = appName
            setDisplayHomeAsUpEnabled(true)
        }

        val repo = UsageRepository(this)
        val records = repo.getUsageForPackage(packageName)

        // Build summary text
        val totalMinutes = records.sumOf { it.totalMinutes }
        val avgMinutes = if (records.isNotEmpty()) totalMinutes / records.size else 0L

        binding.summaryText.text = buildString {
            appendLine("📅 Days tracked: ${records.size}")
            appendLine("⏱ Total time: ${repo.formatMinutes(totalMinutes)}")
            append("📊 Daily average: ${repo.formatMinutes(avgMinutes)}")
        }

        // Simple list of date → time
        val adapter = HistoryAdapter(records, repo)
        binding.historyRecycler.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            this.adapter = adapter
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
