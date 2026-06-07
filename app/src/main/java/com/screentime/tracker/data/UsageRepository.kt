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

    // ── Permission ─────────────────────────────────────────

    fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    // ── Day boundary helpers ───────────────────────────────

    /**
     * Returns the logical "today" label (yyyy-MM-dd) for right now.
     * If current hour < dayStartHour, we are still in the previous logical day.
     */
    fun getTodayDate(): String = logicalDateFor(System.currentTimeMillis())

    /**
     * Returns the logical date label for any given epoch ms.
     */
    fun logicalDateFor(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dateFormat.format(cal.time)
    }

    /**
     * Returns the epoch ms of the start of the logical day containing [epochMs].
     */
    private fun logicalDayStartFor(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, prefs.dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── Collection ─────────────────────────────────────────

    /**
     * Collects usage for the current logical day (from day-start to now).
     * Fully event-driven — never uses queryUsageStats which ignores custom boundaries.
     * Safe to call repeatedly; uses REPLACE so no double-counting.
     */
    fun collectAndSaveToday() {
        if (!hasUsagePermission()) return
        try {
            val now = System.currentTimeMillis()
            val dayStart = logicalDayStartFor(now)
            val dateLabel = logicalDateFor(now)
            collectWindow(dateLabel, dayStart, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Core engine: queries raw UsageEvents in [startMs, endMs],
     * builds per-app totals and per-hour totals, writes to DB.
     *
     * Key correctness properties:
     * - Uses only events within the window (startMs..endMs)
     * - RESUME timestamps are clamped to startMs (handles apps already running at window start)
     * - Open sessions at endMs are closed at endMs (handles currently running app)
     * - Hour distribution splits sessions that cross hour boundaries
     * - Each (package, date) row is replaced atomically — no accumulation errors
     */
    private fun collectWindow(dateLabel: String, startMs: Long, endMs: Long) {
        if (endMs <= startMs) return

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val appTotalMs = mutableMapOf<String, Long>()   // pkg → total ms in foreground
        val hourSlices = LongArray(24)                   // hour 0-23 → total minutes
        val openSessions = mutableMapOf<String, Long>()  // pkg → resume timestamp

        val events = usm.queryEvents(startMs, endMs) ?: return
        val ev = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue

            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Clamp to window start in case app was already running before our window
                    openSessions[pkg] = maxOf(ev.timeStamp, startMs)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumeAt = openSessions.remove(pkg) ?: continue
                    val pauseAt = minOf(ev.timeStamp, endMs)
                    if (pauseAt > resumeAt) {
                        val dur = pauseAt - resumeAt
                        appTotalMs[pkg] = (appTotalMs[pkg] ?: 0L) + dur
                        splitIntoHours(resumeAt, pauseAt, hourSlices)
                    }
                }
            }
        }

        // Close sessions still open at endMs (app in foreground right now)
        for ((pkg, resumeAt) in openSessions) {
            val dur = endMs - resumeAt
            if (dur > 0) {
                appTotalMs[pkg] = (appTotalMs[pkg] ?: 0L) + dur
                splitIntoHours(resumeAt, endMs, hourSlices)
            }
        }

        // Persist per-app totals (skip < 1 minute)
        for ((pkg, totalMs) in appTotalMs) {
            val mins = totalMs / 60_000L
            if (mins < 1L) continue
            val name = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) { pkg }
            db.upsertUsage(UsageRecord(
                packageName = pkg, appName = name, date = dateLabel,
                totalMinutes = mins, lastUpdated = endMs
            ))
        }

        // Persist hourly data (skip empty hours)
        for (h in 0..23) {
            if (hourSlices[h] > 0L) db.upsertHourly(dateLabel, h, hourSlices[h])
        }
    }

    /**
     * Splits a foreground session [fromMs, toMs] across clock hours.
     * Correctly handles sessions that span multiple hours or cross midnight.
     * Adds minutes to [slices] array indexed by hour 0-23.
     */
    private fun splitIntoHours(fromMs: Long, toMs: Long, slices: LongArray) {
        var cursor = fromMs
        val cal = Calendar.getInstance()
        while (cursor < toMs) {
            cal.timeInMillis = cursor
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            // Advance cal to end of this clock hour
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val hourEnd = minOf(cal.timeInMillis, toMs)
            val sliceMs = hourEnd - cursor
            if (sliceMs > 0) slices[hour] += sliceMs / 60_000L
            cursor = hourEnd + 1L
        }
    }

    // ── Summary data ───────────────────────────────────────

    fun getWeeklyData(): List<Pair<String, Long>> {
        val (start, end) = weekRange()
        return db.getDailyTotals(start, end)
    }

    fun getMonthlyData(): List<Pair<String, Long>> {
        val (start, end) = monthRange()
        return db.getDailyTotals(start, end)
    }

    private fun weekRange(): Pair<String, String> {
        val now = System.currentTimeMillis()
        val endCal = Calendar.getInstance().apply { timeInMillis = now }
        if (prefs.dayStartHour > 0 && endCal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            endCal.add(Calendar.DAY_OF_YEAR, -1)
        val end = dateFormat.format(endCal.time)
        endCal.add(Calendar.DAY_OF_YEAR, -6)
        return Pair(dateFormat.format(endCal.time), end)
    }

    private fun monthRange(): Pair<String, String> {
        val now = System.currentTimeMillis()
        val endCal = Calendar.getInstance().apply { timeInMillis = now }
        if (prefs.dayStartHour > 0 && endCal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            endCal.add(Calendar.DAY_OF_YEAR, -1)
        val end = dateFormat.format(endCal.time)
        endCal.add(Calendar.DAY_OF_YEAR, -29)
        return Pair(dateFormat.format(endCal.time), end)
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

    // ── Passthrough ────────────────────────────────────────

    fun getDisplayName(packageName: String, fallback: String): String {
        val meta = db.getAppMeta(packageName)
        return meta.customName?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun saveAppMeta(pkg: String, customName: String?, category: String) =
        db.saveAppMeta(pkg, customName, category)

    fun getAppMeta(pkg: String) = db.getAppMeta(pkg)
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
    fun getHourlyForDate(date: String) = db.getHourlyForDate(date)
    fun getPeakHour(date: String) = db.getHourlyForDate(date).maxByOrNull { it.minutes }?.hour

    // ── Formatting ─────────────────────────────────────────

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60L; val m = minutes % 60L
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }

    fun formatHour(hour: Int): String = when {
        hour == 0  -> "12am"
        hour < 12  -> "${hour}am"
        hour == 12 -> "12pm"
        else       -> "${hour - 12}pm"
    }
}
