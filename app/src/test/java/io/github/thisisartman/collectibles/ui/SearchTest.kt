package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val minami = listOf("Minami-Yoshida Station", "南吉田駅", null)

    @Test fun spacingHyphensAndStationDoNotMatter() {
        for (q in listOf("minamiyoshida", "Minami Yoshida", "minami-yoshida", "MINAMI YOSHIDA STATION")) {
            assertEquals(q, 100, fuzzyScore(q, minami))
        }
    }

    @Test fun japaneseNamesMatch() {
        assertEquals(100, fuzzyScore("南吉田", minami))
        assertEquals(80, fuzzyScore("吉田", minami))
    }

    @Test fun middleOfNameAndLettersInOrder() {
        assertEquals(80, fuzzyScore("yoshida", minami))
        assertEquals(50, fuzzyScore("mnmysd", minami))
    }

    @Test fun typosAndLongVowels() {
        assertTrue(fuzzyScore("minsmi", minami) > 0)          // one typo
        assertTrue(fuzzyScore("tsubme", listOf("Tsubame Station")) > 0)
        assertEquals(100, fuzzyScore("okochi", listOf("Ōkōchi")))
        assertEquals(100, fuzzyScore("tokyo", listOf("Toukyou")))
    }

    @Test fun unrelatedDoesNotMatch() {
        assertEquals(0, fuzzyScore("sanjo", minami))
        assertEquals(0, fuzzyScore("", minami))
    }

    // Owner, 2026-11-09: any part of a code finds the card ("A001" lists every A001 card)
    @Test fun partOfACodeFindsTheCard() {
        assert(fuzzyScore("A001", listOf("15-202-A001")) > 0)
        assert(fuzzyScore("202-a", listOf("15-202-A001")) > 0)
        assert(fuzzyScore("A001", listOf("15-202-B001")) == 0)
    }

    @Test fun fullWidthTypingFinds() = assert(fuzzyScore("Ａ００１", listOf("15-202-A001")) > 0)

    @Test fun katakanaAndHiraganaFindEachOther() {
        assert(fuzzyScore("うらさ", listOf("ウラサ")) > 0)
        assert(fuzzyScore("ウラサ", listOf("うらさ")) > 0)
    }

    @Test fun noTypoAllowanceForKanji() = assertEquals(0, fuzzyScore("新瀉", listOf("新潟駅")))

    @Test fun stationInsideAWordIsKept() = assert(fuzzyScore("stationery", listOf("Stationery shop")) > 0)

    @Test fun rankingIsBestMatchThenNearest() {
        val items = listOf("Urasa" to 3.0, "Kami-Urasa" to 1.0, "Urasa-kita" to 9.0)
        val ranked = rankSearch(items, "ura", { fuzzyScore("ura", listOf(it.first)) }, { it.second }).map { it.first }
        assertEquals(listOf("Urasa", "Urasa-kita", "Kami-Urasa"), ranked)
    }
}
