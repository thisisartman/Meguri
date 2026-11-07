package io.github.thisisartman.collectibles.ui

import android.content.Context
import io.github.thisisartman.collectibles.data.Prefecture
import org.json.JSONArray

/** The 47 prefecture outlines in assets/map/prefectures.json (#81), read once. */
object PrefectureAsset {
    @Volatile private var cache: List<Prefecture>? = null

    fun all(context: Context): List<Prefecture> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val arr = JSONArray(context.assets.open("map/prefectures.json").use { it.readBytes().toString(Charsets.UTF_8) })
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val rings = o.getJSONArray("rings")
                Prefecture(
                    o.getString("code"), o.getString("ja"), o.getString("en"),
                    List(rings.length()) { r ->
                        val ring = rings.getJSONArray(r)
                        List(ring.length()) { p -> ring.getJSONArray(p).let { it.getDouble(0) to it.getDouble(1) } }
                    },
                )
            }
        }.getOrDefault(emptyList()).also { cache = it }
    }
}
