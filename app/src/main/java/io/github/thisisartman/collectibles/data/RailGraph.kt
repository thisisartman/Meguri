package io.github.thisisartman.collectibles.data

import java.util.PriorityQueue

/** One ride on one line: the stations in the order the train reaches them, both ends included. */
data class Leg(val lineId: String, val stations: List<String>)

/**
 * The bundled railway lines as a graph (#46, #60): stations are station items, and each line joins its stations in
 * order. A station on two lines is one item, so a change of train happens wherever two lines share an item.
 * Works offline from the catalogue; no timetable, so "best" means fewest stops plus a cost for each change.
 */
class RailGraph(lines: Map<String, List<String>>) {
    private val lines = lines.filterValues { it.size >= 2 }

    /** Lines through each station. */
    private val linesAt: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        this@RailGraph.lines.forEach { (line, ids) -> ids.forEach { getOrPut(it) { mutableListOf() } += line } }
    }

    val stations: Set<String> get() = linesAt.keys

    fun has(station: String) = station in linesAt

    /**
     * The route from [from] to [to] with the fewest stops, each change of line costing [changeCost] stops.
     * Lines in [avoid] are not used (for "local lines only"). Null when there is no way, or [from] is [to].
     */
    fun route(from: String, to: String, avoid: Set<String> = emptySet(), changeCost: Int = 4): List<Leg>? {
        if (from == to || from !in linesAt || to !in linesAt) return null
        data class State(val station: String, val line: String)
        data class Entry(val cost: Int, val state: State)
        val best = HashMap<State, Int>()
        val prev = HashMap<State, State>()
        val queue = PriorityQueue<Entry>(compareBy { it.cost })
        linesAt.getValue(from).filter { it !in avoid }.forEach { l ->
            val s = State(from, l); best[s] = 0; queue += Entry(0, s)
        }
        var end: State? = null
        while (queue.isNotEmpty()) {
            val (cost, s) = queue.poll()!!
            if (cost > (best[s] ?: Int.MAX_VALUE)) continue
            if (s.station == to) { end = s; break }
            val ids = lines.getValue(s.line)
            val i = ids.indexOf(s.station)
            val next = buildList {
                if (i > 0) add(State(ids[i - 1], s.line) to cost + 1)
                if (i < ids.size - 1) add(State(ids[i + 1], s.line) to cost + 1)
                linesAt.getValue(s.station).filter { it != s.line && it !in avoid }.forEach { add(State(s.station, it) to cost + changeCost) }
            }
            for ((n, c) in next) {
                if (c < (best[n] ?: Int.MAX_VALUE)) { best[n] = c; prev[n] = s; queue += Entry(c, n) }
            }
        }
        val last = end ?: return null
        val path = generateSequence(last) { prev[it] }.toList().reversed()
        // Consecutive states on one line make one leg; a change repeats the station as the next leg's first stop
        val legs = mutableListOf<Leg>()
        var line = path.first().line
        var run = mutableListOf(path.first().station)
        for (s in path.drop(1)) {
            if (s.line != line) {
                if (run.size >= 2) legs += Leg(line, run)
                line = s.line; run = mutableListOf(s.station)
            } else run += s.station
        }
        if (run.size >= 2) legs += Leg(line, run)
        return legs.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val SHINKANSEN = "JR_JOETSU_SHINKANSEN"

        fun from(lineStations: List<LineStation>) = RailGraph(
            lineStations.groupBy { it.lineId }.mapValues { (_, l) -> l.sortedBy { it.seq }.map { it.itemId } },
        )
    }
}

/** Every station a route passes, in order, each once (a change station is not repeated). */
fun List<Leg>.passed(): List<Pair<String, String>> = buildList {
    this@passed.forEachIndexed { i, leg ->
        leg.stations.forEachIndexed { j, s -> if (i == 0 || j > 0) add(s to leg.lineId) }
    }
}
