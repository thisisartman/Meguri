package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grouping shared photos by place and guessing what they show (#80). */
class ShareGroupingTest {
    // Real places: Niitsu Station, the Niitsu Railway Museum (about 2.1 km away), Tsubame city hall, Yahiko Shrine
    private val niitsu = 37.8007 to 139.1180
    private val museum = 37.8018 to 139.1418
    private val tsubame = 37.6731 to 138.8825
    private val yahiko = 37.7056 to 138.8261

    private fun photo(name: String, at: Pair<Double, Double>?) = SharedPhoto(name, at?.first, at?.second)
    private fun item(id: String, type: ItemType, at: Pair<Double, Double>, finds: Int = 0) = ItemRow(
        Item(id = id, type = type, nameJa = id, origin = Origin.CSV_IMPORT, lat = at.first, lng = at.second, createdAt = 1, updatedAt = 1),
        findCount = finds, lastAt = null, photoUri = null,
    )

    @Test fun photosAtOnePlaceGroupAndOthersSplit() {
        val g = ShareGrouping.byPlace(
            listOf(
                photo("lid", tsubame),
                photo("stamp", niitsu),
                photo("niitsu-lid", niitsu.first + 0.001 to niitsu.second), // about 110 m from the station
                photo("goshuin", yahiko),
            ),
        )
        assertEquals(listOf(listOf("lid"), listOf("stamp", "niitsu-lid"), listOf("goshuin")), g.map { grp -> grp.photos.map { it.uri } })
    }

    @Test fun aWalkRoundOnePlaceStaysOneGroup() {
        // Three photos 200 m apart in a line: the ends are 400 m apart, but each is near the next
        val g = ShareGrouping.byPlace(
            listOf(photo("a", niitsu), photo("b", niitsu.first + 0.0018 to niitsu.second), photo("c", niitsu.first + 0.0036 to niitsu.second)),
        )
        assertEquals(1, g.size)
    }

    @Test fun aPhotoBetweenTwoGroupsJoinsThem() {
        val g = ShareGrouping.byPlace(
            listOf(photo("a", niitsu), photo("c", niitsu.first + 0.0036 to niitsu.second), photo("b", niitsu.first + 0.0018 to niitsu.second)),
        )
        assertEquals(1, g.size)
        assertEquals(listOf("a", "c", "b"), g.single().photos.map { it.uri })
    }

    @Test fun photosWithoutALocationGoLastInOneGroup() {
        val g = ShareGrouping.byPlace(listOf(photo("x", null), photo("a", niitsu), photo("y", null)))
        assertEquals(2, g.size)
        assertEquals(listOf("x", "y"), g.last().photos.map { it.uri })
        assertNull(g.last().centre)
    }

    @Test fun suggestionsAreNearestFirstAndRespectKind() {
        val rows = listOf(
            item("station", ItemType.EKI_STAMP, niitsu),
            item("card", ItemType.MANHOLE_CARD, museum),
            item("far", ItemType.MANHOLE_CARD, tsubame),
        )
        assertEquals("2 km by default leaves the museum out", listOf("station"), ShareGrouping.suggestions(rows, niitsu).map { it.first.item.id })
        val near = ShareGrouping.suggestions(rows, niitsu, maxM = 3_000.0)
        assertEquals(listOf("station", "card"), near.map { it.first.item.id })
        val cards = ShareGrouping.suggestions(rows, niitsu, types = setOf(ItemType.MANHOLE_CARD), maxM = 3_000.0)
        assertEquals(listOf("card"), cards.map { it.first.item.id })
    }

    @Test fun noLocationFallsBackToWhereYouAre() {
        val rows = listOf(item("goshuin", ItemType.GOSHUIN, yahiko))
        assertTrue(ShareGrouping.suggestions(rows, null).isEmpty())
        assertEquals("goshuin", ShareGrouping.suggestions(rows, null, fallback = yahiko).single().first.item.id)
    }

    /** S23 run, 2026-11-10: a scan has no position; 2 km around you held nothing ("Nothing here. Try a search"). */
    @Test fun nearYouReachesPastTwoKilometres() {
        val rows = listOf(item("station", ItemType.EKI_STAMP, niitsu), item("far", ItemType.MANHOLE_CARD, tsubame))
        // From the museum the Niitsu station is between 2 and 3 km away; Tsubame much further, still within 50 km
        assertEquals(listOf("station", "far"), ShareGrouping.suggestions(rows, null, fallback = museum).map { it.first.item.id })
    }

    @Test fun atOneSpotTheStampComesBeforeItsEkitag() {
        // The Ekitag row sits on the station's own position (2026-11-08 catalogue); listed first, it must still lose
        val rows = listOf(item("tag", ItemType.EKITAG, niitsu), item("stamp", ItemType.EKI_STAMP, niitsu))
        assertEquals(listOf("stamp", "tag"), ShareGrouping.suggestions(rows, niitsu).map { it.first.item.id })
    }

    @Test fun exifTimeUsesItsOffsetElseTheZone() {
        val withOffset = SharedPhotoReader.exifMillis("2026:11:07 14:03:22", "+09:00", java.time.ZoneId.of("UTC"))
        assertEquals(java.time.Instant.parse("2026-11-07T05:03:22Z").toEpochMilli(), withOffset)
        val inZone = SharedPhotoReader.exifMillis("2026:11:07 14:03:22", null, java.time.ZoneId.of("Asia/Tokyo"))
        assertEquals(withOffset, inZone)
        assertNull(SharedPhotoReader.exifMillis("    :  :     :  :  ", null))
        assertNull(SharedPhotoReader.exifMillis(null, "+09:00"))
    }
}
