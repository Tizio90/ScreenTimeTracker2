package com.screentime.tracker.data

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var dayStartHour: Int
        get() = prefs.getInt("day_start_hour", 0)
        set(value) = prefs.edit().putInt("day_start_hour", value).apply()
}
