package com.screentime.tracker.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.screentime.tracker.R
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = if (isWeekly) "Weekly Summary" else "Monthly Summary"
            setDisplayHomeAsUpEnabled(true)
        }

        val data = if (isWeekly) repo.getWeeklyData() else repo.getMonthlyData()

        if (data.isEmpty()) {
            binding.statsSummary.text = "No data yet.\nUse the app for a few days first."
            return
        }

        setupBarChart(data, isWeekly)
        setupPieChart(data)
        setupStats(data, isWeekly)
    }

    private fun setupBarChart(data: List<Pair<String, Long>>, isWeekly: Boolean) {
        val entries = data.mapIndexed { i, (_, min) -> BarEntry(i.toFloat(), min.toFloat()) }
        val labels = data.map { (date, _) -> date.substring(5) }

        val dataSet = BarDataSet(entries, "Minutes").apply {
            color = Color.parseColor("#5C6BC0")
            highLightColor = Color.parseColor("#7E57C2")
            valueTextColor = Color.parseColor("#5C6BC0")
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val h = v.toLong() / 60; val m = v.toLong() % 60
                    return if (h > 0) "${h}h" else "${m}m"
                }
            }
        }

        binding.chartBar.apply {
            this.data = BarData(dataSet).apply { barWidth = 0.55f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = if (isWeekly) 0f else -45f
                textSize = if (isWeekly) 11f else 9f
                textColor = Color.GRAY
            }
            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.GRAY
                gridColor = Color.parseColor("#F0F0F0")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float): String {
                        val h = v.toLong() / 60
                        return if (h > 0) "${h}h" else "${v.toInt()}m"
                    }
                }
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setDrawGridBackground(false)
            animateY(700)
            invalidate()
        }
    }

    private fun setupPieChart(data: List<Pair<String, Long>>) {
        val categoryTotals = mutableMapOf<String, Long>()
        for ((date, _) in data) {
            repo.getCategoryTotalsForDate(date).forEach { (cat, mins) ->
                categoryTotals[cat] = (categoryTotals[cat] ?: 0L) + mins
            }
        }

        val pieColors = listOf(
            Color.parseColor("#5C6BC0"), Color.parseColor("#EF5350"),
            Color.parseColor("#26A69A"), Color.parseColor("#FFA726"),
            Color.parseColor("#AB47BC"), Color.parseColor("#29B6F6"),
            Color.parseColor("#66BB6A"), Color.parseColor("#FF7043")
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
            sliceSpace = 3f
            selectionShift = 8f
        }

        binding.chartPie.apply {
            this.data = PieData(pieDataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 44f
            transparentCircleRadius = 48f
            setHoleColor(Color.TRANSPARENT)
            setCenterText("Categories")
            setCenterTextSize(13f)
            setCenterTextColor(Color.GRAY)
            legend.isEnabled = true
            legend.textColor = Color.GRAY
            animateY(900)
            invalidate()
        }
    }

    private fun setupStats(data: List<Pair<String, Long>>, isWeekly: Boolean) {
        val totalMins = data.sumOf { it.second }
        val avgMins = if (data.isNotEmpty()) totalMins / data.size else 0L
        val maxDay = data.maxByOrNull { it.second }
        val minDay = data.filter { it.second > 0 }.minByOrNull { it.second }

        binding.statsSummary.text = buildString {
            appendLine("${if (isWeekly) "Last 7 days" else "Last 30 days"}")
            appendLine("")
            appendLine("⏱  Total: ${repo.formatMinutes(totalMins)}")
            appendLine("📈  Daily avg: ${repo.formatMinutes(avgMins)}")
            maxDay?.let { appendLine("🔺  Busiest: ${it.first}  (${repo.formatMinutes(it.second)})") }
            minDay?.let { append("🔻  Lightest: ${it.first}  (${repo.formatMinutes(it.second)})") }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
