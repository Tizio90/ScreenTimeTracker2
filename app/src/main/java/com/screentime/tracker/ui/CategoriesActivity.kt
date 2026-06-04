package com.screentime.tracker.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.screentime.tracker.data.DatabaseHelper
import com.screentime.tracker.data.UsageRepository
import com.screentime.tracker.databinding.ActivityCategoriesBinding

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var repo: UsageRepository
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = UsageRepository(this)
        supportActionBar?.apply { title = "Manage Categories"; setDisplayHomeAsUpEnabled(true) }

        adapter = CategoryAdapter(
            onDelete = { name ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Category")
                    .setMessage("Delete \"$name\"? Apps in this category will become Uncategorized.")
                    .setPositiveButton("Delete") { _, _ ->
                        repo.deleteCustomCategory(name)
                        refreshList()
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        )
        binding.recyclerCategories.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategories.adapter = adapter

        binding.btnAddCategory.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                hint = "Category name (e.g. News, Shopping)"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            AlertDialog.Builder(this)
                .setTitle("Add Custom Category")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val name = input.text.toString().trim()
                    when {
                        name.isEmpty() -> Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        name.length > 30 -> Toast.makeText(this, "Name too long", Toast.LENGTH_SHORT).show()
                        DatabaseHelper.DEFAULT_CATEGORIES.contains(name) ->
                            Toast.makeText(this, "That's already a default category", Toast.LENGTH_SHORT).show()
                        else -> {
                            repo.addCustomCategory(name)
                            refreshList()
                            Toast.makeText(this, "\"$name\" added!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }

        refreshList()
    }

    private fun refreshList() {
        val defaults = DatabaseHelper.DEFAULT_CATEGORIES.map { Pair(it, false) }
        val customs = repo.getCustomCategories().map { Pair(it, true) }
        adapter.submitList(defaults + customs)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
