package com.example.myapplication.chat.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    fun formatMessageTime(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            )
            var date: Date? = null
            for (fmt in inputFormats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                try { date = fmt.parse(dateString); break } catch (_: Exception) {}
            }
            if (date == null) return dateString
            val outputFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            "م ${outputFmt.format(date)}"
        } catch (e: Exception) {
            dateString
        }
    }

    fun formatConversationTime(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            )
            var date: Date? = null
            for (fmt in inputFormats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                try { date = fmt.parse(dateString); break } catch (_: Exception) {}
            }
            if (date == null) return dateString
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date
            return when {
                isSameDay(calendar, today) -> {
                    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "م ${fmt.format(date)}"
                }
                isYesterday(calendar, today) -> "الأحد"
                else -> {
                    val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())
                    fmt.format(date)
                }
            }
        } catch (e: Exception) {
            dateString
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
}
