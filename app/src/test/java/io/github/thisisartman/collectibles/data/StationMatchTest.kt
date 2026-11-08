package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMatchTest {
    private val stations = listOf(
        StationMatch.Candidate("urasa", "浦佐駅", "Urasa Station"),
        StationMatch.Candidate("niigata", "新潟駅", "Niigata Station"),
        StationMatch.Candidate("niitsu", "新津駅", "Niitsu Station"),
        StationMatch.Candidate("shirayama", "白山駅", "Hakusan Station"),
        StationMatch.Candidate("yoshida", "吉田駅", "Yoshida Station"),
        StationMatch.Candidate("higashisanjo", "東三条駅", "Higashi-Sanjo Station"),
        StationMatch.Candidate("sanjo", "三条駅", "Sanjo Station"),
    )

    @Test fun theLineNameIsNotTheStation() =
        assertEquals(listOf("浦佐"), StationMatch.tokens("毘沙門堂 上越線 浦佐駅 Joetsu Line Urasa Sta.").take(1))

    @Test fun aClearlyPrintedNameIsAssigned() {
        val m = StationMatch.match("国指定重要無形民俗文化財 裸押合大祭\n上越線 浦佐駅\nJoetsu Line Urasa Sta.", stations)
        assertEquals("urasa", m.first().id)
        assertTrue(m.first().score >= StationMatch.ASSIGN)
    }

    @Test fun aSloganWithoutAStationNameMatchesNothing() =
        assertEquals(emptyList<StationMatch.Match>(), StationMatch.match("歴史文化ただよう湊町の街", stations))

    @Test fun aPartlyReadNameIsOnlySuggested() {
        // 東三条 read as 三条: Sanjo is exact, Higashi-Sanjo is close behind, so it is not assigned on its own
        val m = StationMatch.match("信越本線 三条駅", stations)
        assertEquals("sanjo", m.first().id)
        assertTrue("a near tie is held below assign", m.first().score < StationMatch.ASSIGN)
    }

    @Test fun aMisreadCharacterLandsInTheSuggestBandOrBelow() {
        // 新潟 read as 新瀉: half the name shared
        val m = StationMatch.match("新瀉駅", stations)
        assertTrue(m.isEmpty() || m.first().score < StationMatch.ASSIGN)
    }
}
