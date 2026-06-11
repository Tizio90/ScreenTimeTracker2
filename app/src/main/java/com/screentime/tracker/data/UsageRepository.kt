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

    // ── Day boundary ───────────────────────────────────────

    fun getTodayDate(): String = logicalDateFor(System.currentTimeMillis())

    fun logicalDateFor(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    private fun logicalDayStartMs(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, prefs.dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ── Core collection ────────────────────────────────────

    /**
     * Complete rewrite of the collection engine.
     *
     * Design principles:
     *  1. Always scan the FULL logical day window (dayStart → now).
     *     This is safe because we deduplicate by start_epoch in the DB.
     *  2. Only use ACTIVITY_STOPPED (not PAUSED) for session end.
     *     PAUSED fires for every notification/overlap; STOPPED is the real end.
     *  3. Track open sessions across the full scan — never miss a RESUME
     *     whose STOP comes later in the same scan.
     *  4. Deduplicate: before inserting, check if a session with the same
     *     package + start_epoch already exists.
     *  5. Close any session still open at scan end (app currently in foreground)
     *     as a partial session — marked so it can be updated next cycle.
     */
    fun collectAndSaveToday() {
        if (!hasUsagePermission()) return
        try {
            val now = System.currentTimeMillis()
            val dayStart = logicalDayStartMs(now)
            val today = logicalDateFor(now)
            processWindow(today, dayStart, now)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processWindow(dateLabel: String, windowStart: Long, windowEnd: Long) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        // Load existing session start epochs for today to deduplicate
        val existingStarts = db.getSessionsForDate(dateLabel)
            .map { it.startEpoch }
            .toHashSet()

        // Also delete any previously saved "open" (partial) sessions so we rewrite them
        db.deleteOpenSessionsForDate(dateLabel)

        // Map: package → resume timestamp
        val openSessions = mutableMapOf<String, Long>()

        val events = usm.queryEvents(windowStart, windowEnd) ?: return
        val ev = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue

            // Skip system UI and our own app
            if (pkg == "com.android.systemui" || pkg == context.packageName) continue

            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Only record if not already tracking this app
                    // (handles multiple activities in same app)
                    if (!openSessions.containsKey(pkg)) {
                        openSessions[pkg] = ev.timeStamp
                    }
                }

                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resumeAt = openSessions.remove(pkg) ?: continue
                    val stopAt = ev.timeStamp

                    // Clamp to window boundaries
                    val sessionStart = maxOf(resumeAt, windowStart)
                    val sessionEnd = minOf(stopAt, windowEnd)
                    if (sessionEnd <= sessionStart) continue

                    val durationMs = sessionEnd - sessionStart
                    val durationMin = durationMs / 60_000L
                    if (durationMin < 1L) continue

                    // Skip if already saved (deduplication by start epoch)
                    if (existingStarts.contains(sessionStart)) continue

                    saveSession(pm, pkg, dateLabel, sessionStart, sessionEnd, durationMin, isOpen = false)
                }
            }
        }

        // Save still-open sessions as partial (app still in foreground)
        for ((pkg, resumeAt) in openSessions) {
            val sessionStart = maxOf(resumeAt, windowStart)
            val durationMs = windowEnd - sessionStart
            val durationMin = durationMs / 60_000L
            if (durationMin < 1L) continue
            if (existingStarts.contains(sessionStart)) continue
            saveSession(pm, pkg, dateLabel, sessionStart, windowEnd, durationMin, isOpen = true)
        }
    }

    private fun saveSession(
        pm: PackageManager,
        pkg: String,
        dateLabel: String,
        startMs: Long,
        endMs: Long,
        durationMin: Long,
        isOpen: Boolean
    ) {
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) { pkg }

        db.insertSession(SessionRecord(
            packageName = pkg,
            appName = appName,
            date = dateLabel,
            startEpoch = startMs,
            endEpoch = endMs,
            durationMinutes = durationMin,
            startTime = timeFormat.format(Date(startMs)),
            endTime = if (isOpen) "ongoing" else timeFormat.format(Date(endMs)),
            isOpen = isOpen
        ))
    }

    // ── Data access ────────────────────────────────────────

    fun getDailyRecords(date: String) = db.getDailyRecords(date)
    fun getSessionsForDate(date: String) = db.getSessionsForDate(date)
    fun getSessionsForPackage(pkg: String) = db.getSessionsForPackage(pkg)
    fun getAllSessions() = db.getAllSessions()
    fun getTotalMinutesForDate(date: String) = db.getTotalMinutesForDate(date)
    fun getAvailableDates() = db.getAvailableDates()
    fun getHourlyForDate(date: String) = db.getHourlyForDate(date)
    fun getPeakHour(date: String) = db.getHourlyForDate(date).maxByOrNull { it.minutes }?.hour

    fun getCategoryTotalsForDate(date: String): Map<String, Long> {
        val allMeta = db.getAllMeta()
        return db.getDailyRecords(date).associate { r ->
            val cat = allMeta[r.packageName]?.category ?: "Uncategorized"
            cat to r.totalMinutes
        }.entries.fold(mutableMapOf<String, Long>()) { acc, (cat, min) ->
            acc[cat] = (acc[cat] ?: 0L) + min; acc
        }
    }

    fun getWeeklyData() = rangeFor(6).let { db.getDailyTotals(it[0], it[1]) }
    fun getMonthlyData() = rangeFor(29).let { db.getDailyTotals(it[0], it[1]) }

    private fun rangeFor(daysBack: Int): Array<String> {
        val cal = Calendar.getInstance()
        if (prefs.dayStartHour > 0 && cal.get(Calendar.HOUR_OF_DAY) < prefs.dayStartHour)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return arrayOf(dateFormat.format(cal.time), end)
    }

    // ── Meta ───────────────────────────────────────────────

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
