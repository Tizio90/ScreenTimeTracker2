package com.screentime.tracker.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.text.SimpleDateFormat
import java.util.*

class UsageRepository(private val context: Context) {

    private val db = DatabaseHelper(context)
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

    fun collectAndSaveToday() {
        try {
            val today = dateFormat.format(Date())
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()
            ) ?: return
            val pm = context.packageManager
            for (stat in stats) {
                if (stat.totalTimeInForeground < 60_000) continue
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: PackageManager.NameNotFoundException) { stat.packageName }
                db.upsertUsage(UsageRecord(
                    packageName = stat.packageName, appName = appName, date = today,
                    totalMinutes = stat.totalTimeInForeground / 60_000,
                    lastUpdated = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Display name: custom name takes priority over system name
    fun getDisplayName(record: UsageRecord): String {
        val meta = db.getAppMeta(record.packageName)
        return meta.customName?.takeIf { it.isNotBlank() } ?: record.appName
    }

    fun getDisplayName(packageName: String, fallback: String): String {
        val meta = db.getAppMeta(packageName)
        return meta.customName?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun saveAppMeta(packageName: String, customName: String?, category: String) =
        db.saveAppMeta(packageName, customName, category)

    fun getAppMeta(packageName: String) = db.getAppMeta(packageName)

    fun getUsageForDate(date: String) = db.getUsageForDate(date)
    fun getUsageForPackage(pkg: String) = db.getUsageForPackage(pkg)
    fun getAllRecords() = db.getAllRecords()
    fun getAvailableDates() = db.getAvailableDates()
    fun getTotalMinutesForDate(date: String) = db.getTotalMinutesForDate(date)
    fun getTodayDate(): String = dateFormat.format(Date())
    fun getAllMeta() = db.getAllMeta()

    fun getWeeklyData(): List<Pair<String, Long>> {
        val cal = Calendar.getInstance()
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFormat.format(cal.time)
        return db.getDailyTotals(startDate, endDate)
    }

    fun getMonthlyData(): List<Pair<String, Long>> {
        val cal = Calendar.getInstance()
        val endDate = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -29)
        val startDate = dateFormat.format(cal.time)
        return db.getDailyTotals(startDate, endDate)
    }

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
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
