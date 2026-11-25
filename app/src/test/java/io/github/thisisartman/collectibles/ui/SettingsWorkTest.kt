package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.photo.CardPictures
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsWorkTest {
    @Test fun nothingMissingSaysTheyAreAllHere() {
        assertEquals(R.string.cards_all_here, SettingsWork.cardsDoneMessage(CardPictures.Result(0, 0, 0)))
    }

    @Test fun aFailureSaysToTryAgain() {
        assertEquals(R.string.cards_done, SettingsWork.cardsDoneMessage(CardPictures.Result(3, 0, 1)))
        assertEquals(R.string.cards_done, SettingsWork.cardsDoneMessage(CardPictures.Result(0, 0, 2)))
    }

    @Test fun cardsNotOnTheSiteAreNotAFailure() {
        assertEquals(R.string.cards_done_ok, SettingsWork.cardsDoneMessage(CardPictures.Result(3, 0, 0)))
        assertEquals(R.string.cards_done_ok, SettingsWork.cardsDoneMessage(CardPictures.Result(0, 13, 0)))
    }
}
