package io.github.thisisartman.collectibles.photo

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoHintsTest {
    private fun item(id: String, lat: Double?, lng: Double?) =
        Item(id = id, type = ItemType.MANHOLE_CARD, nameJa = id, lat = lat, lng = lng, origin = Origin.CSV_IMPORT, createdAt = 0, updatedAt = 0)

    @Test fun nearestWithin300m() {
        val a = item("a", 37.0, 139.0)
        val b = item("b", 37.0015, 139.0) // about 170 m north
        val far = item("far", 37.01, 139.0) // about 1.1 km
        val hit = PhotoHints.match(37.0016, 139.0, listOf(a, b, far))!!
        assertEquals("b", hit.first.id)
        assertTrue(hit.second < 20)
    }

    @Test fun nothingNearMeansNoHint() {
        assertNull(PhotoHints.match(37.0, 139.0, listOf(item("far", 37.01, 139.0), item("noplace", null, null))))
    }

    @Test fun onlyTheCamerasOwnPhotos() {
        assertTrue(PhotoHints.isCameraPath("DCIM/Camera/"))
        assertFalse(PhotoHints.isCameraPath("Pictures/Screenshots/"))
        assertFalse(PhotoHints.isCameraPath("Download/"))
        assertFalse(PhotoHints.isCameraPath("DCIM/CollectiblesTracker/"))
        assertFalse(PhotoHints.isCameraPath(null))
    }
}
