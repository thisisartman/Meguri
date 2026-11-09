package io.github.thisisartman.collectibles.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
}

@Composable
fun dateText(ms: Long): String = Dates.day(ms, LocalConfiguration.current.locales[0])

@Composable
fun dateTimeText(ms: Long): String = Dates.dayTime(ms, LocalConfiguration.current.locales[0])
