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
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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

    fun getTodayDate(): String = logicalDateFor(System.currentTimeMillis())

    fun logicalDateFor(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    private fun logicalDayStartFor(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, prefs.dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── Collection ─────────────────────────────────────────

    /**
     * Collects all sessions for today's logical day.
     * Strategy:
     *  1. Find the last saved session's end epoch for today (or day start if none)
     *  2. Query events only from that point forward — avoids re-processing old events
     *  3. Each completed RESUME→PAUSE pair becomes one SessionRecord
     *  4. Open session (app still running) is NOT saved — saved on next collection cycle
     */
    fun collectAndSaveToday() {
        if (!hasUsagePermission()) return
        try {
            val now = System.currentTimeMillis()
            val dayStart = logicalDayStartFor(now)
            val today = logicalDateFor(now)

            // Find where to start scanning from — after the last saved session end
            val lastSavedEnd = db.getSessionsForDate(today)
                .maxOfOrNull { it.endEpoch } ?: dayStart

            // Add 1ms so we don't re-process the last event
            val scanFrom = maxOf(lastSavedEnd, dayStart)

            collectSessions(today, scanFrom, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Queries UsageEvents from [startMs] to [endMs] and saves completed sessions.
     * Each RESUME→PAUSE/STOP pair >= 1 minute is saved as a SessionRecord.
     * Open sessions (no PAUSE yet) are skipped — they'll be captured next cycle.
     */
    private fun collectSessions(dateLabel: String, startMs: Long, endMs: Long) {
        if (endMs <= startMs) return

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val events = usm.queryEvents(startMs, endMs) ?: return
        val ev = UsageEvents.Event()

        // pkg → resume epoch (clamped to startMs)
        val openSessions = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue

            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openSessions[pkg] = maxOf(ev.timeStamp, startMs)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumeAt = openSessions.remove(pkg) ?: continue
                    val pauseAt = ev.timeStamp
                    val durationMs = pauseAt - resumeAt
                    val durationMin = durationMs / 60_000L
                    if (durationMin < 1L) continue  // skip sub-minute sessions

                    val appName = resolveAppName(pm, pkg)
                    val sessionDate = logicalDateFor(resumeAt)

                    db.insertSession(SessionRecord(
                        packageName = pkg,
                        appName = appName,
                        date = sessionDate,
                        startEpoch = resumeAt,
                        endEpoch = pauseAt,
                        durationMinutes = durationMin,
                        startTime = timeFormat.format(Date(resumeAt)),
                        endTime = timeFormat.format(Date(pauseAt))
                    ))
                }
            }
        }
        // Note: open sessions are NOT saved here — they'll be captured on the next cycle
        // This prevents saving partial/inflated durations for currently active apps
    }

    private fun resolveAppName(pm: PackageManager, pkg: String): String {
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) { pkg }
    }

    // ── Display name ───────────────────────────────────────

    fun getDisplayName(packageName: String, fallback: String): String {
        val meta = db.getAppMeta(packageName)
        return meta.customName?.takeIf { it.isNotBlank() } ?: fallback
    }

    // ── Data access ────────────────────────────────────────

    fun getDailyRecords(date: String) = db.getDailyRecords(date)
    fun getSessionsForDate(date: String) = db.getSessionsForDate(date)
    fun getSessionsForPackage(pkg: String) = db.getSessionsForPackage(pkg)
    fun getAllSessions() = db.getAllSessions()
    fun getTotalMinutesForDate(date: String) = db.getTotalMinutesForDate(date)
    fun getAvailableDates() = db.getAvailableDates()
    fun getHourlyForDate(date: String) = db.getHourlyForDate(date)
    fun getAllTimeHourlyTotals() = db.getAllTimeHourlyTotals()
    fun getPeakHour(date: String) = db.getHourlyForDate(date).maxByOrNull { it.minutes }?.hour

    fun getCategoryTotalsForDate(date: String): Map<String, Long> {
        val allMeta = db.getAllMeta()
        val totals = mutableMapOf<String, Long>()
        for (r in db.getDailyRecords(date)) {
            val cat = allMeta[r.packageName]?.category ?: "Uncategorized"
            totals[cat] = (totals[cat] ?: 0L) + r.totalMinutes
        }
        return totals
    }

    fun getWeeklyData(): List<Pair<String, Long>> {
        val (start, end) = rangeFor(6)
        return db.getDailyTotals(start, end)
    }

    fun getMonthlyData(): List<Pair<String, Long>> {
        val (start, end) = rangeFor(29)
        return db.getDailyTotals(start, end)
    }

    private fun rangeFor(daysBack: Int): Pair<String, String> {
        val cal = Calendar.getInstance()
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return Pair(dateFormat.format(cal.time), end)
    }

    // ── Meta passthrough ───────────────────────────────────

    fun saveAppMeta(pkg: String, customName: String?, category: String) = db.saveAppMeta(pkg, customName, category)
    fun getAppMeta(pkg: String) = db.getAppMeta(pkg)
    fun getAllMeta() = db.getAllMeta()
    fun addCustomCategory(name: String) = db.addCustomCategory(name)
    fun deleteCustomCategory(name: String) = db.deleteCustomCategory(name)
    fun getCustomCategories() = db.getCustomCategories()
    fun getAllCategories() = db.getAllCategories()

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
