package io.github.thisisartman.collectibles.data

import java.time.LocalDate

/**
 * A photo file (#188): a zip holding [MANIFEST], a list of photos and where each belongs, and the photos under
 * `photos/`. Adding one never replaces anything: each photo goes onto its item, and an item you don't have yet gets
 * a find on the date given, or "had before Meguri". Pure, for tests; [PhotoImport] does the phone's side.
 *
 * The list's columns: `photo` (file name under `photos/`), `kind` (a [PhotoKind]), `match` (`catalogue`: an item
 * every phone has, found by `key`; `own`: your own item, found again by `type` and `name`; `extra`: one more photo
 * for the row above; `skip`: left out), `type` (an [ItemType]), `key`, `name`, `date` (yyyy-mm-dd, or empty).
 */
object PhotoBundle {
    const val MANIFEST = "meguri-photos.csv"
    const val PHOTOS = "photos/"

    /** Where a photo goes: an item by its catalogue key, or your own item by type and name. */
    sealed interface Target {
        data class Catalogue(val key: String) : Target
        data class Own(val type: ItemType, val name: String) : Target
    }

    data class Row(val photo: String, val kind: String, val target: Target, val date: LocalDate?)

    /** The list, read and checked. Bad lines are skipped and counted, never guessed. */
    data class Parsed(val rows: List<Row>, val skipped: Int, val bad: List<String>)

    private val kinds = setOf(PhotoKind.STAMP, PhotoKind.EKITAG, PhotoKind.MANHOLE, PhotoKind.PLACE, PhotoKind.GOSHUIN, PhotoKind.PROOF, PhotoKind.OTHER)

    fun parse(text: String): Parsed {
        val rows = mutableListOf<Row>()
        val bad = mutableListOf<String>()
        var skipped = 0
        var last: Target? = null
        for ((n, m) in Csv.parseWithHeader(text.removePrefix("﻿")).withIndex()) {
            fun v(k: String) = m[k]?.trim().orEmpty()
            val line = "line ${n + 2}"
            val photo = v("photo")
            when (val match = v("match").lowercase()) {
                "skip" -> { skipped++; continue }
                "catalogue", "own", "extra" -> {
                    if (photo.isEmpty() || photo.contains("..") || photo.contains('/') || photo.contains('\\')) { bad += "$line: no usable photo name"; continue }
                    val kind = v("kind").lowercase().takeIf { it in kinds } ?: run { bad += "$line: unknown kind '${v("kind")}'"; continue }
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
                    rows += Row(photo, kind, target, date)
                    if (match != "extra") last = target
                }
                else -> bad += "$line: match '$match' is not catalogue, own, extra or skip"
            }
        }
        return Parsed(rows, skipped, bad)
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

    /** A date given is a day, kept at noon so it reads the same in any time zone the phone is set to. */
    fun dayMs(date: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}
