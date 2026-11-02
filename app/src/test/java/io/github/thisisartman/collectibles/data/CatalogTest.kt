package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.ui.MainViewModel
import io.github.thisisartman.collectibles.ui.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test
    fun parsesQuotesCommasNewlinesBomAndJapanese() {
        val text = "﻿type,name_ja,notes\r\nGOSHUIN,八海神社,\"朱印 ¥500, \"\"書き置き\"\"\nあり\"\r\n"
        val rows = Csv.parseWithHeader(text)
        assertEquals(1, rows.size)
        assertEquals("八海神社", rows[0]["name_ja"])
        assertEquals("朱印 ¥500, \"書き置き\"\nあり", rows[0]["notes"])
    }

    @Test
    fun escapeRoundTrips() {
        val line = Csv.line("a,b", "say \"hi\"", null, 3)
        assertEquals(listOf("a,b", "say \"hi\"", "", "3"), Csv.parse(line).single())
    }

    @Test
    fun catalogKeyDefaultsPerType() {
        val card = CatalogRow.from(mapOf("type" to "manhole_card", "name_ja" to "長岡市", "code" to "15-202-A001")).getOrThrow()
        assertEquals("MANHOLE_CARD:15-202-A001", card.catalogKey)
        val stn = CatalogRow.from(mapOf("type" to "EKI_STAMP", "name_ja" to "浦佐駅", "line_id" to "JR_JOETSU", "line_seq" to "11")).getOrThrow()
        assertEquals("EKI_STAMP:浦佐駅", stn.catalogKey)
        assertEquals(11, stn.lineSeq)
        assertTrue(CatalogRow.from(mapOf("type" to "MANHOLE_CARD", "name_ja" to "長岡市")).isFailure)
        assertTrue(CatalogRow.from(mapOf("type" to "NOPE", "name_ja" to "x")).isFailure)
    }

    @Test
    fun reimportKeepsIdAndCreationAndDoesNotBlankFields() {
        val first = CatalogRow.from(mapOf("type" to "MANHOLE_CARD", "name_ja" to "長岡市", "code" to "15-202-A001", "place_phone" to "0258-39-2020", "lat" to "37.44")).getOrThrow()
            .toItem(null, "id-1", 1000)
        val again = CatalogRow.from(mapOf("type" to "MANHOLE_CARD", "name_ja" to "長岡市", "code" to "15-202-A001", "name_romaji" to "Nagaoka-shi")).getOrThrow()
            .toItem(first, "id-2", 2000)
        assertEquals("id-1", again.id)
        assertEquals(1000, again.createdAt)
        assertEquals(2000, again.updatedAt)
        assertEquals("0258-39-2020", again.placePhone)
        assertEquals(37.44, again.lat!!, 0.0)
        assertEquals("Nagaoka-shi", again.nameRomaji)
    }

    private fun row(id: String, type: ItemType, pref: String? = null, finds: Int = 0, lastAt: Long? = null) = ItemRow(
        Item(id = id, type = type, nameJa = id, prefecture = pref, origin = Origin.USER, createdAt = 0, updatedAt = 0),
        findCount = finds, lastAt = lastAt, lastBeforeApp = if (finds > 0 && lastAt == null) true else null, photoUri = null,
    )

    @Test
    fun summariesCountBeforeAppAsCollectedAndFollowLines() {
        val rows = listOf(
            row("a", ItemType.MANHOLE_CARD, "新潟県", finds = 1, lastAt = null),
            row("b", ItemType.MANHOLE_CARD, "新潟県"),
            row("s1", ItemType.EKI_STAMP, finds = 1, lastAt = 5),
            row("s2", ItemType.EKI_STAMP),
            row("s3", ItemType.EKI_STAMP),
            row("g", ItemType.GOSHUIN, finds = 2, lastAt = 9),
        )
        val lines = listOf(Line("JR_JOETSU", "上越線", "Jōetsu Line"))
        val ls = listOf(LineStation("JR_JOETSU", "s1", 1), LineStation("JR_JOETSU", "s2", 2))
        val s = MainViewModel.summarise(rows, lines, ls).associateBy { it.key }
        assertEquals(1, s["m:新潟県"]!!.collected)
        assertEquals(2, s["m:新潟県"]!!.total)
        assertEquals("a", s["m:新潟県"]!!.last!!.item.id)
        assertEquals(1, s["l:JR_JOETSU"]!!.collected)
        assertEquals(2, s["l:JR_JOETSU"]!!.total)
        assertEquals(0, s["l:"]!!.collected) // s3 is on no line
        assertNull(s["l:"]!!.total)
        assertEquals(1, s["g"]!!.collected)
    }

    @Test
    fun searchFoldsMacronsAndLongVowels() {
        assertEquals(foldForSearch("Jōetsu"), foldForSearch("joetsu"))
        assertEquals(foldForSearch("Jouetsu"), foldForSearch("JOETSU"))
        assertEquals(foldForSearch("Echigo-Yuzawa"), foldForSearch("echigo yuzawa"))
    }
}
