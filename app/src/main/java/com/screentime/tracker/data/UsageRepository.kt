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
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Called by WorkManager — collects today's stats and saves to DB. Battery-friendly. */
    fun collectAndSaveToday() {
        val today = dateFormat.format(Date())
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val endOfDay = System.currentTimeMillis()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            endOfDay
        ) ?: return

        val pm = context.packageManager

        for (stat in stats) {
            val totalMs = stat.totalTimeInForeground
            if (totalMs < 60_000) continue  // skip apps used less than 1 minute

            val appName = try {
                val info = pm.getApplicationInfo(stat.packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                stat.packageName
            }

            val record = UsageRecord(
                packageName = stat.packageName,
                appName = appName,
                date = today,
                totalMinutes = totalMs / 60_000,
                lastUpdated = System.currentTimeMillis()
            )
            db.upsertUsage(record)
        }
    }

    fun getUsageForDate(date: String): List<UsageRecord> = db.getUsageForDate(date)

    fun getUsageForPackage(packageName: String): List<UsageRecord> = db.getUsageForPackage(packageName)

    fun getAllRecords(): List<UsageRecord> = db.getAllRecords()

    fun getAvailableDates(): List<String> = db.getAvailableDates()

    fun getTotalMinutesForDate(date: String): Long = db.getTotalMinutesForDate(date)

    fun getTodayDate(): String = dateFormat.format(Date())

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}
