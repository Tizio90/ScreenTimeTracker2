package com.screentime.tracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.screentime.tracker.data.UsageRecord
import com.screentime.tracker.data.UsageRepository
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun export(context: Context, records: List<UsageRecord>, repo: UsageRepository): Intent? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val allMeta = repo.getAllMeta()
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "screen_time_$timestamp.csv")
            FileWriter(file).use { w ->
                w.appendLine("Date,Display Name,Original Name,Package,Category,Minutes,Hours")
                for (r in records) {
                    val meta = allMeta[r.packageName]
                    val displayName = meta?.customName?.takeIf { it.isNotBlank() } ?: r.appName
                    val category = meta?.category ?: "Uncategorized"
                    val hours = String.format("%.2f", r.totalMinutes / 60.0)
                    w.appendLine("${r.date},\"$displayName\",\"${r.appName}\",${r.packageName},\"$category\",${r.totalMinutes},$hours")
                }
            }
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Screen Time Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
