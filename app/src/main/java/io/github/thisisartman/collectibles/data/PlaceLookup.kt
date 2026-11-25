package io.github.thisisartman.collectibles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place found on OpenStreetMap: name, address, position and its OSM page (ODbL: credit OpenStreetMap contributors). */
data class PlaceHit(
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val osmUrl: String?,
    // The place's own Japanese and English names on OpenStreetMap, to fill in a new item (owner, 2026-11-07)
    val nameJa: String? = null,
    val nameEn: String? = null,
)

/**
 * Looks a place up on OpenStreetMap (owner, 2026-11-07: when adding a location, pull the place from Maps or OSM).
 * Online, one request per search, as Nominatim's usage policy asks (an app name in the User-Agent, no bulk use).
 * Results near [nearLat]/[nearLng] come first. Returns an empty list offline or on any error.
 */
object PlaceLookup {
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"

    suspend fun search(query: String, nearLat: Double?, nearLng: Double?, japanese: Boolean): List<PlaceHit> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val near = if (nearLat != null && nearLng != null) {
                val d = 0.5
                "&viewbox=${nearLng - d},${nearLat + d},${nearLng + d},${nearLat - d}"
            } else ""
            val url = URL("$ENDPOINT?q=$q&format=jsonv2&limit=8&countrycodes=jp&addressdetails=0&namedetails=1$near")
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", UserAgent.value)
                setRequestProperty("Accept-Language", if (japanese) "ja,en" else "en,ja")
            }
            c.inputStream.bufferedReader().use { r -> parse(r.readText()) }
        }.getOrDefault(emptyList())
    }

    internal fun parse(json: String): List<PlaceHit> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val display = o.optString("display_name")
            val name = o.optString("name").ifBlank { display.substringBefore(",") }
            val type = o.optString("osm_type"); val id = o.optLong("osm_id")
            PlaceHit(
                name = name,
                address = display.takeIf { it != name },
                lat = o.getString("lat").toDouble(),
                lng = o.getString("lon").toDouble(),
                osmUrl = if (type.isNotBlank() && id != 0L) "https://www.openstreetmap.org/$type/$id" else null,
                nameJa = o.optJSONObject("namedetails")?.let { d -> d.optString("name:ja").ifBlank { d.optString("name").takeIf { n -> n.any { it.code >= 0x3000 } } } }?.ifBlank { null },
                nameEn = o.optJSONObject("namedetails")?.optString("name:en")?.ifBlank { null },
            )
        }
    }
}
