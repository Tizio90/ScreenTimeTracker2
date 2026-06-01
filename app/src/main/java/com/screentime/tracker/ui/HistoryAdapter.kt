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

    inner class ViewHolder(val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.binding.apply {
            dateText.text = record.date
            timeText.text = repo.formatMinutes(record.totalMinutes)
        }
    }

    override fun getItemCount() = records.size
}
