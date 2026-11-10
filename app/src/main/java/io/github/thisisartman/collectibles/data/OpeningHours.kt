package io.github.thisisartman.collectibles.data

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Reads the catalogue's free hours text ("9:00～16:30（月曜（祝日なら翌日）、年末年始を除く）") into rules, so an item can
 * say "Open now, until 16:30" or "Closed today" and Trip can warn (0.5, owner: the Monday-holiday rule).
 *
 * Careful by design: [parse] returns null unless every part of the text was understood. A text with words it does
 * not know (Golden Week, "by appointment", several counters with their own hours) shows as written, with no status,
 * because a wrong "open" sends someone to a closed door.
 */
object OpeningHours {
    enum class DayClass { WEEKDAY, SAT, SUN, HOLIDAY }

    data class Span(val open: LocalTime, val close: LocalTime)

    /** Opening times for some kinds of day; all four kinds when the text gives no day words. */
    data class Times(val days: Set<DayClass>, val spans: List<Span>)

    /** A month-day range that may run over the new year (12/29 to 1/3). */
    data class Period(val fromMonth: Int, val fromDay: Int, val toMonth: Int, val toDay: Int) {
        fun contains(date: LocalDate): Boolean {
            val v = date.monthValue * 100 + date.dayOfMonth
            val a = fromMonth * 100 + fromDay
            val b = toMonth * 100 + toDay
            return if (a <= b) v in a..b else v >= a || v <= b
        }
    }

    sealed interface Closure {
        fun closes(date: LocalDate): Boolean

        data class Weekly(val day: DayOfWeek) : Closure {
            override fun closes(date: LocalDate) = date.dayOfWeek == day
        }

        /** "月曜（祝日なら翌日）": closed that weekday, but when it is a holiday, closed the day after instead. */
        data class WeeklyHolidayNext(val day: DayOfWeek) : Closure {
            override fun closes(date: LocalDate) =
                (date.dayOfWeek == day && !JapanHolidays.isHoliday(date)) ||
                    (date.minusDays(1).dayOfWeek == day && JapanHolidays.isHoliday(date.minusDays(1)) && !JapanHolidays.isHoliday(date))
        }

        /** "月曜（祝日は開館）": closed that weekday unless it is a holiday. */
        data class WeeklyUnlessHoliday(val day: DayOfWeek) : Closure {
            override fun closes(date: LocalDate) = date.dayOfWeek == day && !JapanHolidays.isHoliday(date)
        }

        /** "毎月最終水曜": the last such weekday of the month. */
        data class LastWeekday(val day: DayOfWeek) : Closure {
            override fun closes(date: LocalDate) = date.dayOfWeek == day && date.plusDays(7).month != date.month
        }

        object Holidays : Closure {
            override fun closes(date: LocalDate) = JapanHolidays.isHoliday(date)
        }

        data class Dates(val period: Period) : Closure {
            override fun closes(date: LocalDate) = period.contains(date)
        }

        /** "第2・第4月曜日", optionally "unless that day is a holiday". */
        data class NthWeekday(val weeks: Set<Int>, val day: DayOfWeek, val unlessHoliday: Boolean) : Closure {
            override fun closes(date: LocalDate) =
                date.dayOfWeek == day && (date.dayOfMonth - 1) / 7 + 1 in weeks && !(unlessHoliday && JapanHolidays.isHoliday(date))
        }

        /** "月末": the last day of the month, optionally not when it falls on a weekend or holiday. */
        data class MonthEnd(val unlessDayOff: Boolean) : Closure {
            override fun closes(date: LocalDate) =
                date == date.with(TemporalAdjusters.lastDayOfMonth()) &&
                    !(unlessDayOff && (date.dayOfWeek.value >= 6 || JapanHolidays.isHoliday(date)))
        }
    }

    data class Schedule(
        val times: List<Times>,
        val seasons: List<Pair<Period, List<Span>>>,
        val closures: List<Closure>,
        /** Part of the text is a rough period ("例年11月下旬～6月上旬") or says the days can change. */
        val approximate: Boolean,
    )

    sealed interface Status {
        data class OpenUntil(val close: LocalTime) : Status
        data class OpensAt(val open: LocalTime) : Status
        /** Open on this day, but past the last closing time. */
        data class ClosedForToday(val opened: Span) : Status
        object ClosedToday : Status
        /** Not a closed day, and the text gives no times. */
        object OpenToday : Status
    }

    fun classOf(date: LocalDate): DayClass = when {
        JapanHolidays.isHoliday(date) -> DayClass.HOLIDAY
        date.dayOfWeek == DayOfWeek.SATURDAY -> DayClass.SAT
        date.dayOfWeek == DayOfWeek.SUNDAY -> DayClass.SUN
        else -> DayClass.WEEKDAY
    }

