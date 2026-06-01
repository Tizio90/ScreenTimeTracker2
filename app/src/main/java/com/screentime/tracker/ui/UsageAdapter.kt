package com.screentime.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.screentime.tracker.data.UsageRecord
import com.screentime.tracker.databinding.ItemUsageBinding

class UsageAdapter(
    private val onClick: (UsageRecord) -> Unit
) : ListAdapter<UsageRecord, UsageAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<UsageRecord>() {
            override fun areItemsTheSame(a: UsageRecord, b: UsageRecord) =
                a.packageName == b.packageName && a.date == b.date
            override fun areContentsTheSame(a: UsageRecord, b: UsageRecord) =
                a.totalMinutes == b.totalMinutes
        }
    }

    inner class ViewHolder(val binding: ItemUsageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUsageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val maxMinutes = getItem(0).totalMinutes.coerceAtLeast(1)

        holder.binding.apply {
            appNameText.text = record.appName
            packageText.text = record.packageName
            timeText.text = formatMinutes(record.totalMinutes)
            usageBar.progress = ((record.totalMinutes * 100) / maxMinutes).toInt()
            root.setOnClickListener { onClick(record) }
        }
    }

    private fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
