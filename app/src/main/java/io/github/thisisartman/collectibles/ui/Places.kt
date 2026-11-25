package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.location.distanceM

/** What a place is, for its icon and its heading under Group by kind (#192). In heading order. */
enum class PlaceKind { STATION, CITY, SHRINE, ROADSIDE, DAM, OTHER }

/**
 * A place on Nearby's Places view (#192, owner approved round 3 on 2026-11-26): the things you can get there, a
 * station's stamp first, then its Ekitag, then the rest. The first thing stands for the place.
 */
data class Place(val things: List<ItemRow>) {
    val anchor: ItemRow get() = things.first()
    val id: String get() = anchor.item.id
    val kind: PlaceKind = Places.kindOf(things.map { it.item.type })
    val collected: Int get() = things.count { it.collected }
    val complete: Boolean get() = things.all { it.collected }
    /** Checked and nothing there for every thing: goes last, faded, as on the Collectibles grid. */
    val noneHere: Boolean get() = things.all { it.noneHere }
}

/** Places are worked out on the phone from what is there (#192): no data changes. Pure, for tests. */
object Places {
    /** A thing handed out this close to a place belongs to it (owner, 2026-11-26). */
    const val JOIN_M = 60.0

    // About 111 m of latitude and 80 to 100 m of longitude across Japan: a 60 m neighbour is always in the 3 x 3 cells
    private const val CELL = 0.001

    /**
     * One place per station spot (its stamp and Ekitag, [stationKey]); then every other thing, in a fixed order, joins
     * the nearest place within [JOIN_M] of where that place stands, or starts a place of its own. Stations never join
     * each other. A thing with no position is a place of its own. The result does not depend on the order of [rows].
     */
    fun group(rows: List<ItemRow>): List<Place> {
        val things = mutableListOf<MutableList<ItemRow>>()
        val seeds = mutableListOf<Pair<Double, Double>?>()
        val station = mutableListOf<Boolean>()
        val cells = HashMap<Pair<Long, Long>, MutableList<Int>>()
        fun cellOf(lat: Double, lng: Double) = Math.floor(lat / CELL).toLong() to Math.floor(lng / CELL).toLong()
        fun add(list: MutableList<ItemRow>, at: Pair<Double, Double>?, isStation: Boolean) {
            things += list; seeds += at; station += isStation
            if (at != null) cells.getOrPut(cellOf(at.first, at.second)) { mutableListOf() } += things.lastIndex
        }
        val (stations, rest) = rows.partition { (it.item.type == ItemType.EKI_STAMP || it.item.type == ItemType.EKITAG) && stationKey(it) != null }
        stations.sortedWith(compareBy({ stationKey(it) }, { it.item.id })).groupBy { stationKey(it)!! }.forEach { (_, l) ->
            add(l.toMutableList(), l.first().item.lat!! to l.first().item.lng!!, true)
        }
        rest.sortedWith(compareBy({ it.item.type.ordinal }, { it.item.id })).forEach { r ->
            val lat = r.item.lat
            val lng = r.item.lng
            if (lat == null || lng == null) { add(mutableListOf(r), null, false); return@forEach }
            val (cy, cx) = cellOf(lat, lng)
            var best = -1
            var bestM = Double.MAX_VALUE
            for (dy in -1L..1L) for (dx in -1L..1L) cells[cy + dy to cx + dx]?.forEach { i ->
                val s = seeds[i]!!
                val m = distanceM(lat, lng, s.first, s.second)
                if (m <= JOIN_M && (m < bestM || (m == bestM && i < best))) { best = i; bestM = m }
            }
            if (best >= 0) things[best] += r else add(mutableListOf(r), lat to lng, false)
        }
        return things.map { l -> Place(l.sortedBy { rank(it.item.type) }) }
    }

    // A station's stamp first, its Ekitag next, then the rest in the order they joined (a stable sort keeps it)
    private fun rank(t: ItemType) = when (t) { ItemType.EKI_STAMP -> 0; ItemType.EKITAG -> 1; else -> 2 }

    /** Station before roadside station before shrine or temple before dam; manhole cards only are a city or town. */
    fun kindOf(types: List<ItemType>): PlaceKind = when {
        types.any { it == ItemType.EKI_STAMP || it == ItemType.EKITAG } -> PlaceKind.STATION
        ItemType.ROADSIDE_STATION in types -> PlaceKind.ROADSIDE
        ItemType.GOSHUIN in types -> PlaceKind.SHRINE
        ItemType.DAM_CARD in types -> PlaceKind.DAM
        types.isNotEmpty() && types.all { it == ItemType.MANHOLE_CARD } -> PlaceKind.CITY
        else -> PlaceKind.OTHER
    }

    /**
     * A place's name: a station's name; otherwise where it is handed out (the place name up to its first space or
     * bracket, "燕市役所 2階 下水道課" reads "燕市役所"), else the thing's own name. The place names are in Japanese
     * only, so in English a place goes by its thing's name.
     */
    fun name(p: Place, japanese: Boolean): String {
        val a = p.anchor.item
        if (p.kind == PlaceKind.STATION) return a.tileLabel(japanese)
        val where = a.placeName?.takeIf { japanese }?.let { shortPlace(it) }?.takeIf { it.isNotBlank() }
        return where ?: a.tileLabel(japanese)
    }

    fun shortPlace(s: String): String = s.trim().split(' ', '　', '（', '(').first().trim().ifBlank { s.trim() }

    /** How far the nearest thing at the place is, or null without a position. */
    fun nearestM(p: Place, lat: Double, lng: Double): Double? =
        p.things.mapNotNull { t -> t.item.lat?.let { la -> t.item.lng?.let { lo -> distanceM(lat, lng, la, lo) } } }.minOrNull()

    /** Nearest first (or A to Z), places where you found nothing last; distance null goes after every distance. */
    fun sorted(places: List<Place>, byName: Boolean, japanese: Boolean, meters: (Place) -> Double?): List<Place> =
        if (byName) places.sortedWith(compareBy<Place> { it.noneHere }.thenBy { name(it, japanese).lowercase() })
        else places.sortedWith(compareBy<Place> { it.noneHere }.thenBy { meters(it) ?: Double.MAX_VALUE }.thenBy { name(it, japanese) })

    /** Group by kind: the kinds in heading order, each with its places in the order given; empty kinds left out. */
    fun byKind(places: List<Place>): List<Pair<PlaceKind, List<Place>>> =
        places.groupBy { it.kind }.let { g -> PlaceKind.entries.mapNotNull { k -> g[k]?.let { k to it } } }
}
