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
    /**
     * The phone's 12/24-hour setting, read when the app starts and each time it comes back (#145: Timeline said 20:23
     * and the item page 8:23 PM for the same find). Every time on screen goes through [timePattern].
     */
    @Volatile var use24 = true
    fun readClock(context: Context) { use24 = android.text.format.DateFormat.is24HourFormat(context) }

    /** "14:06", or "2:06 PM" / "午後2:06" when the phone is set to 12-hour time. */
    fun timePattern(locale: Locale = Locale.getDefault()): String = when {
        use24 -> "HH:mm"
        locale.language == "ja" -> "ah:mm"
        else -> "h:mm a"
    }
    fun timeFormat(locale: Locale = Locale.getDefault()) = java.text.SimpleDateFormat(timePattern(locale), locale)
    fun time(ms: Long, locale: Locale = Locale.getDefault()): String = timeFormat(locale).format(Date(ms))

    fun day(ms: Long, locale: Locale): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(ms))
    fun dayTime(ms: Long, locale: Locale): String =
        day(ms, locale) + (if (locale.language == "ja") " " else ", ") + time(ms, locale)
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

/** A day without its year when it is this year ("Nov 21", "11月21日"), for the Trip report's day chips (#147). */
@Composable
fun shortDateText(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val year = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) }
    val skeleton = if (year(ms) == year(System.currentTimeMillis())) "MMMd" else "yMMMd"
    return java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(ms))
}
