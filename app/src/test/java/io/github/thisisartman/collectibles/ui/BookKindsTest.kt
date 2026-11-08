package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** My book as kinds (B1, 2026-11-08): tiles per kind, collections inside each. */
class BookKindsTest {
    private fun row(id: String, type: ItemType, pref: String? = "新潟県", got: Boolean = false, custom: String? = null, none: Boolean = false) = ItemRow(
        Item(id = id, type = type, nameJa = id, origin = Origin.CSV_IMPORT, prefecture = pref, customTypeId = custom, createdAt = 1, updatedAt = 1,
            noneHereAt = if (none) 1L else null),
        findCount = if (got) 1 else 0, lastAt = if (got) 5L else null, lastBeforeApp = null, photoUri = null,
    )

    private val lines = mapOf("s1" to setOf("echigo"), "s2" to setOf("echigo", "yahiko"))
    private val lineOf = { id: String -> lines[id].orEmpty() }

    private val rows = listOf(
        row("c1", ItemType.MANHOLE_CARD, got = true), row("c2", ItemType.MANHOLE_CARD), row("c3", ItemType.MANHOLE_CARD, pref = "東京都"),
        row("s1", ItemType.EKI_STAMP, got = true), row("s2", ItemType.EKI_STAMP), row("s3", ItemType.EKI_STAMP),
        row("g1", ItemType.GOSHUIN, got = true), row("g2", ItemType.GOSHUIN),
        row("x1", ItemType.CUSTOM, pref = null, got = true, custom = "lids"), row("x2", ItemType.CUSTOM, pref = null, custom = "lids"),
        row("d1", ItemType.DAM_CARD, none = true),
    )

    @Test fun oneTilePerKindInOrderWithYourOwnLast() {
        val k = BookKinds.kinds(rows, lineOf)
        assertEquals(listOf("MANHOLE_CARD", "EKI_STAMP", "GOSHUIN", "custom:lids"), k.map { it.key })
        assertEquals(1 to 3, k[0].collected to k[0].total)
        assertEquals(2, k[0].groups) // two prefectures
        // Goshuin has no fixed list; a kind whose only item was found empty has no tile
        assertNull(k[2].total)
    }

    @Test fun stampsGroupByLineAndAStationOnTwoLinesCountsInBoth() {
        val g = BookKinds.groups("EKI_STAMP", rows, lineOf)
        assertEquals(listOf("l:echigo", "l:yahiko", "l:"), g.map { it.key })
        assertEquals(listOf(2, 1, 1), g.map { it.total })
        assertEquals(1, g[0].collected)
    }

    @Test fun cardsGroupByPrefectureAndYourOwnIsOneGroup() {
        assertEquals(listOf("p:新潟県", "p:東京都"), BookKinds.groups("MANHOLE_CARD", rows, lineOf).map { it.key })
        val own = BookKinds.groups("custom:lids", rows, lineOf).single()
        assertEquals(1 to 2, own.collected to own.total)
    }
}
