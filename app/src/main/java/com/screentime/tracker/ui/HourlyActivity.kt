package com.screentime.tracker.ui

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ActivityHourlyBinding

class HourlyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var binding: ActivityHourlyBinding
    private lateinit var repo: UsageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHourlyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = UsageRepository(this)
        val date = intent.getStringExtra(EXTRA_DATE) ?: repo.getTodayDate()

        supportActionBar?.apply {
            title = "Hourly Usage"
            subtitle = date
            setDisplayHomeAsUpEnabled(true)
        }

        val hourlyData = repo.getHourlyForDate(date)

        if (hourlyData.isEmpty()) {
            binding.statsText.text = "No hourly data for $date yet.\nRefresh on the main screen to collect it."
            return
        }

        // Build 24-slot array
        val minutesByHour = LongArray(24)
        for (r in hourlyData) minutesByHour[r.hour] = r.minutes

        val entries = (0..23).map { h -> BarEntry(h.toFloat(), minutesByHour[h].toFloat()) }
        val labels = (0..23).map { repo.formatHour(it) }

        val colors = (0..23).map { h ->
            when {
                minutesByHour[h] == minutesByHour.max() -> Color.parseColor("#E53935") // peak = red
                h in 6..9 -> Color.parseColor("#FB8C00")   // morning = orange
                h in 12..14 -> Color.parseColor("#43A047") // lunch = green
                h in 18..22 -> Color.parseColor("#1976D2") // evening = blue
                else -> Color.parseColor("#90A4AE")         // other = grey
            }
        }

        val dataSet = BarDataSet(entries, "Minutes").apply {
            this.colors = colors
            valueTextSize = 8f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (value > 0) "${value.toInt()}m" else ""
            }
        }

        binding.chartHourly.apply {
            data = BarData(dataSet).apply { barWidth = 0.7f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}m"
                }
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            animateY(600)
            invalidate()
        }

        // Stats
        val peakHour = hourlyData.maxByOrNull { it.minutes }
        val totalMins = minutesByHour.sum()
        val activeHours = hourlyData.size

        binding.statsText.text = buildString {
            appendLine("📊 Date: $date")
            appendLine("⏱ Total tracked: ${repo.formatMinutes(totalMins)}")
            appendLine("🕐 Active hours: $activeHours")
            peakHour?.let {
                appendLine("🔺 Peak hour: ${repo.formatHour(it.hour)}  (${repo.formatMinutes(it.minutes)})")
            }
            appendLine("")
            appendLine("🟠 Morning (6-9am)  🟢 Lunch (12-2pm)  🔵 Evening (6-10pm)")
            append("🔴 Peak hour")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
