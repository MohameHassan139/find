package com.example.myapplication.crash

import com.example.myapplication.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin wrapper around FirebaseCrashlytics. Every call is defensive: Crashlytics isn't
 * initialized until google-services.json is added (see app/build.gradle.kts), and a
 * missing/misconfigured Firebase project should never be what crashes the app.
 */
object CrashReporting {

    /** Call once from App.onCreate(). Reports in release builds only, so local dev
     * crashes don't pollute the dashboard. */
    fun init() {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
    }

    /** Tags subsequent crash reports with the logged-in user, so a report in the
     * Crashlytics dashboard can be traced back to an account. Call on login. */
    fun setUserId(userId: String) {
        runCatching { FirebaseCrashlytics.getInstance().setUserId(userId) }
    }

    /** Call on logout so reports after this point aren't attributed to the old user. */
    fun clearUserId() {
        runCatching { FirebaseCrashlytics.getInstance().setUserId("") }
    }

    /** Breadcrumb log line, bundled into the next crash/exception report from this session. */
    fun log(message: String) {
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    /** Reports a caught (non-fatal) exception without crashing the app. */
    fun recordException(throwable: Throwable) {
        runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
    }
}