package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsWorkTest {
    @Test fun nothingMissingSaysTheyAreAllHere() {
        assertEquals(R.string.cards_all_here, SettingsWork.cardsDoneMessage(0, 0))
    }

    @Test fun aDownloadSaysHowManyCameAndHowManyWereNotFound() {
        assertEquals(R.string.cards_done, SettingsWork.cardsDoneMessage(3, 0))
        assertEquals(R.string.cards_done, SettingsWork.cardsDoneMessage(0, 2))
    }
}
