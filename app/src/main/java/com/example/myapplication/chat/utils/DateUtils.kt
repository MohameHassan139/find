package com.example.myapplication.chat.utils

import com.example.myapplication.utils.LocaleHelper
import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    fun formatMessageTime(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            )
            var date: Date? = null
            for (fmt in inputFormats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                try { date = fmt.parse(dateString); break } catch (_: Exception) {}
            }
            if (date == null) return LocaleHelper.toEnglishNumbers(dateString)
            val outputFmt = SimpleDateFormat("h:mm a", Locale.US)
            LocaleHelper.toEnglishNumbers(outputFmt.format(date))
        } catch (e: Exception) {
            LocaleHelper.toEnglishNumbers(dateString)
        }
    }

    /** [yesterdayLabel] is caller-supplied (rather than hardcoded here) so this stays a
     * pure, locale-agnostic function callers can pass a localized string into — see
     * R.string.yesterday at each call site. */
    fun formatConversationTime(dateString: String?, yesterdayLabel: String): String {
        if (dateString.isNullOrEmpty()) return ""
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            )
            var date: Date? = null
            for (fmt in inputFormats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                try { date = fmt.parse(dateString); break } catch (_: Exception) {}
            }
            if (date == null) return LocaleHelper.toEnglishNumbers(dateString)
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date
            val text = when {
                isSameDay(calendar, today) -> {
                    val fmt = SimpleDateFormat("h:mm a", Locale.US)
                    fmt.format(date)
                }
                isYesterday(calendar, today) -> yesterdayLabel
                else -> {
                    val fmt = SimpleDateFormat("dd/MM", Locale.US)
                    fmt.format(date)
                }
            }
            LocaleHelper.toEnglishNumbers(text)
        } catch (e: Exception) {
            LocaleHelper.toEnglishNumbers(dateString)
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar) =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(c1: Calendar, today: Calendar): Boolean {
        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(c1, yesterday)
    }

    /**
     * Formats last seen timestamp.
     * Returns Pair(displayText, isOnline).
     */
    fun formatLastSeen(dateString: String?, context: android.content.Context): Pair<String, Boolean> {
        if (dateString.isNullOrEmpty()) {
            return Pair(context.getString(com.example.myapplication.R.string.chat_status_offline), false)
        }
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            )
            var date: Date? = null
            for (fmt in inputFormats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                try { date = fmt.parse(dateString); break } catch (_: Exception) {}
            }
            if (date == null) {
                return Pair(LocaleHelper.toEnglishNumbers(dateString), false)
            }

            val diffMillis = System.currentTimeMillis() - date.time
            // If active within 120 seconds (matching iOS AppPresence.onlineThreshold), show "Online"
            if (diffMillis in -60_000..120_000) {
                return Pair(context.getString(com.example.myapplication.R.string.chat_status_online), true)
            }

            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date

            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedTime = LocaleHelper.toEnglishNumbers(timeFmt.format(date))

            val text = when {
                isSameDay(calendar, today) -> {
                    context.getString(com.example.myapplication.R.string.chat_status_last_seen_today, formattedTime)
                }
                isYesterday(calendar, today) -> {
                    context.getString(com.example.myapplication.R.string.chat_status_last_seen_yesterday, formattedTime)
                }
                else -> {
                    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val formattedDate = LocaleHelper.toEnglishNumbers(dateFmt.format(date))
                    context.getString(com.example.myapplication.R.string.chat_status_last_seen_date, formattedDate)
                }
            }
            Pair(text, false)
        } catch (e: Exception) {
            Pair(LocaleHelper.toEnglishNumbers(dateString), false)
        }
    }
}
