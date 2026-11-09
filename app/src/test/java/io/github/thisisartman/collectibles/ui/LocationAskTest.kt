package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAskTest {
    @Test fun asksOnceWhenNotAllowedAndNeverAsked() {
        assertTrue(LocationAccess.askAtStart(granted = false, askedThisRun = false, declined = false, blocked = false))
    }

    @Test fun notTwiceInOneRun() {
        assertFalse(LocationAccess.askAtStart(granted = false, askedThisRun = true, declined = false, blocked = false))
    }

    @Test fun notAfterANoOrWhenBlockedOrAllowed() {
        assertFalse(LocationAccess.askAtStart(granted = false, askedThisRun = false, declined = true, blocked = false))
        assertFalse(LocationAccess.askAtStart(granted = false, askedThisRun = false, declined = false, blocked = true))
        assertFalse(LocationAccess.askAtStart(granted = true, askedThisRun = false, declined = false, blocked = false))
    }
}
