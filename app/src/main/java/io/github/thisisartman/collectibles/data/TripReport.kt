package io.github.thisisartman.collectibles.data

import java.util.Calendar
import java.util.TimeZone

/** One find on the report, with its item and, for your own collections, the collection's name. */
data class ReportRow(val find: Acquisition, val item: Item, val collection: String? = null)

/**
 * The trip report (#61): a day's finds, as the owner defined a trip (one day). An image for stories (portrait 9:16)
 * and a CSV; no PDF (owner, 2026-11-07). Every find has a date (#194), so everything collected that day is on it.
 */
object TripReport {
    /** Local midnight of the day [ms] falls on. */
    fun dayStart(ms: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextDay(start: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** Days with finds, newest first, with how many finds each. */
    fun days(times: List<Long>, tz: TimeZone = TimeZone.getDefault()): List<Pair<Long, Int>> =
        times.groupingBy { dayStart(it, tz) }.eachCount().toList().sortedByDescending { it.first }

    /**
     * Whether a find's time of day is real. "Already went" marks a past trip by its day only (stored at noon), so its
     * time is not shown (review 2026-11-07: every such find read "12:00").
     */
    fun hasTime(f: Acquisition): Boolean = f.via != Via.BULK

    /** How many of each kind, most first (for the image's summary line). */
    fun byKind(rows: List<ReportRow>): List<Pair<String, Int>> =
        rows.groupBy { it.collection ?: it.item.type.name }.map { (k, l) -> k to l.sumOf { it.find.quantity } }.sortedByDescending { it.second }

    private val CSV_HEADER = listOf("date", "time", "type", "collection", "name_ja", "name_romaji", "code", "prefecture", "quantity", "cost_yen", "lat", "lng")

    /** A CSV for a spreadsheet: one line per find, dates and times in the phone's time zone. */
    fun csv(rows: List<ReportRow>, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append(Csv.line(*CSV_HEADER.toTypedArray()))
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply { timeZone = tz }
        rows.forEach { r ->
            val at = r.find.acquiredAt?.let { java.util.Date(it) }
            append(Csv.line(
                at?.let(date::format), at?.takeIf { hasTime(r.find) }?.let(time::format), r.item.type.name, r.collection,
                r.item.nameJa, r.item.nameRomaji, r.item.code, r.item.prefecture,
                r.find.quantity, r.find.costYen, r.find.lat ?: r.item.lat, r.find.lng ?: r.item.lng,
            ))
        }
    }
}
