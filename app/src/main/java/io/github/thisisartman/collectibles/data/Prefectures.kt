package io.github.thisisartman.collectibles.data

/** A prefecture's outline (Natural Earth, public domain; tools/map-pack/make_prefectures.py), for the onboarding (#81). */
data class Prefecture(val code: String, val ja: String, val en: String, val rings: List<List<Pair<Double, Double>>>) {
    /** Whether a point (lat, lng) is inside one of its outlines. */
    fun contains(lat: Double, lng: Double): Boolean = rings.any { inside(it, lng, lat) }

    fun name(japanese: Boolean) = if (japanese) ja else en

    /** The largest outline: the mainland, without outlying islands (for framing the map). */
    fun mainRing(): List<Pair<Double, Double>> = rings.maxByOrNull { area(it) }.orEmpty()

    private fun area(r: List<Pair<Double, Double>>): Double =
        kotlin.math.abs(r.indices.sumOf { i -> val (x1, y1) = r[i]; val (x2, y2) = r[(i + 1) % r.size]; x1 * y2 - x2 * y1 }) / 2

    private fun inside(ring: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var c = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (xi, yi) = ring[i]
            val (xj, yj) = ring[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }
}

object Prefectures {
    /**
     * "Popular choices" in the region step, a hard-coded order for now (owner, 2026-11-07: later it can follow what
     * people pick). ISO 3166-2 codes; prefectures without a catalogue yet are left out of the list.
     */
    val POPULAR = listOf(
        "JP-13", "JP-26", "JP-27", "JP-01", "JP-47", "JP-14", "JP-29", "JP-34", "JP-40", "JP-17", "JP-20", "JP-15",
        "JP-11", "JP-12", "JP-23", "JP-28", "JP-04", "JP-22", "JP-09", "JP-10",
    )

    fun at(all: List<Prefecture>, lat: Double, lng: Double): Prefecture? = all.firstOrNull { it.contains(lat, lng) }

    /**
     * The region list: the prefecture you are in first (when it has a catalogue), then the rest in [POPULAR] order,
     * then any others, A to Z. Only prefectures in [available] (by Japanese name) are listed.
     */
    fun ordered(all: List<Prefecture>, available: Set<String>, here: Pair<Double, Double>?): Pair<Prefecture?, List<Prefecture>> {
        val listed = all.filter { it.ja in available }
        val mine = here?.let { (lat, lng) -> listed.firstOrNull { it.contains(lat, lng) } }
        val rest = listed.filter { it != mine }
            .sortedWith(compareBy({ POPULAR.indexOf(it.code).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.en }))
        return mine to rest
    }
}
