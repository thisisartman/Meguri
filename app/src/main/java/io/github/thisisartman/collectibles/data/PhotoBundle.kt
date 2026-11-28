package io.github.thisisartman.collectibles.data

import java.time.LocalDate

/**
 * A photo file (#188): a zip holding [MANIFEST], a list of photos and where each belongs, and the photos under
 * `photos/`. Adding one never replaces anything: each photo goes onto its item, and an item you don't have yet gets
 * a find on the date given, or else dated when the file is added (owner, 2026-11-26: "just assign it the date it
 * was added"). Stamp photos are cut to the stamp ([cropsStamp]). Pure, for tests; [PhotoImport] does the phone's side.
 *
 * The list's columns: `photo` (file name under `photos/`), `kind` (a [PhotoKind], or [CARD]; each photo is then
 * sorted by what it shows, so a card filed as `manhole` still goes to the Card slot: photo/PhotoSort.kt), `match` (`catalogue`: an item
 * every phone has, found by `key`; `own`: your own item, found again by `type` and `name`; `extra`: one more photo
 * for the row above; `skip`: left out), `type` (an [ItemType]), `key`, `name`, `date` (yyyy-mm-dd, or empty).
 *
 * Optional, and older files have neither: `cover_lat`, `cover_lng`, where the item's manhole cover is, in decimal
 * degrees. Both must be numbers inside Japan ([CoverCoords.inJapan]), or the pair is ignored and the row is still
 * used; an `extra` row's pair is always ignored. A position is only ever given to an item that has none: one set by
 * hand, a scan, the catalogue or an earlier file is never changed ([PhotoImport]).
 */
object PhotoBundle {
    const val MANIFEST = "meguri-photos.csv"
    const val PHOTOS = "photos/"

    /** Where a photo goes: an item by its catalogue key, or your own item by type and name. */
    sealed interface Target {
        data class Catalogue(val key: String) : Target
        data class Own(val type: ItemType, val name: String) : Target
    }

    data class Row(val photo: String, val kind: String, val target: Target, val date: LocalDate?, val cover: CoverCoords.LatLng? = null)

    /** The row's cover position, when both columns are numbers inside Japan; anything else is no position. */
    fun cover(lat: String, lng: String): CoverCoords.LatLng? {
        val a = lat.trim().toDoubleOrNull() ?: return null
        val b = lng.trim().toDoubleOrNull() ?: return null
        return if (CoverCoords.inJapan(a, b)) CoverCoords.LatLng(a, b) else null
    }

    /** The position to give an item: the row's, only when the item has none yet ([current] is its cover_lat). */
    fun coverToSet(row: Row, current: Double?): CoverCoords.LatLng? = if (current == null) row.cover else null

    /** The list, read and checked. Bad lines are skipped and counted, never guessed. */
    /** [named]: every photo the list names, on any line (a skipped one too), so the photos it doesn't name are known (#206). */
    data class Parsed(val rows: List<Row>, val skipped: Int, val bad: List<String>, val named: Set<String> = emptySet())

    /**
     * The photos in the file that its list doesn't name at all (#206): read on the phone and suggested, like photos
     * shared at once. Only pictures (by their ending), never the list itself.
     */
    fun unlisted(files: List<String>, named: Set<String>): List<String> =
        files.filter { it !in named && it.substringAfterLast('.', "").lowercase() in PICTURES }.sorted()

    private val PICTURES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    /**
     * A photo of the card itself, on a manhole or dam card (owner, 2026-11-27): it goes to the Card slot
     * ([PhotoKind.STAMP]); `manhole` is the card's cover. Kept as written here and filed by
     * [io.github.thisisartman.collectibles.photo.PhotoSort] when the photo is added.
     */
    const val CARD = "card"

    private val kinds = setOf(CARD, PhotoKind.STAMP, PhotoKind.EKITAG, PhotoKind.MANHOLE, PhotoKind.PLACE, PhotoKind.GOSHUIN, PhotoKind.PROOF, PhotoKind.OTHER)

