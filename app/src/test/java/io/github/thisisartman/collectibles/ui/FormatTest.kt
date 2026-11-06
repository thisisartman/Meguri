package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun standingAtThePlaceReadsHere() {
        assertEquals(R.string.here to "", formatDistance(0.0))
        assertEquals(R.string.here to "", formatDistance(24.9))
    }

    @Test fun metresRoundToTensFromHereOut() {
        assertEquals(R.string.m to "30", formatDistance(25.0))
        assertEquals(R.string.m to "990", formatDistance(994.0))
    }

    @Test fun kilometresKeepOneDecimalUnderTen() {
        assertEquals(R.string.km to "1.6", formatDistance(1_600.0))
        assertEquals(R.string.km to "11", formatDistance(11_200.0))
    }

    @Test fun stationNotesInJapaneseModeOnly() {
        val en = "Paper stamp not confirmed at this station. Map data © OpenStreetMap contributors."
        assertEquals("この駅の紙のスタンプは未確認です。地図データ © OpenStreetMap contributors", localNote(en, japanese = true))
        assertEquals(en, localNote(en, japanese = false))
        assertEquals("One card per person.", localNote("One card per person.", japanese = true))
    }
}
