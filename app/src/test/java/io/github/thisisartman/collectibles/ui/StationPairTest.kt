package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner, 2026-11-10: a station's stamp and its Ekitag are one station page, whichever of the two you open. */
class StationPairTest {
    private fun row(id: String, type: ItemType, lat: Double, lng: Double) = ItemRow(
        Item(id = id, type = type, nameJa = id, origin = Origin.CSV_IMPORT, lat = lat, lng = lng, createdAt = 1, updatedAt = 1),
        findCount = 0, lastAt = null, lastBeforeApp = null, photoUri = null,
    )

    private val rows = listOf(
        row("urasa-stamp", ItemType.EKI_STAMP, 37.16695, 138.92145),
        row("urasa-tag", ItemType.EKITAG, 37.16695, 138.92145),
        row("yairo-stamp", ItemType.EKI_STAMP, 37.12, 138.91),
        row("lone-tag", ItemType.EKITAG, 37.5, 139.0),
        row("card", ItemType.MANHOLE_CARD, 37.16695, 138.92145),
    )

    @Test fun theStampOpensWithItsEkitag() =
        assertEquals("urasa-stamp" to "urasa-tag", stationPair(rows, "urasa-stamp")!!.let { it.first.item.id to it.second?.item?.id })

    @Test fun theEkitagOpensItsStation() =
        assertEquals("urasa-stamp" to "urasa-tag", stationPair(rows, "urasa-tag")!!.let { it.first.item.id to it.second?.item?.id })

    @Test fun aStationWithoutAnEkitagIsJustItsStamp() =
        assertEquals("yairo-stamp" to null, stationPair(rows, "yairo-stamp")!!.let { it.first.item.id to it.second?.item?.id })

    @Test fun otherThingsAndLoneEkitagsKeepTheirOwnPage() {
        assertNull(stationPair(rows, "card"))
        assertNull(stationPair(rows, "lone-tag"))
    }
}
