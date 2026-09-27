package com.example.myapplication

import android.app.Application
import com.example.myapplication.crash.CrashReporting
import com.example.myapplication.push.FcmService
import android.app.Activity
import android.os.Bundle
import java.lang.ref.WeakReference

class App : Application() {

    companion object {
        // Process-wide context for code with no Activity/ViewModel Context on hand
        // (ApiClient's token/Accept-Language interceptors, AppContainer).
        lateinit var instance: App
            private set
    }

    /** The activity currently on screen, e.g. to show the "session expired" dialog. */
    val currentActivity: Activity? get() = resumedActivity?.get()
    private var resumedActivity: WeakReference<Activity>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Figma typography: Inter + Noto Sans Arabic on every screen (see Type.kt)
        FindTypefaceInflater.install(this)
        // Apply saved theme before any activity is created
        SettingsActivity.applyTheme(this)
        FcmService.ensureChannel(this)
        CrashReporting.init()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumedActivity = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) {
                if (resumedActivity?.get() === activity) resumedActivity = null
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
