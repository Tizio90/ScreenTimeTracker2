package com.screentime.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.screentime.tracker.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onDelete: (String) -> Unit
) : ListAdapter<Pair<String, Boolean>, CategoryAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Pair<String, Boolean>>() {
            override fun areItemsTheSame(a: Pair<String, Boolean>, b: Pair<String, Boolean>) = a.first == b.first
            override fun areContentsTheSame(a: Pair<String, Boolean>, b: Pair<String, Boolean>) = a == b
        }
    }

    inner class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, isCustom) = getItem(position)
        holder.binding.apply {
            categoryName.text = name
            categoryType.text = if (isCustom) "Custom" else "Default"
            btnDelete.visibility = if (isCustom) View.VISIBLE else View.INVISIBLE
            btnDelete.setOnClickListener { onDelete(name) }
        }
    }
}
