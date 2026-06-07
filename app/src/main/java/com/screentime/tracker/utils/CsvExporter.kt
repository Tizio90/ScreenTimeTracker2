package com.screentime.tracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.screentime.tracker.data.SessionRecord
import com.screentime.tracker.data.UsageRepository
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    fun export(context: Context, sessions: List<SessionRecord>, repo: UsageRepository): Intent? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val allMeta = repo.getAllMeta()
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "screen_time_$timestamp.csv")

            FileWriter(file).use { w ->
                // Header
                w.appendLine(
                    "Session ID," +
                    "Date," +
                    "Display Name," +
                    "Original App Name," +
                    "Package," +
                    "Category," +
                    "Start Time," +
                    "End Time," +
                    "Start Epoch (ms)," +
                    "End Epoch (ms)," +
                    "Duration (minutes)," +
                    "Duration (hh:mm)"
                )

                // One row per session, sorted by date + start time
                val sorted = sessions.sortedWith(
                    compareBy({ it.date }, { it.startEpoch })
                )

                for (s in sorted) {
                    val meta = allMeta[s.packageName]
                    val displayName = meta?.customName?.takeIf { it.isNotBlank() } ?: s.appName
                    val category = meta?.category ?: "Uncategorized"
                    val hhmm = "%02d:%02d".format(s.durationMinutes / 60, s.durationMinutes % 60)

                    w.appendLine(
                        "${s.id}," +
                        "${s.date}," +
                        "\"$displayName\"," +
                        "\"${s.appName}\"," +
                        "${s.packageName}," +
                        "\"$category\"," +
                        "${s.startTime}," +
                        "${s.endTime}," +
                        "${s.startEpoch}," +
                        "${s.endEpoch}," +
                        "${s.durationMinutes}," +
                        "$hhmm"
                    )
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Screen Time Sessions Export — $timestamp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
