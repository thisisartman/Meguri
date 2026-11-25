package io.github.thisisartman.collectibles.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.text.Normalizer

/** One end of a trip as a link gives it: a position, a name, or both. */
data class LinkEnd(val name: String?, val lat: Double?, val lng: Double?) {
    val hasPosition get() = lat != null && lng != null
}

/** What a Google Maps directions link says: where from, where to, and how (transit, walking...). */
data class MapsTrip(val from: LinkEnd?, val to: LinkEnd?, val mode: String?)

/**
 * Reads a Google Maps directions link the user pastes (#60). Parsing a link you were given is fine; Meguri never
 * fetches Google's route pages. Formats (Google's documented Maps URLs, and what the address bar shows):
 * - `google.com/maps/dir/?api=1&origin=...&destination=...&travelmode=transit`
 * - `google.com/maps/dir/<from>/<to>/@lat,lng,zoom/data=...!1d<lng>!2d<lat>...!3e3`
 * - a short link from Share (`maps.app.goo.gl/...`), which needs the network once: see [resolve]
 * Transit links carry no stations, so the app plans the line itself from the two ends.
 */
object MapsLink {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""")
    private val LAT_LNG = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val DATA_POINT = Regex("""!1d(-?\d+(?:\.\d+)?)!2d(-?\d+(?:\.\d+)?)""")
    private val DATA_MODE = Regex("""!3e(\d)""")
    // A place's own position in a place link's data part (latitude !3d, longitude !4d), and the map centre after "@"
    private val PLACE_POINT = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val AT_CENTRE = Regex("""^@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val MODES = mapOf("0" to "driving", "1" to "bicycling", "2" to "walking", "3" to "transit")

