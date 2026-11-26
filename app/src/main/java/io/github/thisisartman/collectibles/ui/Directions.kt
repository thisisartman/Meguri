package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType

/**
 * The text Google Maps should look up for an item: the real place, not a pin at our coordinates.
 * Stations by their Japanese name (南吉田駅), other things by the place that hands them out, each with the town
 * so a common name finds the right one. (Owner, 2026-11-07: Directions opened a loose pin with the kanji name
 * instead of Minami-Yoshida Station's own place.)
 */
fun placeQuery(item: Item): String {
    val place = when (item.type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> item.nameJa
        else -> item.placeName?.takeIf { it.isNotBlank() } ?: item.nameJa
    }
    val town = item.municipality?.takeIf { it.isNotBlank() && !place.contains(it) }
    return listOfNotNull(place, town).joinToString(" ")
}

/** How to get there: walking when it is close (under about 2 km), public transport when further; driving was the default. */
fun travelMode(meters: Double?): String = if (meters != null && meters < WALK_M) "walking" else "transit"

private const val WALK_M = 2_000.0

/** Directions to the item's place in the maps app; falls back to a name search near its position. */
fun openDirections(context: Context, item: Item, meters: Double? = null) {
    val q = placeQuery(item)
    startDirections(context, "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(q)}&travelmode=${travelMode(meters)}") {
        // geo: with a text query searches for that place near the position (no custom pin)
        if (item.lat != null && item.lng != null) "geo:${item.lat},${item.lng}?q=${Uri.encode(q)}" else null
    }
}

/** Directions to a point: a card's cover (#203), which has no place name to look up, only the position printed on the card. */
fun pointDirectionsUrl(lat: Double, lng: Double, meters: Double?): String =
    String.format(java.util.Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=%s", lat, lng, travelMode(meters))

/** Directions to a card's cover, the same way as [openDirections]; falls back to a pin at the cover. */
fun openCoverDirections(context: Context, lat: Double, lng: Double, meters: Double? = null) {
    startDirections(context, pointDirectionsUrl(lat, lng, meters)) { String.format(java.util.Locale.US, "geo:0,0?q=%.6f,%.6f", lat, lng) }
}

private fun startDirections(context: Context, url: String, fallback: () -> String?) {
    val ok = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
    if (!ok) fallback()?.let { geo -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo))) } }
}
