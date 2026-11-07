package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Test

/** The onboarding picker's tiles (#81): one per place, with everything there. */
class PickerPlacesTest {
    private fun row(id: String, type: ItemType, ja: String, lat: Double?, lng: Double?) = ItemRow(
        Item(id = id, type = type, nameJa = ja, nameRomaji = ja, origin = Origin.CSV_IMPORT, lat = lat, lng = lng, createdAt = 1, updatedAt = 1),
        findCount = 0, lastAt = null, lastBeforeApp = null, photoUri = null,
    )

    @Test fun aStationsStampAndEkitagAreOnePlace() {
        val places = pickerPlaces(
            listOf(
                row("tag", ItemType.EKITAG, "弥彦駅", 37.7040, 138.8290),
                row("stamp", ItemType.EKI_STAMP, "弥彦駅", 37.7041, 138.8291),
                row("card", ItemType.MANHOLE_CARD, "燕市 E001", 37.6731, 138.8825),
            ),
        )
        assertEquals(2, places.size)
        // Station stamp first on the card, by kind
        assertEquals(listOf("stamp", "tag"), places.first { it.rows.size == 2 }.rows.map { it.item.id })
    }

    @Test fun thingsWithoutAPositionAreEachTheirOwnPlace() {
        assertEquals(2, pickerPlaces(listOf(row("a", ItemType.GOSHUIN, "豪徳寺", null, null), row("b", ItemType.GOSHUIN, "豪徳寺", null, null))).size)
    }

    @Test fun aToZOrNearest() {
        val places = pickerPlaces(listOf(row("y", ItemType.EKI_STAMP, "Yoshida", 37.68, 138.88), row("a", ItemType.EKI_STAMP, "Aoyama", 37.90, 139.0)))
        assertEquals(listOf("a", "y"), sortPlaces(places, false, false) { null }.map { it.first.item.id })
        val far = mapOf("a" to 30_000.0, "y" to 2_000.0)
        assertEquals(listOf("y", "a"), sortPlaces(places, false, true) { far[it.item.id] }.map { it.first.item.id })
    }
}