    /** The first web address in pasted text (Share adds a title before the link). */
    fun findUrl(text: String): String? = URL_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ')')

    fun isShort(url: String): Boolean = host(url)?.let { it == "maps.app.goo.gl" || it == "goo.gl" || it.endsWith(".app.goo.gl") } ?: false

    private fun host(url: String) = runCatching { URI(url).host?.lowercase() }.getOrNull()

    private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun end(raw: String?): LinkEnd? {
        val s = raw?.let(::decode)?.trim()?.ifEmpty { null } ?: return null
        val m = LAT_LNG.find(s)
        return if (m != null) LinkEnd(null, m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) else LinkEnd(s, null, null)
    }

    /** Null when the link is not a Google Maps directions link. */
    fun parse(url: String): MapsTrip? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val h = uri.host?.lowercase() ?: return null
        // The EU consent page wraps the real address in "continue"
        if (h.startsWith("consent.")) {
            val cont = query(uri.rawQuery)["continue"] ?: return null
            return parse(cont)
        }
        if (!(h.startsWith("google.") || h.contains(".google.") || h == "maps.google.com")) return null
        val path = uri.rawPath.orEmpty()
        val q = query(uri.rawQuery)
        if (q["api"] == "1" && (q.containsKey("origin") || q.containsKey("destination"))) {
            return MapsTrip(end(q["origin"]), end(q["destination"]), q["travelmode"])
        }
        // Empty parts are kept after "dir": "dir//B" means from your location to B
        val parts = path.split('/')
        // A place link (what Share gives for one place, often behind a short link): go there from where you are
        val place = parts.indexOf("place").takeIf { it >= 0 }
        if (place != null && parts.indexOf("dir") < 0) {
            val name = parts.getOrNull(place + 1)?.let { decode(it.replace('+', ' ')) }?.trim()?.ifEmpty { null }
            val pos = PLACE_POINT.find(path)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
                ?: parts.firstNotNullOfOrNull { AT_CENTRE.find(it) }?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            if (name == null && pos == null) return null
            return MapsTrip(null, LinkEnd(name, pos?.first, pos?.second), null)
        }
        val dir = parts.indexOf("dir").takeIf { it >= 0 } ?: return null
        val after = parts.drop(dir + 1).dropLastWhile { it.isEmpty() }
        val stop = after.indexOfFirst { it.startsWith("@") || it.startsWith("data=") || it.startsWith("am=") }
        val segments = (if (stop >= 0) after.take(stop) else after).map { decode(it.replace('+', ' ')) }
        val data = after.firstOrNull { it.startsWith("data=") }.orEmpty()
        val points = DATA_POINT.findAll(data).map { it.groupValues[2].toDouble() to it.groupValues[1].toDouble() }.toList()
        val mode = DATA_MODE.find(data)?.groupValues?.get(1)?.let { MODES[it] }
        val ends = segments.map { end(it) }
        if (ends.isEmpty() && points.size < 2) return null
        // Positions in the data part follow the named stops in order; pair them only when the counts agree
        val withPos = if (points.size == ends.size) ends.zip(points) { e, p -> (e ?: LinkEnd(null, null, null)).copy(lat = p.first, lng = p.second) }
        else if (ends.isEmpty()) points.map { LinkEnd(null, it.first, it.second) } else ends
        return MapsTrip(withPos.firstOrNull()?.takeIf { it.name != null || it.hasPosition }, withPos.lastOrNull()?.takeIf { withPos.size >= 2 && (it.name != null || it.hasPosition) }, mode)
    }

    /**
     * One position from any Google Maps link, for setting where an item is (owner, 2026-11-08): a place's own point,
     * `?q=` / `?query=` / `?ll=` with a position (or a name to search), else the map centre after "@". Null when none.
     */
    fun point(url: String): LinkEnd? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val h = uri.host?.lowercase() ?: return null
        if (h.startsWith("consent.")) return query(uri.rawQuery)["continue"]?.let(::point)
        if (!(h.startsWith("google.") || h.contains(".google.") || h == "maps.google.com")) return null
        val path = uri.rawPath.orEmpty()
        PLACE_POINT.find(path)?.let { return LinkEnd(null, it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
        val q = query(uri.rawQuery)
        val asked = listOf("q", "query", "ll").firstNotNullOfOrNull { k -> q[k]?.let(::end) }
        if (asked?.hasPosition == true) return asked
        path.split('/').firstNotNullOfOrNull { AT_CENTRE.find(it) }?.let { return LinkEnd(null, it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
        return asked
    }

    private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').mapNotNull { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) null else kv.substring(0, i) to decode(kv.substring(i + 1).replace('+', ' '))
    }.toMap()

    /** Follows a short link's redirects (online, a handful of requests to Google's link service). Null on any error. */
    suspend fun resolve(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            var u = url
            repeat(6) {
                if (!isShort(u)) return@runCatching u
                val c = (URL(u).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 8_000; readTimeout = 8_000
                    setRequestProperty("User-Agent", UserAgent.value)
                }
                val loc = c.getHeaderField("Location")
                c.disconnect()
                u = URL(URL(u), loc ?: return@runCatching null).toString()
            }
            u.takeIf { !isShort(it) }
        }.getOrNull()
    }

    /**
     * The station a link's name means: an exact match, else the longest station name the link's name starts with
     * ("Nagaoka Station Niigata" is Nagaoka, not Kita-Nagaoka). [stations] are id to names (Japanese, romaji).
     */
    fun matchStation(name: String, stations: List<Pair<String, List<String>>>): String? {
        val k = stationKey(name)
        if (k.isEmpty()) return null
        var best: String? = null
        var bestLen = 0
        for ((id, names) in stations) for (n in names) {
            val sk = stationKey(n)
            if (sk.length < 2) continue
            if (sk == k) return id
            if (k.startsWith(sk) && sk.length > bestLen) { best = id; bestLen = sk.length }
        }
        return best
    }

    /**
     * A station name as people and Google write it, made comparable: "Urasa Station, Minamiuonuma" and "浦佐駅"
     * become "urasa" and "浦佐"; long vowels and hyphens do not matter.
     */
    fun stationKey(name: String): String {
        val first = name.substringBefore(',').substringBefore('、').trim()
        val n = Normalizer.normalize(first, Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "").lowercase()
        return n.replace(Regex("""\b(station|sta\.?|eki)\b"""), "").replace("駅", "")
            .replace("ou", "o").replace("oo", "o").replace("uu", "u")
            .replace(Regex("""[\s\-‐‑–—・'’]"""), "")
    }
}