    fun status(schedule: Schedule, at: LocalDateTime): Status {
        val date = at.toLocalDate()
        if (schedule.closures.any { it.closes(date) }) return Status.ClosedToday
        val spans = schedule.seasons.firstOrNull { it.first.contains(date) }?.second
            ?: if (schedule.times.isEmpty()) return Status.OpenToday
            else schedule.times.filter { classOf(date) in it.days }.flatMap { it.spans }.sortedBy { it.open }
        if (spans.isEmpty()) return Status.ClosedToday
        val now = at.toLocalTime()
        for (s in spans) {
            if (now < s.open) return Status.OpensAt(s.open)
            if (now < s.close) return Status.OpenUntil(s.close)
        }
        return Status.ClosedForToday(Span(spans.first().open, spans.last().close))
    }

    /** The status of an item's hours and closed-days text at [at], or null when the text can't be read with confidence. */
    fun statusOf(hoursText: String?, closedDays: String?, at: LocalDateTime): Status? =
        parse(hoursText, closedDays)?.let { status(it, at) }

    private val ALL = DayClass.entries.toSet()
    private const val WD = "月火水木金土日"
    private val dayOf = mapOf(
        '月' to DayOfWeek.MONDAY, '火' to DayOfWeek.TUESDAY, '水' to DayOfWeek.WEDNESDAY, '木' to DayOfWeek.THURSDAY,
        '金' to DayOfWeek.FRIDAY, '土' to DayOfWeek.SATURDAY, '日' to DayOfWeek.SUNDAY,
    )
    private const val T = """(\d{1,2}):(\d{2})\s*~\s*(\d{1,2}):(\d{2})"""
    private const val HOL = """祝(?:祭)?日?"""
    /** Any word for a holiday in "if it is a holiday" phrases. */
    private const val HOLX = """(?:祝休日|休日|祝(?:祭)?日?)"""

    private fun time(h: String, m: String) = LocalTime.of(h.toInt().coerceAtMost(23), if (h.toInt() >= 24) 59 else m.toInt())
    private fun span(g: List<String>, i: Int) = Span(time(g[i], g[i + 1]), time(g[i + 2], g[i + 3]))
    private fun part(word: String) = when (word) { "上" -> 1 to 10; "中" -> 11 to 20; else -> 21 to 31 }

    /** Full-width digits and brackets to plain ones, "9時" to "9:00", and every kind of wave dash to "~". */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[〜～−–—―]"), "~")
            .replace(Regex("""(\d{1,2})時(\d{1,2})分"""), "$1:$2")
            .replace(Regex("""(\d{1,2})時(?!間)"""), "$1:00")
            .replace(Regex("""(\d{1,2}):(\d)(?!\d)"""), "$1:0$2")

