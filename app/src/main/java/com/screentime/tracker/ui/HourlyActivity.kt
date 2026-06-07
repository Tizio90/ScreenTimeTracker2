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

    companion object { const val EXTRA_DATE = "extra_date" }

    private lateinit var binding: ActivityHourlyBinding
    private lateinit var repo: UsageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHourlyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = UsageRepository(this)
        val date = intent.getStringExtra(EXTRA_DATE) ?: repo.getTodayDate()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Hourly Usage"
            subtitle = date
            setDisplayHomeAsUpEnabled(true)
        }

        val hourlyData = repo.getHourlyForDate(date)

        if (hourlyData.isEmpty()) {
            binding.statsText.text = "No hourly data for $date yet.\nRefresh on the main screen first."
            return
        }

        val minutesByHour = LongArray(24)
        for (r in hourlyData) minutesByHour[r.hour] = r.minutes
        val peak = minutesByHour.max()

        val entries = (0..23).map { h -> BarEntry(h.toFloat(), minutesByHour[h].toFloat()) }
        val labels = (0..23).map { repo.formatHour(it) }

        // Color-code bars by time of day
        val colors = (0..23).map { h ->
            when {
                minutesByHour[h] == peak -> Color.parseColor("#5C6BC0")   // peak = brand
                h in 6..9               -> Color.parseColor("#FFA726")    // morning = amber
                h in 12..14             -> Color.parseColor("#26A69A")    // lunch = teal
                h in 18..22             -> Color.parseColor("#7E57C2")    // evening = purple
                else                    -> Color.parseColor("#CFD8DC")    // other = light grey
            }
        }

        val dataSet = BarDataSet(entries, "Minutes").apply {
            this.colors = colors
            valueTextSize = 7f
            valueTextColor = Color.GRAY
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String = if (v > 0) "${v.toInt()}m" else ""
            }
        }

        binding.chartHourly.apply {
            data = BarData(dataSet).apply { barWidth = 0.65f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -55f
                textSize = 8f
                textColor = Color.GRAY
            }
            axisLeft.apply {
                axisMinimum = 0f
                textColor = Color.GRAY
                gridColor = Color.parseColor("#F0F0F0")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = "${v.toInt()}m"
                }
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            animateY(600)
            invalidate()
        }

        val peakRecord = hourlyData.maxByOrNull { it.minutes }
        val totalMins = minutesByHour.sum()

        binding.statsText.text = buildString {
            appendLine("$date")
            appendLine("")
            appendLine("⏱  Total tracked: ${repo.formatMinutes(totalMins)}")
            appendLine("🕐  Active hours: ${hourlyData.size}")
            peakRecord?.let {
                appendLine("🔵  Peak hour: ${repo.formatHour(it.hour)}  (${repo.formatMinutes(it.minutes)})")
            }
            appendLine("")
            append("🟡 Morning  🟢 Lunch  🟣 Evening  🔵 Peak")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