    fun parse(text: String): Parsed {
        val rows = mutableListOf<Row>()
        val bad = mutableListOf<String>()
        var skipped = 0
        var last: Target? = null
        val named = mutableSetOf<String>()
        for ((n, m) in Csv.parseWithHeader(text.removePrefix("﻿")).withIndex()) {
            fun v(k: String) = m[k]?.trim().orEmpty()
            val line = "line ${n + 2}"
            val photo = v("photo")
            if (photo.isNotEmpty()) named += photo
            when (val match = v("match").lowercase()) {
                "skip" -> { skipped++; continue }
                "catalogue", "own", "extra" -> {
                    if (photo.isEmpty() || photo.contains("..") || photo.contains('/') || photo.contains('\\')) { bad += "$line: no usable photo name"; continue }
                    // No kind given: sorted by what the photo shows when added (a screenshot of a station is its Ekitag)
                    val kind = v("kind").lowercase().ifEmpty { PhotoKind.OTHER }.takeIf { it in kinds } ?: run { bad += "$line: unknown kind '${v("kind")}'"; continue }
                    val date = v("date").takeIf { it.isNotEmpty() }?.let { d ->
                        runCatching { LocalDate.parse(d) }.getOrNull() ?: run { bad += "$line: date '$d' is not yyyy-mm-dd"; continue }
                    }
                    val target = when (match) {
                        "extra" -> last ?: run { bad += "$line: an extra photo with nothing above it"; continue }
                        "catalogue" -> v("key").takeIf { it.contains(':') }?.let { Target.Catalogue(it) }
                            ?: run { bad += "$line: no catalogue key"; continue }
                        else -> {
                            val type = runCatching { ItemType.valueOf(v("type").uppercase()) }.getOrNull()
                                ?.takeIf { it != ItemType.CUSTOM } ?: run { bad += "$line: unknown type '${v("type")}'"; continue }
                            val name = v("name").takeIf { it.isNotEmpty() } ?: run { bad += "$line: no name"; continue }
                            Target.Own(type, name)
                        }
                    }
                    val cover = if (match == "extra") null else cover(v("cover_lat"), v("cover_lng"))
                    rows += Row(photo, kind, target, date, cover)
                    if (match != "extra") last = target
                }
                else -> bad += "$line: match '$match' is not catalogue, own, extra or skip"
            }
        }
        return Parsed(rows, skipped, bad, named)
    }

    /**
     * What adding the file would do, before anything is written: photos for items on this phone, new items of your
     * own, and catalogue items this phone doesn't have (their region isn't downloaded), whose photos are left out.
     */
    data class Plan(val toItems: Int, val newItems: Int, val photos: Int, val missing: List<String>)

    fun plan(rows: List<Row>, keysOnPhone: Set<String>, ownOnPhone: Set<Pair<ItemType, String>>): Plan {
        val missing = rows.mapNotNull { (it.target as? Target.Catalogue)?.key?.takeIf { k -> k !in keysOnPhone } }.distinct()
        val usable = rows.filter { r -> (r.target as? Target.Catalogue)?.let { it.key in keysOnPhone } ?: true }
        val targets = usable.map { it.target }.distinct()
        val newItems = targets.count { it is Target.Own && (it.type to it.name) !in ownOnPhone }
        return Plan(toItems = targets.size - newItems, newItems = newItems, photos = usable.size, missing = missing)
    }

    /** When an item new to your collection was found: the row's day, or [now], the moment the file is added. */
    fun findDate(row: Row, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        row.date?.let { dayMs(it, zone) } ?: now

    /**
     * Whether the photo is cut to the stamp on it by its shape (#221): a stamp photo of a station, an Ekitag (a
     * photo of its stamp; a screenshot has its own cut), a roadside station, a museum, a place or your own type.
     * Cards have their own cut; a goshuin is cut to its page ([cutsPage]); place and other photos stay whole.
     */
    fun cropsStamp(type: ItemType, kind: String): Boolean =
        kind == PhotoKind.STAMP && type in STAMP_TYPES

    private val STAMP_TYPES = setOf(
        ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.ROADSIDE_STATION, ItemType.MUSEUM_STAMP, ItemType.PLACE, ItemType.CUSTOM,
    )

    /** Whether the photo is a goshuin, cut to its page (#221): a goshuin item's stamp photo, or a goshuin photo of any item. */
    fun cutsPage(type: ItemType, kind: String): Boolean =
        kind == PhotoKind.GOSHUIN || (type == ItemType.GOSHUIN && kind == PhotoKind.STAMP)

    /** A date given is a day, kept at noon so it reads the same in any time zone the phone is set to. */
    fun dayMs(date: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
