package com.example.myapplication.chat.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DateUtilsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    /**
     * Builds a UTC-wall-clock timestamp string (the format DateUtils always parses input
     * as) that represents [daysAgo] days before now *in the JVM's default timezone* — the
     * same timezone DateUtils itself uses when comparing against "today". Because an
     * absolute instant round-trips correctly through any timezone, this makes the tests
     * pass regardless of what timezone the machine running them is set to.
     */
    private fun timestamp(daysAgo: Int, hour: Int, minute: Int): Calendar {
        val target = Calendar.getInstance()
        target.add(Calendar.DAY_OF_YEAR, -daysAgo)
        target.set(Calendar.HOUR_OF_DAY, hour)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)
        return target
    }

    private fun asUtcString(calendar: Calendar): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(calendar.time)
    }

    // ── formatMessageTime ───────────────────────────────────────────────────

    @Test
    fun formatMessageTime_nullInput_returnsEmptyString() {
        assertEquals("", DateUtils.formatMessageTime(null))
    }

    @Test
    fun formatMessageTime_emptyInput_returnsEmptyString() {
        assertEquals("", DateUtils.formatMessageTime(""))
    }

    @Test
    fun formatMessageTime_unparseableInput_returnsOriginalString() {
        val garbage = "not-a-date"
        assertEquals(garbage, DateUtils.formatMessageTime(garbage))
    }

    @Test
    fun formatMessageTime_pmTime_hasNoStrayLocaleMarker() {
        // Regression test: this used to hardcode an Arabic "م" prefix on every
        // result regardless of locale or AM/PM, e.g. "م 3:30 PM".
        val result = DateUtils.formatMessageTime(asUtcString(timestamp(daysAgo = 0, hour = 15, minute = 30)))
        assertEquals("3:30 PM", result)
        assertFalse("must not contain the old hardcoded Arabic marker", result.contains("م"))
    }

    @Test
    fun formatMessageTime_amTime_formatsCorrectly() {
        val result = DateUtils.formatMessageTime(asUtcString(timestamp(daysAgo = 0, hour = 9, minute = 5)))
        assertEquals("9:05 AM", result)
    }

    // ── formatConversationTime ──────────────────────────────────────────────

    @Test
    fun formatConversationTime_nullInput_returnsEmptyString() {
        assertEquals("", DateUtils.formatConversationTime(null, "Yesterday"))
    }

    @Test
    fun formatConversationTime_today_showsTime() {
        val result = DateUtils.formatConversationTime(
            asUtcString(timestamp(daysAgo = 0, hour = 14, minute = 0)),
            "Yesterday"
        )
        assertEquals("2:00 PM", result)
    }

    @Test
    fun formatConversationTime_yesterday_showsSuppliedLabel() {
        // Regression test: this used to always return the hardcoded string "الأحد"
        // (Arabic for "Sunday") regardless of what day it actually was.
        val result = DateUtils.formatConversationTime(
            asUtcString(timestamp(daysAgo = 1, hour = 10, minute = 0)),
            "Yesterday"
        )
        assertEquals("Yesterday", result)
    }

    @Test
    fun formatConversationTime_olderThanYesterday_showsDateAsDdSlashMm() {
        val target = timestamp(daysAgo = 5, hour = 10, minute = 0)
        val expected = SimpleDateFormat("dd/MM", Locale.US).format(target.time)

        val result = DateUtils.formatConversationTime(asUtcString(target), "Yesterday")
        assertEquals(expected, result)
    }
}