package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.data.OpeningHours.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {
    private fun at(s: String) = LocalDateTime.parse(s)
    private fun status(hours: String, closed: String? = null, time: String) = OpeningHours.statusOf(hours, closed, at(time))

    @Test fun holidays2026() {
        listOf("2026-01-01", "2026-01-12", "2026-03-20", "2026-05-06", "2026-07-20", "2026-09-21", "2026-09-22", "2026-09-23", "2026-10-12", "2026-11-23")
            .forEach { assertTrue(it, JapanHolidays.isHoliday(LocalDate.parse(it))) }
        listOf("2026-05-07", "2026-09-24", "2026-11-16").forEach { assertFalse(it, JapanHolidays.isHoliday(LocalDate.parse(it))) }
    }

    @Test fun mondayOrTuesdayAfterAHolidayMonday() {
        val h = "9:00～16:30（月曜（祝日なら翌日）、年末年始を除く）"
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-16T10:00")) // ordinary Monday
        assertEquals(Status.OpenUntil(LocalTime.of(16, 30)), status(h, time = "2026-11-17T10:00"))
        assertEquals(Status.OpenUntil(LocalTime.of(16, 30)), status(h, time = "2026-10-12T10:00")) // Sports Day Monday
        assertEquals(Status.ClosedToday, status(h, time = "2026-10-13T10:00")) // so Tuesday is closed
        assertEquals(Status.ClosedToday, status(h, time = "2026-12-30T10:00"))
    }

    @Test fun weekdaysOnly() {
        val h = "平日 9:00～17:00"
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-14T10:00"))
        assertEquals(Status.OpensAt(LocalTime.of(9, 0)), status(h, time = "2026-11-13T08:00"))
        assertTrue(status(h, time = "2026-11-13T18:00") is Status.ClosedForToday)
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-23T10:00")) // a holiday is not 平日
    }

    @Test fun winterHoursAndClosedWednesday() {
        val h = "9:00～18:00（休館日：水曜日、12/29～1/3）但し冬季（11/1～3/31）は9時～17時"
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-11T10:00"))
        assertTrue(status(h, time = "2026-11-12T17:30") is Status.ClosedForToday)
        assertEquals(Status.OpenUntil(LocalTime.of(18, 0)), status(h, time = "2026-06-04T17:30"))
    }

    @Test fun dayKindsSecondThursdayAndMonthEnd() {
        val h = "平日・土 9:00～21:00／日祝 9:00～18:00（第2木曜、月末、12/29～1/3は休館）"
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-12T10:00")) // 2nd Thursday
        assertEquals(Status.ClosedToday, status(h, time = "2026-11-30T10:00"))
        assertTrue(status(h, time = "2026-11-15T19:00") is Status.ClosedForToday) // Sunday closes at 18
        assertEquals(Status.OpenUntil(LocalTime.of(21, 0)), status(h, time = "2026-11-14T19:00"))
    }

    @Test fun closedDaysOnlyAndWeekendText() {
        assertEquals(Status.OpenToday, status("", "（土日祝日及び年末年始は配布していません）", "2026-11-13T10:00"))
        assertEquals(Status.ClosedToday, status("", "（土日祝日及び年末年始は配布していません）", "2026-11-14T10:00"))
        assertEquals(Status.OpenUntil(LocalTime.of(17, 0)), status("8:30～17:00 （土日祝日を含む）", time = "2026-11-14T10:00"))
    }

    @Test fun moreHolidayWordingsAndMonthSeasons() {
        val nextWeekday = "11:00～17:30（月曜（祝日なら翌平日）を除く）"
        assertEquals(Status.ClosedToday, status(nextWeekday, time = "2026-10-13T12:00"))
        val openOnHoliday = "9:30～15:30（月曜（祝日は配布）、年末年始を除く。不定休あり）"
        assertEquals(Status.ClosedToday, status(openOnHoliday, time = "2026-11-16T10:00"))
        assertTrue(OpeningHours.parse(openOnHoliday, null)!!.approximate)
        assertEquals(Status.OpenUntil(LocalTime.of(15, 30)), status(openOnHoliday, time = "2026-10-12T10:00"))
        val lastMonday = "9:00～18:00（毎月最終月曜、12/31～1/1、臨時休館日を除く）"
        assertEquals(Status.ClosedToday, status(lastMonday, time = "2026-11-30T10:00"))
        assertEquals(Status.OpenUntil(LocalTime.of(18, 0)), status(lastMonday, time = "2026-11-23T10:00"))
        val months = "4～11月 9:00～18:00／12～3月 9:00～17:00（火曜は休み）"
        assertEquals(Status.ClosedToday, status(months, time = "2026-11-17T10:00")) // Tuesday
        // November is in 4~11月 (until 18:00); winter hours (12~3月) end at 17:00
        assertEquals(Status.OpenUntil(LocalTime.of(18, 0)), status(months, time = "2026-11-18T17:30"))
        assertTrue(status(months, time = "2026-12-02T17:30") is Status.ClosedForToday)
        assertEquals(Status.OpenUntil(LocalTime.of(18, 0)), status(months, time = "2026-06-03T17:30"))
        assertTrue(status("3～10月 9:00～16:45／11～2月 9:00～16:00（火曜、12/29～1/3を除く）", time = "2026-11-12T16:30") is Status.ClosedForToday)
    }

    @Test fun unknownWordsGiveNoStatus() {
        assertNull(OpeningHours.parse("①9:00～17:00; ②10:00～16:00", null))
        assertNull(OpeningHours.parse("9:00～17:00（GW期間は平日も配布）", null))
        assertNull(OpeningHours.parse("9:00～17:00（土曜、日曜以外の祝日を除く）", null))
        assertNull(OpeningHours.parse(null, null))
    }

    /** How much of the bundled catalogue gets a status; printed so a change in coverage shows in the test log. */
    @Test fun catalogueCoverage() {
        val csv = File("src/main/assets/catalogue/trip-niigata.csv").takeIf { it.exists() } ?: return
        val rows = Csv.parse(csv.readText()).drop(1)
        val header = Csv.parse(csv.readText()).first()
        val hi = header.indexOf("hours_text"); val ci = header.indexOf("closed_days")
        val withText = rows.filter { it.getOrNull(hi).orEmpty().isNotBlank() || it.getOrNull(ci).orEmpty().isNotBlank() }
        val read = withText.count { OpeningHours.parse(it.getOrNull(hi), it.getOrNull(ci)) != null }
        println("OpeningHours coverage: $read of ${withText.size} rows with hours text")
        withText.filter { OpeningHours.parse(it.getOrNull(hi), it.getOrNull(ci)) == null }
            .take(40).forEach { println("  not read: ${it.getOrNull(hi)} | ${it.getOrNull(ci)}") }
        assertTrue(read > 0)
    }
}
