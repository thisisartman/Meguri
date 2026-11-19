package io.github.thisisartman.collectibles.data

import java.net.URLEncoder

/**
 * Links to the catalogue issue forms with the item filled in (0.6, #1). They open in the browser; nothing is sent
 * until the person submits the form on GitHub. Only the item's own catalogue details go in, never your position.
 */
object CatalogueReport {
    private const val NEW_ISSUE = "https://github.com/thisisartman/Meguri/issues/new"

    enum class Kind(val template: String, val prefix: String) {
        AVAILABILITY("availability.yml", "[Availability] "),
        CORRECTION("data_correction.yml", "[Correction] "),
        ADD("new_place.yml", "[Add] "),
    }

    /** How the item is named in the form: "名前 (Romaji) · code". */
    fun label(item: Item): String = buildString {
        append(item.nameJa)
        item.nameRomaji?.takeIf { it.isNotBlank() && it != item.nameJa }?.let { append(" (").append(it).append(')') }
        item.code?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    fun url(kind: Kind, item: Item): String {
        val fields = linkedMapOf("template" to kind.template)
        when (kind) {
            Kind.AVAILABILITY, Kind.CORRECTION -> {
                fields["title"] = kind.prefix + label(item)
                fields["item"] = label(item)
            }
            Kind.ADD -> {
                fields["title"] = kind.prefix
                val where = listOfNotNull(item.placeName?.takeIf { it.isNotBlank() } ?: item.nameJa, item.address?.takeIf { it.isNotBlank() }).joinToString(", ")
                val map = if (item.lat != null && item.lng != null) " https://www.openstreetmap.org/?mlat=${item.lat}&mlon=${item.lng}#map=18/${item.lat}/${item.lng}" else ""
                fields["location"] = where + map
            }
        }
        return NEW_ISSUE + "?" + fields.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
    }
}
