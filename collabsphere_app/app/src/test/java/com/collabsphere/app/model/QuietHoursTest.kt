package com.collabsphere.app.model

import com.collabsphere.app.view.components.formatMinuteOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun disabledIsNeverQuiet() {
        assertFalse(QuietHours(enabled = false, startMinute = 0, endMinute = 1439).isQuietAt(600))
    }

    @Test
    fun sameDayWindowIsHalfOpen() {
        val hours = QuietHours(enabled = true, startMinute = 13 * 60, endMinute = 14 * 60)
        assertTrue(hours.isQuietAt(13 * 60))
        assertTrue(hours.isQuietAt(13 * 60 + 59))
        assertFalse(hours.isQuietAt(14 * 60))
        assertFalse(hours.isQuietAt(12 * 60 + 59))
    }

    @Test
    fun overnightWindowWrapsPastMidnight() {
        val hours = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
        assertTrue(hours.isQuietAt(23 * 60))
        assertTrue(hours.isQuietAt(0))
        assertTrue(hours.isQuietAt(6 * 60 + 59))
        assertFalse(hours.isQuietAt(7 * 60))
        assertFalse(hours.isQuietAt(12 * 60))
    }

    @Test
    fun equalStartAndEndIsNeverQuiet() {
        assertFalse(QuietHours(enabled = true, startMinute = 600, endMinute = 600).isQuietAt(600))
    }

    @Test
    fun formatsTwelveAndTwentyFourHourClocks() {
        assertEquals("22:05", formatMinuteOfDay(22 * 60 + 5, is24Hour = true))
        assertEquals("10:05 PM", formatMinuteOfDay(22 * 60 + 5, is24Hour = false))
        assertEquals("12:00 AM", formatMinuteOfDay(0, is24Hour = false))
        assertEquals("12:30 PM", formatMinuteOfDay(12 * 60 + 30, is24Hour = false))
    }
}
