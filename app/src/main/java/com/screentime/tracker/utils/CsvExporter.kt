package com.screentime.tracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.screentime.tracker.data.UsageRecord
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    fun export(context: Context, records: List<UsageRecord>): Intent? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "screen_time_$timestamp.csv"

            // Write to app's cache dir (no storage permission needed on Android 11+)
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(cacheDir, fileName)

            FileWriter(file).use { writer ->
                writer.appendLine("Date,App Name,Package,Minutes,Hours")
                for (r in records.sortedWith(compareByDescending<UsageRecord> { it.date }.thenByDescending { it.totalMinutes })) {
                    val hours = String.format("%.2f", r.totalMinutes / 60.0)
                    writer.appendLine("${r.date},\"${r.appName}\",${r.packageName},${r.totalMinutes},$hours")
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Screen Time Export - $timestamp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
