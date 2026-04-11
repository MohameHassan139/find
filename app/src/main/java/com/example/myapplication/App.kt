package com.example.myapplication

import android.app.Application
import com.example.myapplication.SettingsActivity

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme before any activity is created
        SettingsActivity.applyTheme(this)
    }
}
