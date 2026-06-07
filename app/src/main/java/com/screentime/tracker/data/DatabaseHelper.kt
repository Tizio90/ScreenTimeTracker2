package com.screentime.tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ── Data classes ───────────────────────────────────────────

data class SessionRecord(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val date: String,           // logical date yyyy-MM-dd
    val startEpoch: Long,       // ms since epoch
    val endEpoch: Long,         // ms since epoch
    val durationMinutes: Long,  // (endEpoch - startEpoch) / 60000, stored for fast queries
    val startTime: String,      // HH:mm for display
    val endTime: String         // HH:mm for display
)

data class DailyRecord(
    val packageName: String,
    val appName: String,
    val date: String,
    val totalMinutes: Long,
    val sessionCount: Int
)

data class HourlyRecord(
    val hour: Int,
    val minutes: Long
)

data class AppMeta(
    val packageName: String,
    val customName: String?,
    val category: String
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "screen_time.db"
        const val DATABASE_VERSION = 6

        // Sessions table — one row per app session
        const val TABLE_SESSIONS = "sessions"
        const val COL_ID = "_id"
        const val COL_PACKAGE = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_DATE = "date"
        const val COL_START_EPOCH = "start_epoch"
        const val COL_END_EPOCH = "end_epoch"
        const val COL_DURATION_MIN = "duration_minutes"
        const val COL_START_TIME = "start_time"
        const val COL_END_TIME = "end_time"

        // App metadata
        const val TABLE_META = "app_meta"
        const val COL_CUSTOM_NAME = "custom_name"
        const val COL_CATEGORY = "category"

        // Custom categories
        const val TABLE_CUSTOM_CATEGORIES = "custom_categories"
        const val COL_CAT_NAME = "name"

        val DEFAULT_CATEGORIES = listOf(
            "Uncategorized", "Social", "Games", "Productivity",
            "Entertainment", "Browser", "Health", "Finance", "Other"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Sessions: every discrete foreground session
        db.execSQL("""
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_DATE TEXT NOT NULL,
                $COL_START_EPOCH INTEGER NOT NULL,
                $COL_END_EPOCH INTEGER NOT NULL,
                $COL_DURATION_MIN INTEGER NOT NULL,
                $COL_START_TIME TEXT NOT NULL,
                $COL_END_TIME TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_sess_date ON $TABLE_SESSIONS ($COL_DATE)")
        db.execSQL("CREATE INDEX idx_sess_pkg ON $TABLE_SESSIONS ($COL_PACKAGE)")
        db.execSQL("CREATE INDEX idx_sess_start ON $TABLE_SESSIONS ($COL_START_EPOCH)")

        // App metadata
        db.execSQL("""
            CREATE TABLE $TABLE_META (
                $COL_PACKAGE TEXT PRIMARY KEY,
                $COL_CUSTOM_NAME TEXT,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'Uncategorized'
            )
        """.trimIndent())

        // Custom categories
        db.execSQL("""
            CREATE TABLE $TABLE_CUSTOM_CATEGORIES (
                $COL_CAT_NAME TEXT PRIMARY KEY
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS $TABLE_META (
                $COL_PACKAGE TEXT PRIMARY KEY,
                $COL_CUSTOM_NAME TEXT,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'Uncategorized'
            )""")
        }
        if (oldVersion < 3) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_CATEGORIES (
                $COL_CAT_NAME TEXT PRIMARY KEY
            )""")
        }
        // v6: migrate to session-based tracking. Drop old tables, create sessions.
        if (oldVersion < 6) {
            db.execSQL("DROP TABLE IF EXISTS usage_log")
            db.execSQL("DROP TABLE IF EXISTS hourly_log")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PACKAGE TEXT NOT NULL,
                    $COL_APP_NAME TEXT NOT NULL,
                    $COL_DATE TEXT NOT NULL,
                    $COL_START_EPOCH INTEGER NOT NULL,
                    $COL_END_EPOCH INTEGER NOT NULL,
                    $COL_DURATION_MIN INTEGER NOT NULL,
                    $COL_START_TIME TEXT NOT NULL,
                    $COL_END_TIME TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sess_date ON $TABLE_SESSIONS ($COL_DATE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sess_pkg ON $TABLE_SESSIONS ($COL_PACKAGE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sess_start ON $TABLE_SESSIONS ($COL_START_EPOCH)")
        }
    }

    // ── Sessions ───────────────────────────────────────────

    fun insertSession(session: SessionRecord) {
        val values = ContentValues().apply {
            put(COL_PACKAGE, session.packageName)
            put(COL_APP_NAME, session.appName)
            put(COL_DATE, session.date)
            put(COL_START_EPOCH, session.startEpoch)
            put(COL_END_EPOCH, session.endEpoch)
            put(COL_DURATION_MIN, session.durationMinutes)
            put(COL_START_TIME, session.startTime)
            put(COL_END_TIME, session.endTime)
        }
        writableDatabase.insert(TABLE_SESSIONS, null, values)
    }

    /** Delete all sessions for a date+package so we can rewrite them cleanly */
    fun deleteSessionsForDateAndPackage(date: String, pkg: String) {
        writableDatabase.delete(
            TABLE_SESSIONS,
            "$COL_DATE=? AND $COL_PACKAGE=?",
            arrayOf(date, pkg)
        )
    }

    private fun cursorToSession(c: android.database.Cursor) = SessionRecord(
        id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
        packageName = c.getString(c.getColumnIndexOrThrow(COL_PACKAGE)),
        appName = c.getString(c.getColumnIndexOrThrow(COL_APP_NAME)),
        date = c.getString(c.getColumnIndexOrThrow(COL_DATE)),
        startEpoch = c.getLong(c.getColumnIndexOrThrow(COL_START_EPOCH)),
        endEpoch = c.getLong(c.getColumnIndexOrThrow(COL_END_EPOCH)),
        durationMinutes = c.getLong(c.getColumnIndexOrThrow(COL_DURATION_MIN)),
        startTime = c.getString(c.getColumnIndexOrThrow(COL_START_TIME)),
        endTime = c.getString(c.getColumnIndexOrThrow(COL_END_TIME))
    )

    fun getSessionsForDate(date: String): List<SessionRecord> {
        val r = mutableListOf<SessionRecord>()
        val c = readableDatabase.query(
            TABLE_SESSIONS, null, "$COL_DATE=?", arrayOf(date),
            null, null, "$COL_START_EPOCH ASC"
        )
        c.use { while (it.moveToNext()) r.add(cursorToSession(it)) }
        return r
    }

    fun getSessionsForPackage(pkg: String): List<SessionRecord> {
        val r = mutableListOf<SessionRecord>()
        val c = readableDatabase.query(
            TABLE_SESSIONS, null, "$COL_PACKAGE=?", arrayOf(pkg),
            null, null, "$COL_START_EPOCH DESC"
        )
        c.use { while (it.moveToNext()) r.add(cursorToSession(it)) }
        return r
    }

    fun getAllSessions(): List<SessionRecord> {
        val r = mutableListOf<SessionRecord>()
        val c = readableDatabase.query(
            TABLE_SESSIONS, null, null, null,
            null, null, "$COL_START_EPOCH DESC"
        )
        c.use { while (it.moveToNext()) r.add(cursorToSession(it)) }
        return r
    }

    fun getSessionsInRange(startEpoch: Long, endEpoch: Long): List<SessionRecord> {
        val r = mutableListOf<SessionRecord>()
        val c = readableDatabase.query(
            TABLE_SESSIONS, null,
            "$COL_START_EPOCH>=? AND $COL_END_EPOCH<=?",
            arrayOf(startEpoch.toString(), endEpoch.toString()),
            null, null, "$COL_START_EPOCH ASC"
        )
        c.use { while (it.moveToNext()) r.add(cursorToSession(it)) }
        return r
    }

    // ── Aggregated views (computed from sessions) ──────────

    /** Per-app totals for a date, sorted by total minutes desc */
    fun getDailyRecords(date: String): List<DailyRecord> {
        val r = mutableListOf<DailyRecord>()
        val c = readableDatabase.rawQuery("""
            SELECT $COL_PACKAGE, $COL_APP_NAME,
                   $COL_DATE,
                   SUM($COL_DURATION_MIN) as total_min,
                   COUNT(*) as session_count
            FROM $TABLE_SESSIONS
            WHERE $COL_DATE=?
            GROUP BY $COL_PACKAGE
            ORDER BY total_min DESC
        """.trimIndent(), arrayOf(date))
        c.use { while (it.moveToNext()) {
            r.add(DailyRecord(
                packageName = it.getString(0),
                appName = it.getString(1),
                date = it.getString(2),
                totalMinutes = it.getLong(3),
                sessionCount = it.getInt(4)
            ))
        }}
        return r
    }

    fun getTotalMinutesForDate(date: String): Long {
        val c = readableDatabase.rawQuery(
            "SELECT SUM($COL_DURATION_MIN) FROM $TABLE_SESSIONS WHERE $COL_DATE=?", arrayOf(date)
        )
        return c.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    fun getDailyTotals(startDate: String, endDate: String): List<Pair<String, Long>> {
        val r = mutableListOf<Pair<String, Long>>()
        val c = readableDatabase.rawQuery("""
            SELECT $COL_DATE, SUM($COL_DURATION_MIN)
            FROM $TABLE_SESSIONS
            WHERE $COL_DATE>=? AND $COL_DATE<=?
            GROUP BY $COL_DATE
            ORDER BY $COL_DATE ASC
        """.trimIndent(), arrayOf(startDate, endDate))
        c.use { while (it.moveToNext()) r.add(Pair(it.getString(0), it.getLong(1))) }
        return r
    }

    fun getAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val c = readableDatabase.rawQuery(
            "SELECT DISTINCT $COL_DATE FROM $TABLE_SESSIONS ORDER BY $COL_DATE DESC", null
        )
        c.use { while (it.moveToNext()) dates.add(it.getString(0)) }
        return dates
    }

    /** Hourly totals for a specific date, from sessions */
    fun getHourlyForDate(date: String): List<HourlyRecord> {
        // Extract hour from start_time string "HH:mm" — group and sum
        val c = readableDatabase.rawQuery("""
            SELECT CAST(SUBSTR($COL_START_TIME, 1, 2) AS INTEGER) as hour,
                   SUM($COL_DURATION_MIN)
            FROM $TABLE_SESSIONS
            WHERE $COL_DATE=?
            GROUP BY hour
            ORDER BY hour ASC
        """.trimIndent(), arrayOf(date))
        val r = mutableListOf<HourlyRecord>()
        c.use { while (it.moveToNext()) r.add(HourlyRecord(it.getInt(0), it.getLong(1))) }
        return r
    }

    /** All-time hourly totals across all dates */
    fun getAllTimeHourlyTotals(): List<HourlyRecord> {
        val c = readableDatabase.rawQuery("""
            SELECT CAST(SUBSTR($COL_START_TIME, 1, 2) AS INTEGER) as hour,
                   SUM($COL_DURATION_MIN)
            FROM $TABLE_SESSIONS
            GROUP BY hour
            ORDER BY hour ASC
        """.trimIndent(), null)
        val r = mutableListOf<HourlyRecord>()
        c.use { while (it.moveToNext()) r.add(HourlyRecord(it.getInt(0), it.getLong(1))) }
        return r
    }

    // ── App Meta ───────────────────────────────────────────

    fun saveAppMeta(packageName: String, customName: String?, category: String) {
        val values = ContentValues().apply {
            put(COL_PACKAGE, packageName)
            put(COL_CUSTOM_NAME, customName?.takeIf { it.isNotBlank() })
            put(COL_CATEGORY, category)
        }
        writableDatabase.insertWithOnConflict(TABLE_META, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAppMeta(pkg: String): AppMeta {
        val c = readableDatabase.query(TABLE_META, null, "$COL_PACKAGE=?", arrayOf(pkg), null, null, null)
        return c.use {
            if (it.moveToFirst()) AppMeta(pkg,
                it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)),
                it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)))
            else AppMeta(pkg, null, "Uncategorized")
        }
    }

    fun getAllMeta(): Map<String, AppMeta> {
        val map = mutableMapOf<String, AppMeta>()
        val c = readableDatabase.query(TABLE_META, null, null, null, null, null, null)
        c.use { while (it.moveToNext()) {
            val pkg = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE))
            map[pkg] = AppMeta(pkg,
                it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)),
                it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)))
        }}
        return map
    }

    // ── Custom Categories ──────────────────────────────────

    fun addCustomCategory(name: String) {
        val values = ContentValues().apply { put(COL_CAT_NAME, name.trim()) }
        writableDatabase.insertWithOnConflict(TABLE_CUSTOM_CATEGORIES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun deleteCustomCategory(name: String) {
        writableDatabase.delete(TABLE_CUSTOM_CATEGORIES, "$COL_CAT_NAME=?", arrayOf(name))
    }

    fun getCustomCategories(): List<String> {
        val r = mutableListOf<String>()
        val c = readableDatabase.query(TABLE_CUSTOM_CATEGORIES, null, null, null, null, null, "$COL_CAT_NAME ASC")
        c.use { while (it.moveToNext()) r.add(it.getString(0)) }
        return r
    }

    fun getAllCategories() = DEFAULT_CATEGORIES + getCustomCategories()
}
