package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PickerDatesTest {
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
    private val utcNov9 = 1_794_182_400_000L // 2026-11-09T00:00Z, the picker's value for 9 November
    private val day = 86_400_000L

    @Test fun aPickedDayIsStoredAtLocalNoonThatDay() {
        // 2026-11-09 12:00 in Tokyo is 03:00 UTC
        assertEquals(utcNov9 + 3 * 3_600_000L, Dates.noonFromPicker(utcNov9, tokyo))
    }

    @Test fun theLocalDayGoesBackToThePickersValue() {
        val earlyMorningTokyo = utcNov9 - 3_600_000L // 2026-11-09 08:00 in Tokyo, still 8 November in UTC
        assertEquals(utcNov9, Dates.pickerDay(earlyMorningTokyo, tokyo))
        assertEquals(utcNov9, Dates.pickerDay(Dates.noonFromPicker(utcNov9, tokyo), tokyo))
    }

    @Test fun todayIsPickableEarlyInTheMorningButTomorrowIsNot() {
        val earlyMorningTokyo = utcNov9 - 3_600_000L
        assertTrue(Dates.notFuture(utcNov9, earlyMorningTokyo, tokyo))
        assertTrue(Dates.notFuture(utcNov9 - day, earlyMorningTokyo, tokyo))
        assertFalse(Dates.notFuture(utcNov9 + day, earlyMorningTokyo, tokyo))
    }
}
