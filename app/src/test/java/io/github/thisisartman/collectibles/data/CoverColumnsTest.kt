package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.ui.pointDirectionsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #203: a card's cover position in catalogue files, reloads, backups and directions. */
class CoverColumnsTest {
    private fun card(extra: Map<String, String> = emptyMap()) =
        CatalogRow.from(mapOf("type" to "MANHOLE_CARD", "name_ja" to "長岡市 A001", "code" to "15-202-A001", "lat" to "37.44", "lng" to "138.85") + extra).getOrThrow()

    @Test fun olderFilesWithoutTheColumnsStillLoad() {
        val row = card()
        assertNull(row.coverLat)
        assertNull(row.coverLng)
        assertEquals(37.44, row.lat!!, 0.0)
    }

    @Test fun coverColumnsAreRead() {
        val row = card(mapOf("cover_lat" to "37.44569", "cover_lng" to "138.86007"))
        assertEquals(37.44569, row.coverLat!!, 0.0)
        assertEquals(138.86007, row.coverLng!!, 0.0)
        val item = row.toItem(null, "i1", 5)
        assertEquals(37.44569, item.coverLat!!, 0.0)
        assertEquals(138.86007, item.coverLng!!, 0.0)
    }

    @Test fun halfAPairOrABadValueLeavesTheCardWithoutACover() {
        assertNull(card(mapOf("cover_lat" to "37.44569")).coverLat)
        assertNull(card(mapOf("cover_lat" to "37.44569", "cover_lng" to "abc")).coverLat)
        assertNull(card(mapOf("cover_lat" to "51.5", "cover_lng" to "-0.1")).coverLat)
        // The card itself still loads
        assertEquals("15-202-A001", card(mapOf("cover_lat" to "x", "cover_lng" to "y")).code)
    }

    @Test fun aReloadWithoutACoverKeepsTheOneYouHad() {
        val had = card().toItem(null, "i1", 1).copy(coverLat = 37.5, coverLng = 138.9)
        val again = card().toItem(had, "other", 2)
        assertEquals(37.5, again.coverLat!!, 0.0)
        assertEquals(138.9, again.coverLng!!, 0.0)
    }

    @Test fun aReloadWithACoverTakesTheCatalogues() {
        val had = card().toItem(null, "i1", 1).copy(coverLat = 37.5, coverLng = 138.9)
        val again = card(mapOf("cover_lat" to "37.44569", "cover_lng" to "138.86007")).toItem(had, "other", 2)
        assertEquals(37.44569, again.coverLat!!, 0.0)
        assertEquals(138.86007, again.coverLng!!, 0.0)
    }

    @Test fun backupCarriesTheCover() {
        val item = card().toItem(null, "i1", 1).copy(coverLat = 37.44569, coverLng = 138.86007)
        val t = CatalogAndBackup.itemsCsv(listOf(item)).trimEnd('\n').split("\n").map { Csv.parse(it).single() }
        assertEquals("37.44569", t[1][t[0].indexOf("cover_lat")])
        assertEquals("138.86007", t[1][t[0].indexOf("cover_lng")])
    }

    @Test fun directionsGoToThePointOnTheCard() {
        val url = pointDirectionsUrl(37.44569, 138.86007, 500.0)
        assertTrue(url, url.contains("destination=37.445690,138.860070"))
        assertTrue(url, url.endsWith("travelmode=walking"))
        assertTrue(pointDirectionsUrl(37.4, 138.8, null).endsWith("travelmode=transit"))
    }
}
