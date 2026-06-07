package com.screentime.tracker.ui

import android.graphics.Color
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

        // Distinct colors for app initials
        private val ICON_COLORS = listOf(
            "#5C6BC0", "#EF5350", "#26A69A", "#FFA726",
            "#AB47BC", "#29B6F6", "#66BB6A", "#FF7043",
            "#EC407A", "#8D6E63", "#78909C", "#42A5F5"
        )
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
        val categoryStr = if (meta.category != "Uncategorized") "${meta.category}  •  " else ""

        // Icon: first letter of display name, colored by hash
        val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val colorHex = ICON_COLORS[Math.abs(record.packageName.hashCode()) % ICON_COLORS.size]

        holder.binding.apply {
            appInitial.text = initial
            appInitial.setBackgroundColor(Color.parseColor(colorHex))
            appNameText.text = displayName
            packageText.text = "$categoryStr$sessions"
            timeText.text = repo.formatMinutes(record.totalMinutes)
            sessionCountText.text = if (record.sessionCount > 0) "$sessions" else ""
            usageBar.progress = ((record.totalMinutes * 100) / maxMinutes).toInt()
            root.setOnClickListener { onClick(record) }
        }
    }
}
