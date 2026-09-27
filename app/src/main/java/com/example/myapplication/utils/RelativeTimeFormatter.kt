package com.example.myapplication.utils

import android.content.Context
import com.example.myapplication.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * "Just now / 5 minutes / 3 hours / 2 days", then a dd/MM/yyyy date after 30 days.
 * Text comes from string resources, so it follows the app language (the ad cards used
 * to hard-code their own Arabic/English words, and My Ads was Arabic-only).
 */
object RelativeTimeFormatter {

    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val MONTH = 30 * DAY

    // API timestamps look like 2026-09-27T06:10:00.000000Z; the first 19 chars are enough.
    // Locale.US so parsing never depends on the device's digit system.
    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    @Synchronized
    fun format(context: Context, isoDate: String?): String {
        if (isoDate.isNullOrEmpty()) return ""
        val date = runCatching { isoParser.parse(isoDate.take(19)) }.getOrNull() ?: return ""
        val seconds = (System.currentTimeMillis() - date.time) / 1000
        return when {
            seconds < MINUTE -> context.getString(R.string.time_now)
            seconds < HOUR -> context.getString(R.string.time_minutes, seconds / MINUTE)
            seconds < DAY -> context.getString(R.string.time_hours, seconds / HOUR)
            seconds < MONTH -> context.getString(R.string.time_days, seconds / DAY)
            else -> dateFormat.format(date)
        }
    }
}
