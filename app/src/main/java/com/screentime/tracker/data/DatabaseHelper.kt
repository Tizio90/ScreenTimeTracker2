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

data class AppMeta(
    val packageName: String,
    val customName: String?,
    val category: String
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "screen_time.db"
        const val DATABASE_VERSION = 2

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

        val CATEGORIES = listOf(
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_META (
                    $COL_PACKAGE TEXT PRIMARY KEY,
                    $COL_CUSTOM_NAME TEXT,
                    $COL_CATEGORY TEXT NOT NULL DEFAULT 'Uncategorized'
                )
            """.trimIndent())
        }
    }

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

    fun saveAppMeta(packageName: String, customName: String?, category: String) {
        val values = ContentValues().apply {
            put(COL_PACKAGE, packageName)
            put(COL_CUSTOM_NAME, customName?.takeIf { it.isNotBlank() })
            put(COL_CATEGORY, category)
        }
        writableDatabase.insertWithOnConflict(TABLE_META, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAppMeta(packageName: String): AppMeta {
        val cursor = readableDatabase.query(
            TABLE_META, null, "$COL_PACKAGE = ?", arrayOf(packageName),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                AppMeta(
                    packageName = packageName,
                    customName = it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)),
                    category = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY))
                )
            } else AppMeta(packageName, null, "Uncategorized")
        }
    }

    fun getAllMeta(): Map<String, AppMeta> {
        val map = mutableMapOf<String, AppMeta>()
        val cursor = readableDatabase.query(TABLE_META, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val pkg = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE))
                map[pkg] = AppMeta(
                    packageName = pkg,
                    customName = it.getString(it.getColumnIndexOrThrow(COL_CUSTOM_NAME)),
                    category = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY))
                )
            }
        }
        return map
    }

    private fun rowToRecord(cursor: android.database.Cursor) = UsageRecord(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        packageName = cursor.getString(cursor.getColumnIndexOrThrow(COL_PACKAGE)),
        appName = cursor.getString(cursor.getColumnIndexOrThrow(COL_APP_NAME)),
        date = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE)),
        totalMinutes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MINUTES)),
        lastUpdated = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED))
    )

    fun getUsageForDate(date: String): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val cursor = readableDatabase.query(
            TABLE_USAGE, null, "$COL_DATE = ?", arrayOf(date),
            null, null, "$COL_MINUTES DESC"
        )
        cursor.use { while (it.moveToNext()) records.add(rowToRecord(it)) }
        return records
    }

    fun getUsageForPackage(packageName: String): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val cursor = readableDatabase.query(
            TABLE_USAGE, null, "$COL_PACKAGE = ?", arrayOf(packageName),
            null, null, "$COL_DATE DESC"
        )
        cursor.use { while (it.moveToNext()) records.add(rowToRecord(it)) }
        return records
    }

    fun getAllRecords(): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val cursor = readableDatabase.query(
            TABLE_USAGE, null, null, null,
            null, null, "$COL_DATE DESC, $COL_MINUTES DESC"
        )
        cursor.use { while (it.moveToNext()) records.add(rowToRecord(it)) }
        return records
    }

    fun getUsageForDateRange(startDate: String, endDate: String): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val cursor = readableDatabase.query(
            TABLE_USAGE, null,
            "$COL_DATE >= ? AND $COL_DATE <= ?", arrayOf(startDate, endDate),
            null, null, "$COL_DATE ASC, $COL_MINUTES DESC"
        )
        cursor.use { while (it.moveToNext()) records.add(rowToRecord(it)) }
        return records
    }

    fun getDailyTotals(startDate: String, endDate: String): List<Pair<String, Long>> {
        val results = mutableListOf<Pair<String, Long>>()
        val cursor = readableDatabase.rawQuery(
            "SELECT $COL_DATE, SUM($COL_MINUTES) FROM $TABLE_USAGE WHERE $COL_DATE >= ? AND $COL_DATE <= ? GROUP BY $COL_DATE ORDER BY $COL_DATE ASC",
            arrayOf(startDate, endDate)
        )
        cursor.use { while (it.moveToNext()) results.add(Pair(it.getString(0), it.getLong(1))) }
        return results
    }

    fun getAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT $COL_DATE FROM $TABLE_USAGE ORDER BY $COL_DATE DESC", null
        )
        cursor.use { while (it.moveToNext()) dates.add(it.getString(0)) }
        return dates
    }

    fun getTotalMinutesForDate(date: String): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM($COL_MINUTES) FROM $TABLE_USAGE WHERE $COL_DATE = ?", arrayOf(date)
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }
}
