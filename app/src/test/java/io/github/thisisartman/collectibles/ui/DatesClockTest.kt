package io.github.thisisartman.collectibles.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** #145: every time on screen follows the phone's 12/24-hour setting. */
class DatesClockTest {
    private val at = Calendar.getInstance().apply { clear(); set(2026, 10, 23, 20, 23) }.timeInMillis

    @After fun reset() { Dates.use24 = true }

    @Test fun twentyFourHour() {
        Dates.use24 = true
        assertEquals("20:23", Dates.time(at, Locale.US))
        assertEquals("20:23", Dates.time(at, Locale.JAPAN))
    }

    @Test fun twelveHour() {
        Dates.use24 = false
        assertEquals("8:23 PM", Dates.time(at, Locale.US))
        assertEquals("午後8:23", Dates.time(at, Locale.JAPAN))
    }

    @Test fun dateAndTimeUseTheSameClock() {
        Dates.use24 = false
        assert(Dates.dayTime(at, Locale.US).endsWith(", 8:23 PM"))
        Dates.use24 = true
        assert(Dates.dayTime(at, Locale.US).endsWith(", 20:23"))
    }
}
