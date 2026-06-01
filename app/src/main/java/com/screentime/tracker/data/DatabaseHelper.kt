package com.screentime.tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class UsageRecord(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val date: String,        // yyyy-MM-dd
    val totalMinutes: Long,
    val lastUpdated: Long    // epoch ms
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "screen_time.db"
        const val DATABASE_VERSION = 1

        const val TABLE_USAGE = "usage_log"
        const val COL_ID = "_id"
        const val COL_PACKAGE = "package_name"
        const val COL_APP_NAME = "app_name"
        const val COL_DATE = "date"
        const val COL_MINUTES = "total_minutes"
        const val COL_UPDATED = "last_updated"
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
        db.execSQL("CREATE INDEX idx_package ON $TABLE_USAGE ($COL_PACKAGE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USAGE")
        onCreate(db)
    }

    fun upsertUsage(record: UsageRecord) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PACKAGE, record.packageName)
            put(COL_APP_NAME, record.appName)
            put(COL_DATE, record.date)
            put(COL_MINUTES, record.totalMinutes)
            put(COL_UPDATED, record.lastUpdated)
        }
        db.insertWithOnConflict(TABLE_USAGE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUsageForDate(date: String): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USAGE, null,
            "$COL_DATE = ?", arrayOf(date),
            null, null, "$COL_MINUTES DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    UsageRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        packageName = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE)),
                        appName = it.getString(it.getColumnIndexOrThrow(COL_APP_NAME)),
                        date = it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        totalMinutes = it.getLong(it.getColumnIndexOrThrow(COL_MINUTES)),
                        lastUpdated = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED))
                    )
                )
            }
        }
        return records
    }

    fun getUsageForPackage(packageName: String): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USAGE, null,
            "$COL_PACKAGE = ?", arrayOf(packageName),
            null, null, "$COL_DATE DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    UsageRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        packageName = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE)),
                        appName = it.getString(it.getColumnIndexOrThrow(COL_APP_NAME)),
                        date = it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        totalMinutes = it.getLong(it.getColumnIndexOrThrow(COL_MINUTES)),
                        lastUpdated = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED))
                    )
                )
            }
        }
        return records
    }

    fun getAllRecords(): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USAGE, null, null, null,
            null, null, "$COL_DATE DESC, $COL_MINUTES DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    UsageRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        packageName = it.getString(it.getColumnIndexOrThrow(COL_PACKAGE)),
                        appName = it.getString(it.getColumnIndexOrThrow(COL_APP_NAME)),
                        date = it.getString(it.getColumnIndexOrThrow(COL_DATE)),
                        totalMinutes = it.getLong(it.getColumnIndexOrThrow(COL_MINUTES)),
                        lastUpdated = it.getLong(it.getColumnIndexOrThrow(COL_UPDATED))
                    )
                )
            }
        }
        return records
    }

    fun getAvailableDates(): List<String> {
        val dates = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            true, TABLE_USAGE, arrayOf(COL_DATE),
            null, null, COL_DATE, null, "$COL_DATE DESC", null
        )
        cursor.use {
            while (it.moveToNext()) {
                dates.add(it.getString(0))
            }
        }
        return dates
    }

    fun getTotalMinutesForDate(date: String): Long {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_MINUTES) FROM $TABLE_USAGE WHERE $COL_DATE = ?",
            arrayOf(date)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }
}
