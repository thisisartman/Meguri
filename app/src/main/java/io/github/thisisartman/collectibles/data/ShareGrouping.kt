package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.location.distanceM

/** A photo shared into Meguri (#80), with what its file says about where and when it was taken. */
/** [placeHidden]: the gallery hid where it was taken, and Meguri could see it if allowed (see SharedPhotoReader). */
data class SharedPhoto(
    val uri: String, val lat: Double? = null, val lng: Double? = null, val takenAt: Long? = null, val placeHidden: Boolean = false,
) {
    val hasPlace: Boolean get() = lat != null && lng != null
}

/** Photos taken near each other, and the item they most likely show. [centre] is null for photos with no location. */
data class PhotoGroup(val photos: List<SharedPhoto>, val centre: Pair<Double, Double>?)

object ShareGrouping {
    /** How far apart two photos can be and still count as the same place: a station and its square, a shrine's grounds. */
    const val SAME_PLACE_M = 250.0

    /**
     * Groups photos by place (#80): a photo joins a group if it is within [SAME_PLACE_M] of any photo already in it, so a
     * walk round one station stays one group. Photos without a location form one group of their own, last. Groups keep
     * the order the photos were shared in.
     */
    fun byPlace(photos: List<SharedPhoto>, radiusM: Double = SAME_PLACE_M): List<PhotoGroup> {
        val placed = photos.filter { it.hasPlace }
        val groups = mutableListOf<MutableList<SharedPhoto>>()
        for (p in placed) {
            val near = groups.filter { g -> g.any { distanceM(it.lat!!, it.lng!!, p.lat!!, p.lng!!) <= radiusM } }
            when {
                near.isEmpty() -> groups += mutableListOf(p)
                else -> {
                    // A photo between two groups joins them into one
                    val first = near.first()
                    first += p
                    near.drop(1).forEach { other -> first += other; groups.remove(other) }
                }
            }
        }
        val out = groups.map { g ->
            PhotoGroup(g.sortedBy { photos.indexOf(it) }, g.map { it.lat!! }.average() to g.map { it.lng!! }.average())
        }.sortedBy { g -> photos.indexOf(g.photos.first()) }
        val unplaced = photos.filter { !it.hasPlace }
        return if (unplaced.isEmpty()) out else out + PhotoGroup(unplaced, null)
    }

    /**
     * What a group most likely shows: items nearest its centre (or [fallback], where you are now, for photos without a
     * location), closest first, within [maxM]. Things checked and found empty are left out; [types] narrows by kind.
     */
    fun suggestions(
        rows: List<ItemRow>, centre: Pair<Double, Double>?, fallback: Pair<Double, Double>? = null,
        types: Set<ItemType>? = null, maxM: Double = 2_000.0, limit: Int = 8,
    ): List<Pair<ItemRow, Double>> {
        val at = centre ?: fallback ?: return emptyList()
        // Near you (no position on the photo, as for a scan): 2 km around you is often empty, the owner's nearest
        // station is 3 km away ("Nothing here", S23 run 2026-11-10); look 50 km out, nearest first
        val reach = if (centre == null) maxOf(maxM, 50_000.0) else maxM
        return rows.asSequence()
            .filter { !it.noneHere && (types == null || it.item.type in types) }
            .mapNotNull { r ->
                val lat = r.item.lat ?: return@mapNotNull null
                val lng = r.item.lng ?: return@mapNotNull null
                r to distanceM(at.first, at.second, lat, lng)
            }
            .filter { it.second <= reach }
            // At one spot a station's stamp and its Ekitag tie; a shared photo is more likely the paper stamp
            .sortedWith(compareBy({ it.second }, { it.first.item.type == ItemType.EKITAG }))
            .take(limit)
            .toList()
    }
}
