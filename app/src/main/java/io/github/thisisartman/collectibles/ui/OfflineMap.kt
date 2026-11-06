package io.github.thisisartman.collectibles.ui

import android.content.Context
import java.io.File

/**
 * The offline map pack (#9): OpenStreetMap data for the Niigata trip area from Protomaps (build 2026-11-06,
 * zoom 0 to 14), shipped in the app with its styles, Noto Sans glyphs and icons. Japanese names are drawn with
 * the phone's own fonts. Outside the pack's area the map uses online tiles.
 */
object OfflineMap {
    // west, south, east, north of the pack
    private const val WEST = 138.45
    private const val SOUTH = 36.95
    private const val EAST = 139.35
    private const val NORTH = 38.05
    private const val ASSET = "map/niigata.pmtiles"

    fun covers(lat: Double, lng: Double) = lat in SOUTH..NORTH && lng in WEST..EAST

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
