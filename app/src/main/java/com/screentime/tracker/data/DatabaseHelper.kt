package com.screentime.tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class UsageRecord(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val date: String,
    val totalMinutes: Long,
    val lastUpdated: Long
)

data class HourlyRecord(
    val hour: Int,       // 0-23
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
        const val DATABASE_VERSION = 5

        const val TABLE_USAGE = "usage_log"
        const val COL_ID = "_id"
        const val COL_PACKAGE = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_DATE = "date"
        const val COL_MINUTES = "total_minutes"
        const val COL_UPDATED = "last_updated"

        const val TABLE_META = "app_meta"
        const val COL_CUSTOM_NAME = "custom_name"
        const val COL_CATEGORY = "category"

        const val TABLE_HOURLY = "hourly_log"
        const val COL_HOUR = "hour"

        const val TABLE_CUSTOM_CATEGORIES = "custom_categories"
        const val COL_CAT_NAME = "name"

        val DEFAULT_CATEGORIES = listOf(
            "Uncategorized", "Social", "Games", "Productivity",
            "Entertainment", "Browser", "Health", "Finance", "Other"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_USAGE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PACKAGE TEXT NOT NULL,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_DATE TEXT NOT NULL,
                $COL_MINUTES INTEGER NOT NULL DEFAULT 0,
                $COL_UPDATED INTEGER NOT NULL,
                UNIQUE($COL_PACKAGE, $COL_DATE) ON CONFLICT REPLACE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_date ON $TABLE_USAGE ($COL_DATE)")

        db.execSQL("""
            CREATE TABLE $TABLE_META (
                $COL_PACKAGE TEXT PRIMARY KEY,
                $COL_CUSTOM_NAME TEXT,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'Uncategorized'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_HOURLY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DATE TEXT NOT NULL,
                $COL_HOUR INTEGER NOT NULL,
                $COL_MINUTES INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_DATE, $COL_HOUR) ON CONFLICT REPLACE
            )
        """.trimIndent())

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
            db.execSQL("""CREATE TABLE IF NOT EXISTS $TABLE_HOURLY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DATE TEXT NOT NULL,
                $COL_HOUR INTEGER NOT NULL,
                $COL_MINUTES INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_DATE, $COL_HOUR) ON CONFLICT REPLACE
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS $TABLE_CUSTOM_CATEGORIES (
                $COL_CAT_NAME TEXT PRIMARY KEY
            )""")
        }
        // v4: clear corrupted data from old engine. Meta preserved.
        if (oldVersion < 4) {
            try { db.execSQL("DELETE FROM $TABLE_USAGE") } catch (e: Exception) {}
            try { db.execSQL("DELETE FROM $TABLE_HOURLY") } catch (e: Exception) {}
        }
        // v5: clear again — tracking engine was still wrong in v4 build.
        if (oldVersion < 5) {
            try { db.execSQL("DELETE FROM $TABLE_USAGE") } catch (e: Exception) {}
            try { db.execSQL("DELETE FROM $TABLE_HOURLY") } catch (e: Exception) {}
        }
    }


    // ── Usage ──────────────────────────────────────────────
    fun upsertUsage(record: UsageRecord) {
        val values = ContentValues().apply {
            put(COL_PACKAGE, record.packageName)
            put(COL_APP_NAME, record.appName)
            put(COL_DATE, record.date)
            put(COL_MINUTES, record.totalMinutes)
            put(COL_UPDATED, record.lastUpdated)
        }
        writableDatabase.insertWithOnConflict(TABLE_USAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun rowToRecord(c: android.database.Cursor) = UsageRecord(
        id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
        packageName = c.getString(c.getColumnIndexOrThrow(COL_PACKAGE)),
        appName = c.getString(c.getColumnIndexOrThrow(COL_APP_NAME)),
        date = c.getString(c.getColumnIndexOrThrow(COL_DATE)),
        totalMinutes = c.getLong(c.getColumnIndexOrThrow(COL_MINUTES)),
        lastUpdated = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED))
    )

    fun getUsageForDate(date: String): List<UsageRecord> {
        val r = mutableListOf<UsageRecord>()
        val c = readableDatabase.query(TABLE_USAGE, null, "$COL_DATE=?", arrayOf(date), null, null, "$COL_MINUTES DESC")
        c.use { while (it.moveToNext()) r.add(rowToRecord(it)) }
        return r
    }

    fun getUsageForPackage(pkg: String): List<UsageRecord> {
        val r = mutableListOf<UsageRecord>()
        val c = readableDatabase.query(TABLE_USAGE, null, "$COL_PACKAGE=?", arrayOf(pkg), null, null, "$COL_DATE DESC")
        c.use { while (it.moveToNext()) r.add(rowToRecord(it)) }
        return r
    }

    fun getAllRecords(): List<UsageRecord> {
        val r = mutableListOf<UsageRecord>()
        val c = readableDatabase.query(TABLE_USAGE, null, null, null, null, null, "$COL_DATE DESC, $COL_MINUTES DESC")
        c.use { while (it.moveToNext()) r.add(rowToRecord(it)) }
        return r
    }

    fun getAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val c = readableDatabase.rawQuery("SELECT DISTINCT $COL_DATE FROM $TABLE_USAGE ORDER BY $COL_DATE DESC", null)
        c.use { while (it.moveToNext()) dates.add(it.getString(0)) }
        return dates
    }

    fun getTotalMinutesForDate(date: String): Long {
        val c = readableDatabase.rawQuery("SELECT SUM($COL_MINUTES) FROM $TABLE_USAGE WHERE $COL_DATE=?", arrayOf(date))
        return c.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    fun getDailyTotals(startDate: String, endDate: String): List<Pair<String, Long>> {
        val r = mutableListOf<Pair<String, Long>>()
        val c = readableDatabase.rawQuery(
            "SELECT $COL_DATE, SUM($COL_MINUTES) FROM $TABLE_USAGE WHERE $COL_DATE>=? AND $COL_DATE<=? GROUP BY $COL_DATE ORDER BY $COL_DATE ASC",
            arrayOf(startDate, endDate)
        )
        c.use { while (it.moveToNext()) r.add(Pair(it.getString(0), it.getLong(1))) }
        return r
    }

    // ── Hourly ─────────────────────────────────────────────
    fun upsertHourly(date: String, hour: Int, minutes: Long) {
        val values = ContentValues().apply {
            put(COL_DATE, date); put(COL_HOUR, hour); put(COL_MINUTES, minutes)
        }
        writableDatabase.insertWithOnConflict(TABLE_HOURLY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getHourlyForDate(date: String): List<HourlyRecord> {
        val r = mutableListOf<HourlyRecord>()
        val c = readableDatabase.query(TABLE_HOURLY, null, "$COL_DATE=?", arrayOf(date), null, null, "$COL_HOUR ASC")
        c.use { while (it.moveToNext()) r.add(HourlyRecord(it.getInt(it.getColumnIndexOrThrow(COL_HOUR)), it.getLong(it.getColumnIndexOrThrow(COL_MINUTES)))) }
        return r
    }

    fun getAllTimeHourlyTotals(): List<HourlyRecord> {
        val r = mutableListOf<HourlyRecord>()
        val c = readableDatabase.rawQuery("SELECT $COL_HOUR, SUM($COL_MINUTES) FROM $TABLE_HOURLY GROUP BY $COL_HOUR ORDER BY $COL_HOUR ASC", null)
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

    fun getAppMeta(packageName: String): AppMeta {
        val c = readableDatabase.query(TABLE_META, null, "$COL_PACKAGE=?", arrayOf(packageName), null, null, null)
        return c.use {
            if (it.moveToFirst()) AppMeta(packageName, it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)), it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)))
            else AppMeta(packageName, null, "Uncategorized")
        }
    }

    fun getAllMeta(): Map<String, AppMeta> {
        val map = mutableMapOf<String, AppMeta>()
        val c = readableDatabase.query(TABLE_META, null, null, null, null, null, null)
        c.use { while (it.moveToNext()) {
            val pkg = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE))
            map[pkg] = AppMeta(pkg, it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)), it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)))
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

    fun getAllCategories(): List<String> {
        return DEFAULT_CATEGORIES + getCustomCategories()
    }
}
