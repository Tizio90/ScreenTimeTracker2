package com.screentime.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.screentime.tracker.data.DailyRecord
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ItemUsageBinding

class UsageAdapter(
    private val repo: UsageRepository,
    private val onClick: (DailyRecord) -> Unit
) : ListAdapter<DailyRecord, UsageAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DailyRecord>() {
            override fun areItemsTheSame(a: DailyRecord, b: DailyRecord) =
                a.packageName == b.packageName && a.date == b.date
            override fun areContentsTheSame(a: DailyRecord, b: DailyRecord) =
                a.totalMinutes == b.totalMinutes && a.sessionCount == b.sessionCount
        }
    }

    inner class ViewHolder(val binding: ItemUsageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val maxMinutes = getItem(0).totalMinutes.coerceAtLeast(1)
        val meta = repo.getAppMeta(record.packageName)
        val displayName = meta.customName?.takeIf { it.isNotBlank() } ?: record.appName
        val sessions = if (record.sessionCount == 1) "1 session" else "${record.sessionCount} sessions"
        val categoryStr = if (meta.category != "Uncategorized") "📁 ${meta.category}  •  " else ""

        holder.binding.apply {
            appNameText.text = displayName
            packageText.text = "$categoryStr$sessions"
            timeText.text = repo.formatMinutes(record.totalMinutes)
            usageBar.progress = ((record.totalMinutes * 100) / maxMinutes).toInt()
            root.setOnClickListener { onClick(record) }
        }
    }
}
