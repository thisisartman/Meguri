package io.github.thisisartman.collectibles.data

/** Minimal RFC 4180 CSV: quoted fields, doubled quotes, CRLF or LF, UTF-8 BOM stripped. */
object Csv {
    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Rows as maps keyed by the header row (trimmed, lower-case). */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).map { r -> header.indices.associate { header[it] to (r.getOrNull(it)?.trim() ?: "") } }
    }

    fun escape(v: Any?): String {
        val s = v?.toString() ?: return ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun line(vararg values: Any?): String = values.joinToString(",") { escape(it) } + "\n"
}

/**
 * One catalogue row as read from the CSV (docs/CSV_FORMAT.md). An eki-stamp row may also name the
 * railway line it is on and its place along that line.
 */
data class CatalogRow(
    val type: ItemType,
    val catalogKey: String,
    val code: String?,
    val nameJa: String,
    val nameRomaji: String?,
    val series: String?,
    val releaseDate: String?,
    val prefecture: String?,
    val municipality: String?,
    val placeName: String?,
    val address: String?,
    val placePhone: String?,
    val lat: Double?,
    val lng: Double?,
    val hoursText: String?,
    val closedDays: String?,
    val notes: String?,
    val sourceUrl: String?,
    val lineId: String?,
    val lineNameJa: String?,
    val lineNameRomaji: String?,
    val lineSeq: Int?,
) {
    companion object {
        /** Reads one CSV map; returns null with a reason when the row cannot be used. */
        fun from(m: Map<String, String>): Result<CatalogRow> = runCatching {
            fun v(k: String) = m[k]?.trim()?.ifEmpty { null }
            val type = ItemType.valueOf(v("type")?.uppercase() ?: error("missing type"))
            val nameJa = v("name_ja") ?: error("missing name_ja")
            val code = v("code")
            val key = v("catalog_key") ?: when (type) {
                ItemType.MANHOLE_CARD -> "MANHOLE_CARD:" + (code ?: error("manhole card without code"))
                else -> "$type:$nameJa"
            }
            CatalogRow(
                type, key, code, nameJa, v("name_romaji"), v("series"), v("release_date"), v("prefecture"), v("municipality"),
                v("place_name"), v("address"), v("place_phone"), v("lat")?.toDouble(), v("lng")?.toDouble(),
                v("hours_text"), v("closed_days"), v("notes"), v("source_url"),
                v("line_id"), v("line_name_ja"), v("line_name_romaji"), v("line_seq")?.toInt(),
            )
        }
    }

    /** The item to store, keeping an existing item's id, creation time and anything the CSV leaves empty. */
    fun toItem(existing: Item?, id: String, now: Long): Item = Item(
        id = existing?.id ?: id,
        type = type,
        catalogKey = catalogKey,
        code = code ?: existing?.code,
        nameJa = nameJa,
        nameRomaji = nameRomaji ?: existing?.nameRomaji,
        series = series ?: existing?.series,
        releaseDate = releaseDate ?: existing?.releaseDate,
        prefecture = prefecture ?: existing?.prefecture,
        municipality = municipality ?: existing?.municipality,
        placeName = placeName ?: existing?.placeName,
        address = address ?: existing?.address,
        placePhone = placePhone ?: existing?.placePhone,
        lat = lat ?: existing?.lat,
        lng = lng ?: existing?.lng,
        hoursText = hoursText ?: existing?.hoursText,
        closedDays = closedDays ?: existing?.closedDays,
        notes = notes ?: existing?.notes,
        sourceUrl = sourceUrl ?: existing?.sourceUrl,
        origin = existing?.origin ?: Origin.CSV_IMPORT,
        parentItemId = existing?.parentItemId,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}
