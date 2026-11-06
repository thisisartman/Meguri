package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.location.distanceM

/** Something near a stop on the route, and whether it counts towards "to collect" on this trip. */
data class TripThing(val row: ItemRow, val meters: Double, val counts: Boolean)

/** One station the train passes, with what is near it. */
data class TripStop(
    val stationId: String,
    val lineId: String,
    /** The line you change to here, when this is a change of train. */
    val changeTo: String?,
    val getOff: Boolean,
    val things: List<TripThing>,
)

data class TripPlan(val stops: List<TripStop>) {
    val toCollect: Int get() = stops.sumOf { s -> s.things.count { it.counts } }
    val itemIds: List<String> get() = stops.flatMap { s -> s.things.map { it.row.item.id } }
    val countedIds: List<String> get() = stops.flatMap { s -> s.things.filter { it.counts }.map { it.row.item.id } }
}

/**
 * The rules for what a route trip lists and counts (#46, owner's answers 2026-11-06 and 11-07):
 * - along the route means near **any** station the train passes, not only the ones you get off at;
 * - near means within [radiusM] (2 km);
 * - a station stamp (eki stamp, Ekitag) counts only at a stop you confirm you will get off at;
 * - anything else near a passed station counts while it is still to collect.
 * Each thing is listed once, at the passed station nearest to it.
 */
object TripRules {
    const val RADIUS_M = 2_000.0

    fun isStationStamp(t: ItemType) = t == ItemType.EKI_STAMP || t == ItemType.EKITAG

    fun plan(legs: List<Leg>, rows: List<ItemRow>, getOff: Set<String>, radiusM: Double = RADIUS_M): TripPlan {
        val passed = legs.passed()
        if (passed.isEmpty()) return TripPlan(emptyList())
        val byId = rows.associateBy { it.item.id }
        val changes = buildMap { legs.zipWithNext().forEach { (a, b) -> put(a.stations.last(), b.lineId) } }
        val onRoute = passed.map { it.first }.toSet()
        val near = HashMap<String, MutableList<Pair<ItemRow, Double>>>()
        for (r in rows) {
            val lat = r.item.lat ?: continue
            val lng = r.item.lng ?: continue
            // A station on the route is listed at its own stop, whatever else is nearer
            val at = if (r.item.id in onRoute) r.item.id to 0.0 else {
                passed.mapNotNull { (id, _) ->
                    val s = byId[id]?.item ?: return@mapNotNull null
                    val sl = s.lat ?: return@mapNotNull null
                    val sg = s.lng ?: return@mapNotNull null
                    id to distanceM(sl, sg, lat, lng)
                }.minByOrNull { it.second }?.takeIf { it.second <= radiusM }
            } ?: continue
            near.getOrPut(at.first) { mutableListOf() } += r to at.second
        }
        return TripPlan(passed.map { (id, line) ->
            val off = id in getOff
            TripStop(
                stationId = id, lineId = line, changeTo = changes[id], getOff = off,
                things = near[id].orEmpty().sortedBy { it.second }.map { (r, m) ->
                    TripThing(r, m, counts = !r.collected && (off || !isStationStamp(r.item.type)))
                },
            )
        })
    }
}
