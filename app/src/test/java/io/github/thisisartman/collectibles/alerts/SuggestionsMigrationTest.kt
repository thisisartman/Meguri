package io.github.thisisartman.collectibles.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsMigrationTest {
    @Test fun `photo hints switched off keeps suggestions off`() {
        assertFalse(SuggestionsMigration.startsOn(photoHintsSet = true, photoHintsOn = false))
    }

    @Test fun `photo hints on, or never set, starts suggestions on`() {
        assertTrue(SuggestionsMigration.startsOn(photoHintsSet = true, photoHintsOn = true))
        assertTrue(SuggestionsMigration.startsOn(photoHintsSet = false, photoHintsOn = false))
    }
}
