package com.screentime.tracker.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository(private val context: Context) {

    private val db = DatabaseHelper(context)
    val prefs = Prefs(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    /** Returns the logical "today" date string based on day start hour */
    fun getTodayDate(): String {
        val cal = Calendar.getInstance()
        val dayStartHour = prefs.dayStartHour
        // If current time is before day start hour, the logical day is still yesterday
        if (cal.get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dateFormat.format(cal.time)
    }

    /** Returns start-of-logical-day epoch ms */
    private fun getStartOfLogicalDay(): Long {
        val cal = Calendar.getInstance()
        val dayStartHour = prefs.dayStartHour
        if (cal.get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun collectAndSaveToday() {
        try {
            val today = getTodayDate()
            val startOfDay = getStartOfLogicalDay()
            val now = System.currentTimeMillis()

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            // Daily totals — query from logical day start
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now) ?: return
            for (stat in stats) {
                if (stat.totalTimeInForeground < 60_000) continue
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) { stat.packageName }
                db.upsertUsage(UsageRecord(
                    packageName = stat.packageName, appName = appName, date = today,
                    totalMinutes = stat.totalTimeInForeground / 60_000,
                    lastUpdated = now
                ))
            }

            // Hourly breakdown using UsageEvents from logical day start
            val hourlyMinutes = LongArray(24)
            val events = usm.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()
            var lastForegroundTime = -1L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> lastForegroundTime = event.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (lastForegroundTime > 0) {
                            val durationMs = event.timeStamp - lastForegroundTime
                            val hour = Calendar.getInstance().apply { timeInMillis = lastForegroundTime }.get(Calendar.HOUR_OF_DAY)
                            hourlyMinutes[hour] += durationMs / 60_000
                            lastForegroundTime = -1L
                        }
                    }
                }
            }
            for (h in 0..23) {
                if (hourlyMinutes[h] > 0) db.upsertHourly(today, h, hourlyMinutes[h])
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getDisplayName(packageName: String, fallback: String): String {
        val meta = db.getAppMeta(packageName)
        return meta.customName?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun saveAppMeta(packageName: String, customName: String?, category: String) =
        db.saveAppMeta(packageName, customName, category)

    fun getAppMeta(packageName: String) = db.getAppMeta(packageName)
    fun getAllMeta() = db.getAllMeta()

    fun addCustomCategory(name: String) = db.addCustomCategory(name)
    fun deleteCustomCategory(name: String) = db.deleteCustomCategory(name)
    fun getCustomCategories() = db.getCustomCategories()
    fun getAllCategories() = db.getAllCategories()

    fun getUsageForDate(date: String) = db.getUsageForDate(date)
    fun getUsageForPackage(pkg: String) = db.getUsageForPackage(pkg)
    fun getAllRecords() = db.getAllRecords()
    fun getAvailableDates() = db.getAvailableDates()
    fun getTotalMinutesForDate(date: String) = db.getTotalMinutesForDate(date)

    fun getWeeklyData(): List<Pair<String, Long>> {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour) cal.add(Calendar.DAY_OF_YEAR, -1)
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return db.getDailyTotals(dateFormat.format(cal.time), endDate)
    }

    fun getMonthlyData(): List<Pair<String, Long>> {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour) cal.add(Calendar.DAY_OF_YEAR, -1)
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -29)
        return db.getDailyTotals(dateFormat.format(cal.time), endDate)
    }

    fun getHourlyForDate(date: String) = db.getHourlyForDate(date)
    fun getAllTimeHourlyTotals() = db.getAllTimeHourlyTotals()

    fun getPeakHour(date: String): Int? =
        db.getHourlyForDate(date).maxByOrNull { it.minutes }?.hour

    fun getCategoryTotalsForDate(date: String): Map<String, Long> {
        val records = db.getUsageForDate(date)
        val allMeta = db.getAllMeta()
        val totals = mutableMapOf<String, Long>()
        for (r in records) {
            val cat = allMeta[r.packageName]?.category ?: "Uncategorized"
            totals[cat] = (totals[cat] ?: 0L) + r.totalMinutes
        }
        return totals
    }

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun formatHour(hour: Int): String = when {
        hour == 0 -> "12am"
        hour < 12 -> "${hour}am"
        hour == 12 -> "12pm"
        else -> "${hour - 12}pm"
    }
}
