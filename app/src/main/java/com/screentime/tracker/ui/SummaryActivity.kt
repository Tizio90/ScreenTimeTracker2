package com.screentime.tracker.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ActivitySummaryBinding

class SummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_WEEKLY = "weekly"
        const val MODE_MONTHLY = "monthly"
    }

    private lateinit var binding: ActivitySummaryBinding
    private lateinit var repo: UsageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = UsageRepository(this)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_WEEKLY
        val isWeekly = mode == MODE_WEEKLY

        supportActionBar?.apply {
            title = if (isWeekly) "Weekly Summary" else "Monthly Summary"
            setDisplayHomeAsUpEnabled(true)
        }

        val data = if (isWeekly) repo.getWeeklyData() else repo.getMonthlyData()

        if (data.isEmpty()) {
            binding.statsSummary.text = "No data yet. Use the app for a few days first."
            return
        }

        setupBarChart(data, isWeekly)
        setupPieChart(data)
        setupStats(data, isWeekly)
    }

    private fun setupBarChart(data: List<Pair<String, Long>>, isWeekly: Boolean) {
        val entries = data.mapIndexed { i, (_, minutes) -> BarEntry(i.toFloat(), minutes.toFloat()) }
        val labels = data.map { (date, _) -> date.substring(5) }

        val dataSet = BarDataSet(entries, "Minutes").apply {
            color = Color.parseColor("#1976D2")
            valueTextColor = Color.BLACK
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val h = value.toLong() / 60; val m = value.toLong() % 60
                    return if (h > 0) "${h}h" else "${m}m"
                }
            }
        }

        binding.chartBar.apply {
            this.data = BarData(dataSet).apply { barWidth = 0.6f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = if (isWeekly) 0f else -45f
                textSize = if (isWeekly) 12f else 9f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val h = value.toLong() / 60
                        return if (h > 0) "${h}h" else "${value.toInt()}m"
                    }
                }
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            animateY(600)
            invalidate()
        }
    }

    private fun setupPieChart(data: List<Pair<String, Long>>) {
        val categoryTotals = mutableMapOf<String, Long>()
        for ((date, _) in data) {
            val cats = repo.getCategoryTotalsForDate(date)
            for ((cat, mins) in cats) {
                categoryTotals[cat] = (categoryTotals[cat] ?: 0L) + mins
            }
        }

        val pieColors = listOf(
            Color.parseColor("#1976D2"), Color.parseColor("#E53935"),
            Color.parseColor("#43A047"), Color.parseColor("#FB8C00"),
            Color.parseColor("#8E24AA"), Color.parseColor("#00ACC1"),
            Color.parseColor("#F4511E"), Color.parseColor("#6D4C41"),
            Color.parseColor("#546E7A")
        )

        val pieEntries = categoryTotals.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { (cat, mins) -> PieEntry(mins.toFloat(), cat) }

        if (pieEntries.isEmpty()) return

        val pieDataSet = PieDataSet(pieEntries, "").apply {
            colors = pieColors
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }

        binding.chartPie.apply {
            this.data = PieData(pieDataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 38f
            setHoleColor(Color.WHITE)
            setCenterText("By Category")
            setCenterTextSize(12f)
            legend.isEnabled = true
            animateY(800)
            invalidate()
        }
    }

    private fun setupStats(data: List<Pair<String, Long>>, isWeekly: Boolean) {
        val totalMins = data.sumOf { it.second }
        val avgMins = if (data.isNotEmpty()) totalMins / data.size else 0L
        val maxDay = data.maxByOrNull { it.second }
        val minDay = data.filter { it.second > 0 }.minByOrNull { it.second }

        binding.statsSummary.text = buildString {
            appendLine("📊 ${if (isWeekly) "Last 7 days" else "Last 30 days"}")
            appendLine("⏱ Total screen time: ${repo.formatMinutes(totalMins)}")
            appendLine("📈 Daily average: ${repo.formatMinutes(avgMins)}")
            maxDay?.let { appendLine("🔺 Busiest day: ${it.first}  (${repo.formatMinutes(it.second)})") }
            minDay?.let { append("🔻 Lightest day: ${it.first}  (${repo.formatMinutes(it.second)})") }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
