package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.ItemRow

/**
 * One tile in the onboarding's "what you already have" picker (#81): a place and everything there, so a station's
 * stamp and its Ekitag are ticked off from one card. Things at one place share a name and sit within about 100 m.
 */
data class PickerPlace(val key: String, val rows: List<ItemRow>) {
    val first: ItemRow get() = rows.first()
}

fun pickerPlaces(rows: List<ItemRow>): List<PickerPlace> =
    rows.groupBy { r ->
        val pos = r.item.lat?.let { lat -> r.item.lng?.let { lng -> "%.3f,%.3f".format(java.util.Locale.US, lat, lng) } }
        if (pos == null) r.item.id else fold(r.item.nameJa) + "@" + pos
    }.map { (key, rs) -> PickerPlace(key, rs.sortedBy { it.item.type.ordinal }) }

/** The picker's order: A to Z by the name shown, or nearest first when [here] is known and [nearest] is on. */
fun sortPlaces(places: List<PickerPlace>, japanese: Boolean, nearest: Boolean, distance: (ItemRow) -> Double?): List<PickerPlace> =
    if (nearest) places.sortedBy { distance(it.first) ?: Double.MAX_VALUE }
    else places.sortedBy { fold(it.first.item.displayName(japanese)) }
