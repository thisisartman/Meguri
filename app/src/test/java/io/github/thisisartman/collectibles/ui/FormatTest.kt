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

    @Test fun generatedStationNotesReadInTheAppsVoice() {
        for (generated in listOf(
            "Paper stamp not confirmed at this station. Map data © OpenStreetMap contributors.",
            "Station position from OpenStreetMap (ODbL). Whether this station has a paper stamp is not checked.",
        )) {
            assertEquals("Not every station has a stamp. If you can't see one, ask at the ticket gate.", localNote(generated, japanese = false))
            assertEquals("スタンプがない駅もあります。見当たらないときは改札で聞いてみてください。", localNote(generated, japanese = true))
        }
        assertEquals("One card per person.", localNote("One card per person.", japanese = true))
    }
}
