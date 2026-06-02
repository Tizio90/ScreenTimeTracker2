package com.screentime.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.screentime.tracker.data.UsageRecord
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val records: List<UsageRecord>,
    private val repo: UsageRepository
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.binding.dateText.text = record.date
        holder.binding.timeText.text = repo.formatMinutes(record.totalMinutes)
    }

    override fun getItemCount() = records.size
}