    fun parse(hoursText: String?, closedDays: String?): Schedule? {
        val raw = listOfNotNull(hoursText, closedDays).filter { it.isNotBlank() }.joinToString(";")
        if (raw.isBlank()) return null
        // Several counters with their own hours (①…②…): not read, too easy to show the wrong one
        if (Regex("[①-⑨]").findAll(raw).map { it.value }.toSet().size > 1) return null
        var s = normalize(raw.replace(Regex("[①-⑨]"), ""))
        val times = mutableListOf<Times>()
        val seasons = mutableListOf<Pair<Period, List<Span>>>()
        val closures = mutableListOf<Closure>()
        var approximate = false

        fun take(pattern: String, action: (List<String>) -> Unit) {
            s = Regex(pattern).replace(s) { m -> action(m.groupValues); " " }
        }

        // Notes after ※: a rough closed season is kept, anything else (bring a photo of the dam, etc.) is only a note
        s = s.split("※").mapIndexed { i, piece ->
            if (i == 0) piece else {
                Regex("""(\d{1,2})月([上中下])旬~(\d{1,2})月([上中下])旬""").find(piece)?.let { m ->
                    val g = m.groupValues
                    closures += Closure.Dates(Period(g[1].toInt(), part(g[2]).first, g[3].toInt(), part(g[4]).second)); approximate = true
                }
                Regex("[;；]").split(piece, 2).getOrElse(1) { "" }
            }
        }.joinToString(" ")

        take("""年中無休""") {}
        take("""悪天候で臨時休館あり|臨時休館日?(?:あり)?|施設点検日|施設の休館日|店舗の休業日|不定休(?:あり)?|メンテナンス休館日(?:\(年\d+日\))?|臨時休業(?:日|あり)?|営業日・?時間は変わることあり|変わることがあります|変わることあり""") { approximate = true }
        take("""土日$HOL\s*を?含む(?:毎日)?""") {}
        // A season with its own hours: "但し冬季(11/1~3/31)は9:00~17:00"
        take("""(?:但し)?(?:冬季|冬期|夏季|夏期)?\(?(\d{1,2})/(\d{1,2})~(\d{1,2})/(\d{1,2})\)?は$T""") { g ->
            seasons += Period(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt()) to listOf(span(g, 5))
        }
        take("""(?:冬期|冬季)休館期間:?\(?(\d{1,2})月(\d{1,2})日~(\d{1,2})月(\d{1,2})日\)?""") { g ->
            closures += Closure.Dates(Period(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt()))
        }
        take("""(?:例年|冬期休館期間:?)?(\d{1,2})月([上中下])旬~(\d{1,2})月([上中下])旬""") { g ->
            closures += Closure.Dates(Period(g[1].toInt(), part(g[2]).first, g[3].toInt(), part(g[4]).second)); approximate = true
        }
        // Seasons by month with their own hours: "4~11月 9:00~18:00/12~3月 9:00~17:00"
        take("""(\d{1,2})~(\d{1,2})月\s*$T""") { g ->
            seasons += Period(g[1].toInt(), 1, g[2].toInt(), 31) to listOf(span(g, 3))
        }
        // Times, each with the kinds of day written before it ("平日・土 9:00~21:00/日祝 9:00~18:00")
        take("""(?:\(?((?:平日|[土日]|$HOL|曜|[・、/\s])+)\)?\s*)?$T(?:\s*[、,]\s*$T)?""") { g ->
            val words = g[1].replace("曜", "").replace(Regex(HOL), "祝")
            val days = buildSet {
                if ("平日" in words) add(DayClass.WEEKDAY)
                if ('土' in words) add(DayClass.SAT)
                if ('日' in words.replace("平日", "")) add(DayClass.SUN)
                if ('祝' in words) add(DayClass.HOLIDAY)
            }.ifEmpty { ALL }
            times += Times(days, listOfNotNull(span(g, 2), if (g[6].isNotEmpty()) span(g, 6) else null))
        }
        take("""(?:毎週)?([$WD])曜日?\s*\((?:\1曜日?が)?(?:国民の)?$HOLX(?:の場合)?(?:は|なら)(?:その後の平日|翌平日|翌日|[$WD]曜日?)(?:休館)?\)""") { g ->
            closures += Closure.WeeklyHolidayNext(dayOf.getValue(g[1][0]))
        }
        take("""(?:毎週)?([$WD])曜日?\s*\($HOLX(?:は|の場合は)(?:開館|配布|営業)\)""") { g ->
            closures += Closure.WeeklyUnlessHoliday(dayOf.getValue(g[1][0]))
        }
        take("""毎月最終([$WD])曜日?""") { g -> closures += Closure.LastWeekday(dayOf.getValue(g[1][0])) }
        // "第3火曜(祝日なら翌平日)": the moved day isn't kept, so it is flagged as may vary
        take("""第(\d)([$WD])曜日?\s*\($HOLX(?:なら|の場合は)(?:翌平日|翌日)\)""") { g ->
            closures += Closure.NthWeekday(setOf(g[1].toInt()), dayOf.getValue(g[2][0]), true); approximate = true
        }
        take("""第(\d)((?:・第\d)*)([$WD])曜日?(\(この日が(?:国民の)?祝日の場合を除く\))?""") { g ->
            val weeks = (listOf(g[1]) + Regex("""\d""").findAll(g[2]).map { it.value }).map { it.toInt() }.toSet()
            closures += Closure.NthWeekday(weeks, dayOf.getValue(g[3][0]), g[4].isNotEmpty())
        }
        take("""毎?月末日?(\(この日が、?土曜日・日曜日・国民の祝日の場合を除く\))?""") { g -> closures += Closure.MonthEnd(g[1].isNotEmpty()) }
        take("""(\d{1,2})(?:/|月)(\d{1,2})日?~(\d{1,2})(?:/|月)(\d{1,2})日?""") { g ->
            closures += Closure.Dates(Period(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt()))
        }
        take("""(\d{1,2})(?:/|月)(\d{1,2})日?""") { g ->
            closures += Closure.Dates(Period(g[1].toInt(), g[2].toInt(), g[1].toInt(), g[2].toInt()))
        }
        take("""元日""") { closures += Closure.Dates(Period(1, 1, 1, 1)) }
        take("""年末年始""") { closures += Closure.Dates(Period(12, 29, 1, 3)) }
        take("""土[・、]?日[・、]?$HOL""") { closures += listOf(Closure.Weekly(DayOfWeek.SATURDAY), Closure.Weekly(DayOfWeek.SUNDAY), Closure.Holidays) }
        take("""日[・、]?$HOL""") { closures += listOf(Closure.Weekly(DayOfWeek.SUNDAY), Closure.Holidays) }
        take("""(?:毎週)?([$WD])曜日?""") { g -> closures += Closure.Weekly(dayOf.getValue(g[1][0])) }
        take(HOL) { closures += Closure.Holidays }

        // Whatever is left must be only connecting words ("を除く", "休館日:", "配布していません"); anything else and the
        // text says something this reader doesn't know, so no status is shown
        val rest = s.replace(
            Regex("""を除く|除く|休止|休み|を除き|休館日|休館|定休日|閉庁日|毎週|配布|配付|しておりません|していません|しません|は|の|に|日|毎日|および|及び|並びに|[\s、・,()/:;~。．.]"""),
            "",
        )
        if (rest.isNotEmpty()) return null
        if (times.isEmpty() && seasons.isEmpty() && closures.isEmpty()) return null
        return Schedule(times, seasons, closures.distinct(), approximate)
    }
}
