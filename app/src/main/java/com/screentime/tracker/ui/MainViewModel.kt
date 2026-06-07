package com.screentime.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.screentime.tracker.data.DailyRecord
import com.screentime.tracker.data.HourlyRecord
import com.screentime.tracker.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = UsageRepository(app)

    val dailyList    = MutableLiveData<List<DailyRecord>>(emptyList())
    val selectedDate = MutableLiveData<String>()
    val totalMinutes = MutableLiveData<Long>(0L)
    val availableDates = MutableLiveData<List<String>>(emptyList())
    val categoryData = MutableLiveData<Map<String, Long>>(emptyMap())
    val hourlyData   = MutableLiveData<List<HourlyRecord>>(emptyList())
    val peakHour     = MutableLiveData<Int?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val today = repo.getTodayDate()
            selectedDate.postValue(today)
            loadDateInternal(today)
        }
    }

    fun loadToday() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = repo.getTodayDate()
            selectedDate.postValue(today)
            loadDateInternal(today)
        }
    }

    fun loadDate(date: String) {
        selectedDate.value = date
        viewModelScope.launch(Dispatchers.IO) { loadDateInternal(date) }
    }

    fun loadAvailableDates() {
        viewModelScope.launch(Dispatchers.IO) {
            availableDates.postValue(repo.getAvailableDates())
        }
    }

    fun refreshNow() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.collectAndSaveToday()
            val date = selectedDate.value ?: repo.getTodayDate()
            selectedDate.postValue(date)
            loadDateInternal(date)
        }
    }

    private suspend fun loadDateInternal(date: String) {
        dailyList.postValue(repo.getDailyRecords(date))
        totalMinutes.postValue(repo.getTotalMinutesForDate(date))
        categoryData.postValue(repo.getCategoryTotalsForDate(date))
        hourlyData.postValue(repo.getHourlyForDate(date))
        peakHour.postValue(repo.getPeakHour(date))
    }

    fun formatMinutes(minutes: Long) = repo.formatMinutes(minutes)
}
