package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The rail graph built from the catalogue that ships in the app, keyed the way the import keys stations. */
class BundledRailTest {
    private val rows = Csv.parseWithHeader(File("src/main/assets/catalogue/trip-niigata.csv").readText()).map { CatalogRow.from(it).getOrThrow() }
    private val graph = RailGraph(
        rows.filter { it.lineId != null && it.lineSeq != null }.groupBy { it.lineId!! }
            .mapValues { (_, l) -> l.sortedBy { it.lineSeq }.map { it.catalogKey } },
    )
    private fun key(name: String) = "EKI_STAMP:$name"

    @Test fun eachStationIsOneKeyAcrossLines() {
        // Transfer stations must be one item, or changes of train silently break
        listOf("宮内駅", "東三条駅", "吉田駅", "新潟駅", "長岡駅", "浦佐駅").forEach { assertTrue(it, graph.has(key(it))) }
        val keys = rows.filter { it.type == ItemType.EKI_STAMP }.map { it.catalogKey }.toSet()
        // 92 stations before the Shinkansen (96 rows, four of them a second line's row) plus Jomo-Kogen
        assertEquals(93, keys.size)
    }

    @Test fun damCardsFromTheMlitList() {
        // Niigata's 33 dams on the MLIT list of 2026-01-16, less Okumiomote (not handed out: road closed)
        val dams = rows.filter { it.type == ItemType.DAM_CARD }
        assertEquals(32, dams.size)
        assertEquals(32, dams.map { it.catalogKey }.toSet().size)
        assertTrue(dams.none { it.nameJa == "奥三面ダム" })
        assertTrue(dams.all { it.prefecture == "新潟県" && it.sourceUrl!!.startsWith("https://www.mlit.go.jp/") })
        assertTrue(dams.all { "国土交通省" in it.notes.orEmpty() })
        assertEquals(30, dams.count { it.lat != null && it.lng != null })
    }

    @Test fun urasaToNiigataRidesTheShinkansen() {
        val r = graph.route(key("浦佐駅"), key("新潟駅"))!!
        assertEquals(listOf(RailGraph.SHINKANSEN), r.map { it.lineId })
        assertEquals(listOf("浦佐駅", "長岡駅", "燕三条駅", "新潟駅"), r.single().stations.map { it.removePrefix("EKI_STAMP:") })
    }

    @Test fun localLinesOnlyChangeAtMiyauchi() {
        val r = graph.route(key("浦佐駅"), key("新潟駅"), avoid = setOf(RailGraph.SHINKANSEN))!!
        assertEquals(listOf("JR_JOETSU", "JR_SHINETSU"), r.map { it.lineId })
        assertEquals(key("宮内駅"), r[0].stations.last())
    }

    @Test fun yahikoToNiigataFindsAWay() {
        val r = graph.route(key("弥彦駅"), key("新潟駅"), avoid = setOf(RailGraph.SHINKANSEN))
        assertNotNull(r)
        assertEquals(key("新潟駅"), r!!.last().stations.last())
    }
}
