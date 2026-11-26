package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.ui.MainViewModel
import io.github.thisisartman.collectibles.ui.Ranked
import io.github.thisisartman.collectibles.ui.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        findCount = finds, lastAt = lastAt, photoUri = null,
    )

    @Test
    fun summariesCountCollectedAndFollowLines() {
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
    fun aroundHereKeepsNearCollectionsNearestFirstAndFoldsTheRest() {
        val rows = listOf(
            row("niigata", ItemType.MANHOLE_CARD, "新潟県"),
            row("tokyo", ItemType.MANHOLE_CARD, "東京都"),
            row("tokyo-had", ItemType.MANHOLE_CARD, "東京都", finds = 1),
            row("s1", ItemType.EKI_STAMP),
            row("done", ItemType.EKI_STAMP, finds = 1, lastAt = 5),
        )
        val lines = listOf(Line("YAHIKO", "弥彦線", "Yahiko Line"), Line("DONE", "済線", "Done Line"))
        val ls = listOf(LineStation("YAHIKO", "s1", 1), LineStation("DONE", "done", 1))
        val s = MainViewModel.summarise(rows, lines, ls)
        val byId = rows.associateBy { it.item.id }
        // Nearest first, as the view model ranks them; the Tokyo card is far away
        val nearest = listOf(Ranked(byId["s1"]!!, 400.0), Ranked(byId["niigata"]!!, 1_200.0), Ranked(byId["tokyo"]!!, 250_000.0))

        val a = MainViewModel.splitAroundHere(s, nearest, located = true)
        assertEquals(listOf("l:YAHIKO", "m:新潟県"), a.near.map { it.summary.key })
        assertEquals("s1", a.near.first().next!!.row.item.id)
        // Tokyo is too far; a finished line has nothing left to get
        assertEquals(setOf("m:東京都", "l:DONE"), a.elsewhere.map { it.key }.toSet())

        // Without a position nothing is "around here": everything is listed as before
        val none = MainViewModel.splitAroundHere(s, nearest, located = false)
        assertEquals(s.size, none.near.size)
        assertTrue(none.elsewhere.isEmpty())

        // Far from everything: the same full list, not an empty "Around here"
        val farAway = MainViewModel.splitAroundHere(s, nearest.map { it.copy(meters = 300_000.0) }, located = true)
        assertEquals(s.size, farAway.near.size)
        assertTrue(farAway.elsewhere.isEmpty())
        assertTrue(!farAway.located)
    }

    @Test
    fun tripCatalogueParsesCompletely() {
        val text = javaClass.classLoader!!.getResource("trip-joetsu-niigata.csv")!!.readText()
        val parsed = Csv.parseWithHeader(text).map { CatalogRow.from(it) }
        assertTrue(parsed.all { it.isSuccess })
        val rows = parsed.map { it.getOrThrow() }
        assertEquals(41, rows.size)
        val stations = rows.filter { it.lineId == "JR_JOETSU" }.sortedBy { it.lineSeq }
        assertEquals((1..35).toList(), stations.map { it.lineSeq })
        assertEquals("浦佐駅", stations[26].nameJa)
        assertEquals(6, rows.count { it.type == ItemType.MANHOLE_CARD })
        assertEquals(rows.size, rows.map { it.catalogKey }.toSet().size)
        assertTrue(rows.all { it.lat != null && it.lng != null })
    }

    @Test
    fun searchFoldsMacronsAndLongVowels() {
        assertEquals(foldForSearch("Jōetsu"), foldForSearch("joetsu"))
        assertEquals(foldForSearch("Jouetsu"), foldForSearch("JOETSU"))
        assertEquals(foldForSearch("Echigo-Yuzawa"), foldForSearch("echigo yuzawa"))
    }

    @Test fun reloadingTheSameRowChangesNothing() {
        val old = Item(id = "a", type = ItemType.EKI_STAMP, catalogKey = "EKI_STAMP:x", nameJa = "吉田駅", origin = Origin.CSV_IMPORT, createdAt = 1, updatedAt = 1000)
        assertFalse(catalogRowChanges(old, old.copy(updatedAt = 5000)))
        assertTrue(catalogRowChanges(old, old.copy(nameRomaji = "Yoshida Station", updatedAt = 5000)))
        assertTrue(catalogRowChanges(null, old))
    }
}

