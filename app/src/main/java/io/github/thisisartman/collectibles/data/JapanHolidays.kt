package io.github.thisisartman.collectibles.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Japan's national holidays, worked out from the law instead of a stored list, so the opening-hours rules keep working
 * every year offline (0.5, owner: "closed Monday, or Tuesday when Monday is a holiday"). Fixed dates, the Happy Monday
 * days, the two equinox days (the usual formula, good for 1980 to 2099), the substitute holiday after a Sunday
 * holiday, and the citizens' holiday between two holidays. The one-off moves of 2019 to 2021 are not included.
 */
object JapanHolidays {
    private val cache = HashMap<Int, Set<LocalDate>>()

    fun isHoliday(date: LocalDate): Boolean = date in forYear(date.year)

    @Synchronized
    fun forYear(year: Int): Set<LocalDate> = cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Set<LocalDate> {
        fun d(m: Int, day: Int) = LocalDate.of(year, m, day)
        fun nthMonday(m: Int, n: Int) = d(m, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))
        val leap = (year - 1980) / 4
        val spring = (20.8431 + 0.242194 * (year - 1980)).toInt() - leap
        val autumn = (23.2488 + 0.242194 * (year - 1980)).toInt() - leap
        val base = sortedSetOf(
            d(1, 1), nthMonday(1, 2), d(2, 11), d(2, 23), d(3, spring), d(4, 29), d(5, 3), d(5, 4), d(5, 5),
            nthMonday(7, 3), d(8, 11), nthMonday(9, 3), d(9, autumn), nthMonday(10, 2), d(11, 3), d(11, 23),
        )
        val all = base.toSortedSet()
        // Substitute holiday: a holiday on a Sunday moves to the next day that is not already a holiday
        for (h in base) if (h.dayOfWeek == DayOfWeek.SUNDAY) {
            var next = h.plusDays(1)
            while (next in all) next = next.plusDays(1)
            all += next
        }
        // Citizens' holiday: an ordinary day with a holiday on both sides (happens in some Septembers)
        for (h in base) {
            val between = h.plusDays(1)
            if (between !in all && h.plusDays(2) in base && between.dayOfWeek != DayOfWeek.SUNDAY) all += between
        }
        return all
    }
}
