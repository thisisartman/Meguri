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
        // 92 stations before the Shinkansen (96 rows, four of them a second line's row) plus Jomo-Kogen, plus Kamitokoro (opened 2025-03)
        // plus 217 JR East stations in Saitama and Tokyo (#65, 2026-11-08)
        assertEquals(311, keys.size)
    }

    @Test fun tokyoToUrasaRidesTheShinkansenFromTokyo() {
        val r = graph.route(key("東京駅"), key("浦佐駅"))!!
        assertEquals(listOf(RailGraph.SHINKANSEN), r.map { it.lineId })
        assertEquals(listOf("東京駅", "上野駅", "大宮駅", "熊谷駅", "本庄早稲田駅", "高崎駅", "上毛高原駅", "越後湯沢駅", "浦佐駅"), r.single().stations.map { it.removePrefix("EKI_STAMP:") })
    }

    @Test fun shinjukuToKawagoeOnLocalLines() {
        val r = graph.route(key("新宿駅"), key("川越駅"), avoid = setOf(RailGraph.SHINKANSEN))
        assertNotNull(r)
        assertEquals(key("川越駅"), r!!.last().stations.last())
    }

    @Test fun damCardsFromTheMlitList() {
        // Niigata's 33 dams on the MLIT list of 2026-01-16, less Okumiomote (not handed out: road closed)
        // plus Saitama's 12 and Tokyo's 2 (added 2026-11-08), all handed out
        val dams = rows.filter { it.type == ItemType.DAM_CARD }
        assertEquals(mapOf("新潟県" to 32, "埼玉県" to 12, "東京都" to 2), dams.groupingBy { it.prefecture }.eachCount())
        assertEquals(dams.size, dams.map { it.catalogKey }.toSet().size)
        assertTrue(dams.none { it.nameJa == "奥三面ダム" })
        assertTrue(dams.all { it.sourceUrl!!.startsWith("https://www.mlit.go.jp/") && "国土交通省" in it.notes.orEmpty() })
        // 城川 and 山本調整池 (Niigata) have no position found
        assertEquals(dams.size - 2, dams.count { it.lat != null && it.lng != null })
    }

    @Test fun roadsideStationsFromTheMlitList() {
        // MLIT 道の駅 list of 2026-09-04: Niigata 42, Saitama 21, Tokyo 1
        val r = rows.filter { it.type == ItemType.ROADSIDE_STATION }
        assertEquals(mapOf("新潟県" to 42, "埼玉県" to 21, "東京都" to 1), r.groupingBy { it.prefecture }.eachCount())
        assertEquals(r.size, r.map { it.catalogKey }.toSet().size)
        assertTrue(r.all { it.nameJa.startsWith("道の駅") && "国土交通省" in it.notes.orEmpty() })
        // 風の丘米山 and 越後川口 have no position in OSM
        assertEquals(r.size - 2, r.count { it.lat != null && it.lng != null })
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
