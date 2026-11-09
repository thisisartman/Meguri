package io.github.thisisartman.collectibles.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One date style across the app (review sweep 2026-11-09, finding 14: "Nov 9, 2026", "2026-11-07 02:38" and
 * "7 Nov 02:38" were all in use). The phone's own medium date, plus a short time where a time matters:
 * "9 Nov 2026" / "9 Nov 2026, 14:06" in English, "2026/11/09" / "2026/11/09 14:06" in Japanese.
 */
object Dates {
    fun day(ms: Long, locale: Locale): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))
    fun dayTime(ms: Long, locale: Locale): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(ms))
    fun day(context: Context, ms: Long): String = day(ms, context.resources.configuration.locales[0])
    fun dayTime(context: Context, ms: Long): String = dayTime(ms, context.resources.configuration.locales[0])

    // Material's date picker works in UTC midnights; Meguri stores a day without a time at local noon, so a change of
    // time zone keeps the date (as "Already went" does)

    /** Local noon on the calendar day the date picker returned as [utcMs]. */
    fun noonFromPicker(utcMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance(tz).apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0)
        }.timeInMillis
    }

    /** The date picker's value (UTC midnight) for the local day of [localMs]. */
    fun pickerDay(localMs: Long, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz).apply { timeInMillis = localMs }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Whether a day in the date picker is today or before, by the local calendar (no future dates). */
    fun notFuture(utcMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Boolean = utcMs <= pickerDay(nowMs, tz)
}

@Composable
fun dateText(ms: Long): String = Dates.day(ms, LocalConfiguration.current.locales[0])

@Composable
fun dateTimeText(ms: Long): String = Dates.dayTime(ms, LocalConfiguration.current.locales[0])
