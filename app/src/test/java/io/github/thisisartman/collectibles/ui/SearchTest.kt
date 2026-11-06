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
}
