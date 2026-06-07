package com.screentime.tracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.screentime.tracker.data.SessionRecord
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ItemSessionBinding

class SessionAdapter(
    private val sessions: List<SessionRecord>,
    private val repo: UsageRepository
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = sessions[position]
        holder.binding.apply {
            sessionDate.text = s.date
            sessionTime.text = "${s.startTime} → ${s.endTime}"
            sessionDuration.text = repo.formatMinutes(s.durationMinutes)
        }
    }

    override fun getItemCount() = sessions.size
}
