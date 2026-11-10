package io.github.thisisartman.collectibles.ui

import android.content.Context
import io.github.thisisartman.collectibles.data.RegionPacks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The offline map (#9, #65; region packs since 0.5): OpenStreetMap data from Protomaps (zoom 0 to 14), one file per
 * prefecture you have downloaded, drawn together with the app's styles, Noto Sans glyphs and icons. Japanese names
 * are drawn with the phone's own fonts. A phone that still has the map that used to come inside the app
 * (Niigata, Saitama and Tokyo in one file) keeps using it until it downloads packs. Elsewhere the map uses online tiles.
 */
object OfflineMap {
    private const val LEGACY = "map/region.pmtiles"
    private const val LEGACY_COVERAGE = "map/coverage.json"

    private data class Source(val file: File, val rings: List<DoubleArray>)
    @Volatile private var cached: Pair<Long, List<Source>>? = null

    /** Called after a pack is installed or removed, so the next map picks up the change. */
    fun changed() { cached = null }

    // The packs on this phone, else the old built-in map; read again when the installed packs change
    private fun sources(context: Context): List<Source> {
        val stamp = RegionPacks.installedStamp(context)
        cached?.takeIf { it.first == stamp }?.let { return it.second }
        val packs = RegionPacks.installedMaps(context).map { (f, rings) -> Source(f, rings) }
        val list = packs.ifEmpty {
            val legacy = File(context.filesDir, LEGACY)
            if (legacy.exists()) listOf(Source(legacy, legacyRings(context))) else emptyList()
        }
        cached = stamp to list
        return list
    }

    private fun legacyRings(context: Context): List<DoubleArray> = runCatching {
        val arr = JSONArray(context.assets.open(LEGACY_COVERAGE).bufferedReader().use { it.readText() })
        List(arr.length()) { r ->
            val ring = arr.getJSONArray(r)
            DoubleArray(ring.length() * 2) { k -> ring.getJSONArray(k / 2).getDouble(k % 2) }
        }
    }.getOrDefault(emptyList())

    /** Inside a prefecture whose map is on this phone. */
    fun covers(context: Context, lat: Double, lng: Double): Boolean =
        sources(context).any { s -> s.rings.any { inside(it, lng, lat) } }

    /** Whether any offline map is on this phone. */
    fun available(context: Context): Boolean = sources(context).isNotEmpty()

    /** Even-odd ray casting. */
    private fun inside(ring: DoubleArray, x: Double, y: Double): Boolean {
        var c = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i]; val yi = ring[2 * i + 1]; val xj = ring[2 * j]; val yj = ring[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    /**
     * The style for this theme and language, drawing every map on this phone: the style's one map source and its
     * layers are repeated for each file (neighbouring prefectures overlap a little at the border, and draw the same).
     */
    fun styleJson(context: Context, dark: Boolean, japanese: Boolean): String? {
        val files = sources(context).map { it.file }
        if (files.isEmpty()) return null
        val name = "map/style-${if (dark) "dark" else "light"}-${if (japanese) "ja" else "en"}.json"
        val style = JSONObject(context.assets.open(name).bufferedReader().use { it.readText() })
        val template = style.getJSONObject("sources").getJSONObject("protomaps")
        val sources = JSONObject()
        files.forEachIndexed { i, f ->
            sources.put("pm$i", JSONObject(template.toString()).put("url", "pmtiles://file://" + f.absolutePath))
        }
        style.put("sources", sources)
        val layers = style.getJSONArray("layers")
        val out = JSONArray()
        for (k in 0 until layers.length()) {
            val layer = layers.getJSONObject(k)
            if (layer.optString("source") != "protomaps") { out.put(layer); continue }
            files.indices.forEach { i ->
                out.put(JSONObject(layer.toString()).put("source", "pm$i").put("id", layer.getString("id") + if (i == 0) "" else "#$i"))
            }
        }
        style.put("layers", out)
        return style.toString()
    }
}
