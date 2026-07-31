package com.example.myapplication

import android.app.Application
import com.example.myapplication.SettingsActivity
import com.example.myapplication.crash.CrashReporting
import com.example.myapplication.push.FcmService

class App : Application() {

    companion object {
        // Process-wide context for places that build a Retrofit client
        // without an Activity/ViewModel Context on hand (e.g. RetrofitClient's
        // no-token singleton's Accept-Language interceptor).
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Apply saved theme before any activity is created
        SettingsActivity.applyTheme(this)
        FcmService.ensureChannel(this)
        CrashReporting.init()
    }
}
