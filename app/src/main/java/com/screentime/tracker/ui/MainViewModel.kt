package com.screentime.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.screentime.tracker.data.UsageRecord
import com.screentime.tracker.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = UsageRepository(app)

    private val _usageList = MutableLiveData<List<UsageRecord>>()
    val usageList: LiveData<List<UsageRecord>> = _usageList

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    private val _totalMinutes = MutableLiveData<Long>()
    val totalMinutes: LiveData<Long> = _totalMinutes

    private val _availableDates = MutableLiveData<List<String>>()
    val availableDates: LiveData<List<String>> = _availableDates

    init {
        loadToday()
    }

    fun loadToday() {
        val today = repo.getTodayDate()
        loadDate(today)
    }

    fun loadDate(date: String) {
        _selectedDate.value = date
        viewModelScope.launch(Dispatchers.IO) {
            val records = repo.getUsageForDate(date)
            val total = repo.getTotalMinutesForDate(date)
            _usageList.postValue(records)
            _totalMinutes.postValue(total)
        }
    }

    fun loadAvailableDates() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableDates.postValue(repo.getAvailableDates())
        }
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.collectAndSaveToday()
            val current = _selectedDate.value ?: repo.getTodayDate()
            val records = repo.getUsageForDate(current)
            val total = repo.getTotalMinutesForDate(current)
            _usageList.postValue(records)
            _totalMinutes.postValue(total)
        }
    }

    fun formatMinutes(minutes: Long) = repo.formatMinutes(minutes)
}
