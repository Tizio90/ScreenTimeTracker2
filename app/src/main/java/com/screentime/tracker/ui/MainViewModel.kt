package com.screentime.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.screentime.tracker.data.UsageRecord
import com.screentime.tracker.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = UsageRepository(app)

    val usageList = MutableLiveData<List<UsageRecord>>()
    val selectedDate = MutableLiveData<String>()
    val totalMinutes = MutableLiveData<Long>()
    val availableDates = MutableLiveData<List<String>>()
    val weeklyData = MutableLiveData<List<Pair<String, Long>>>()
    val monthlyData = MutableLiveData<List<Pair<String, Long>>>()
    val categoryData = MutableLiveData<Map<String, Long>>()

    init { loadToday() }

    fun loadToday() = loadDate(repo.getTodayDate())

    fun loadDate(date: String) {
        selectedDate.value = date
        viewModelScope.launch(Dispatchers.IO) {
            usageList.postValue(repo.getUsageForDate(date))
            totalMinutes.postValue(repo.getTotalMinutesForDate(date))
            categoryData.postValue(repo.getCategoryTotalsForDate(date))
        }
    }

    fun loadAvailableDates() {
        viewModelScope.launch(Dispatchers.IO) {
            availableDates.postValue(repo.getAvailableDates())
        }
    }

    fun loadWeeklyData() {
        viewModelScope.launch(Dispatchers.IO) {
            weeklyData.postValue(repo.getWeeklyData())
        }
    }

    fun loadMonthlyData() {
        viewModelScope.launch(Dispatchers.IO) {
            monthlyData.postValue(repo.getMonthlyData())
        }
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.collectAndSaveToday()
            val date = selectedDate.value ?: repo.getTodayDate()
            usageList.postValue(repo.getUsageForDate(date))
            totalMinutes.postValue(repo.getTotalMinutesForDate(date))
            categoryData.postValue(repo.getCategoryTotalsForDate(date))
        }
    }

    fun formatMinutes(minutes: Long) = repo.formatMinutes(minutes)
}
