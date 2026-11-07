package io.github.thisisartman.collectibles.ui

import android.content.Context
import java.io.File

/**
 * The offline map pack (#9, #65): OpenStreetMap data for Niigata, Saitama and Tokyo prefectures from Protomaps
 * (build 2026-11-06, zoom 0 to 14), shipped in the app with its styles, Noto Sans glyphs and icons. Japanese names are drawn with
 * the phone's own fonts. Outside the pack's area the map uses online tiles.
 */
object OfflineMap {
    private const val ASSET = "map/region.pmtiles"
    private const val COVERAGE = "map/coverage.json"
    @Volatile private var rings: List<DoubleArray>? = null

    /** Inside Niigata, Saitama or Tokyo (the prefecture outlines the pack was cut along, simplified). */
    fun covers(context: Context, lat: Double, lng: Double): Boolean = coverage(context).any { inside(it, lng, lat) }

    // Each ring is x0,y0,x1,y1,... (longitude, latitude), read once from the outline file
    private fun coverage(context: Context): List<DoubleArray> = rings ?: synchronized(this) {
        rings ?: runCatching {
            val arr = org.json.JSONArray(context.assets.open(COVERAGE).bufferedReader().use { it.readText() })
            List(arr.length()) { r ->
                val ring = arr.getJSONArray(r)
                DoubleArray(ring.length() * 2) { k -> ring.getJSONArray(k / 2).getDouble(k % 2) }
            }
        }.getOrDefault(emptyList()).also { rings = it }
    }

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

    /** The pack as a file: MapLibre reads PMTiles from files, not from app assets. Copied once per app version. */
    @Synchronized
    fun file(context: Context): File {
        val f = File(context.filesDir, ASSET)
        val size = context.assets.openFd(ASSET).use { it.length }
        if (!f.exists() || f.length() != size) {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(f)
        }
        return f
    }

    /** The style for this theme and language, pointing at the pack on this phone. */
    fun styleJson(context: Context, dark: Boolean, japanese: Boolean): String {
        val name = "map/style-${if (dark) "dark" else "light"}-${if (japanese) "ja" else "en"}.json"
        val json = context.assets.open(name).bufferedReader().use { it.readText() }
        return json.replace("pmtiles://__PACK__", "pmtiles://file://" + file(context).absolutePath)
    }
}
