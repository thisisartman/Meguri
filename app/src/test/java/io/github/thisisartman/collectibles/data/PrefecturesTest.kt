package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which prefecture you are in, and the region list's order (#81). */
class PrefecturesTest {
    private fun square(code: String, ja: String, en: String, lat: Double, lng: Double) =
        Prefecture(code, ja, en, listOf(listOf(lng - 1 to lat - 1, lng + 1 to lat - 1, lng + 1 to lat + 1, lng - 1 to lat + 1)))

    private val niigata = square("JP-15", "新潟県", "Niigata", 37.8, 139.0)
    private val tokyo = square("JP-13", "東京都", "Tokyo", 35.7, 139.7 + 3) // kept apart from Niigata's square
    private val saitama = square("JP-11", "埼玉県", "Saitama", 35.9 - 3, 139.6)
    private val kyoto = square("JP-26", "京都府", "Kyoto", 35.0, 135.7)
    private val all = listOf(niigata, tokyo, saitama, kyoto)

    @Test fun pointInsideAPrefecture() {
        assertEquals("新潟県", Prefectures.at(all, 37.8007, 139.118)?.ja)
        assertNull(Prefectures.at(all, 20.0, 120.0))
    }

    /** 0.5.0: the owner's Hakkaisan Place, added by hand, had no prefecture and sat under "No prefecture". */
    @Test fun itemsYouAddedGetTheirPrefectureFromTheirPosition() {
        fun item(id: String, origin: Origin, pref: String?, lat: Double?, lng: Double?) =
            Item(id = id, type = ItemType.PLACE, nameJa = id, origin = origin, prefecture = pref, lat = lat, lng = lng, createdAt = 1, updatedAt = 1)
        val items = listOf(
            item("hakkaisan", Origin.USER, null, 37.07, 138.9),   // inside Niigata's square
            item("blank", Origin.USER, " ", 35.7, 142.7),         // blank counts as missing; Tokyo's square
            item("set", Origin.USER, "群馬県", 37.8, 139.0),       // already has one: left alone
            item("nowhere", Origin.USER, null, 20.0, 120.0),      // outside every outline
            item("noPosition", Origin.USER, null, null, null),
            item("catalogue", Origin.CSV_IMPORT, null, 37.8, 139.0), // catalogue rows are the catalogue's business
        )
        assertEquals(listOf("hakkaisan" to "新潟県", "blank" to "東京都"), Prefectures.missing(items, all))
    }

    @Test fun yourPrefectureFirstThenPopularOrder() {
        val (mine, rest) = Prefectures.ordered(all, setOf("新潟県", "東京都", "埼玉県"), 37.8007 to 139.118)
        assertEquals("新潟県", mine?.ja)
        // Kyoto has no catalogue, so it isn't listed; Tokyo comes before Saitama in the popular order
        assertEquals(listOf("東京都", "埼玉県"), rest.map { it.ja })
    }

    @Test fun withoutAPositionEverythingIsInPopularOrder() {
        val (mine, rest) = Prefectures.ordered(all, setOf("新潟県", "東京都", "埼玉県"), null)
        assertNull(mine)
        assertEquals(listOf("東京都", "新潟県", "埼玉県"), rest.map { it.ja })
    }

    @Test fun mainRingLeavesOutTheIslands() {
        val main = listOf(139.0 to 35.5, 140.0 to 35.5, 140.0 to 36.0, 139.0 to 36.0)
        val island = listOf(142.1 to 27.0, 142.2 to 27.0, 142.2 to 27.1, 142.1 to 27.1)
        assertEquals(main, Prefecture("JP-13", "東京都", "Tokyo", listOf(island, main)).mainRing())
    }
}
