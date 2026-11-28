package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipLogTest {
    @Test fun `a tip shows the first time only`() {
        var log = TipLog()
        assertTrue(log.canShow(TipId.PLUS))
        log = log.open(TipId.PLUS)
        assertEquals(TipId.PLUS, log.showing)
        log = log.close(TipId.PLUS)
        assertFalse(log.canShow(TipId.PLUS))
        assertEquals(log, log.open(TipId.PLUS))
    }

    @Test fun `one tip at a time`() {
        var log = TipLog().open(TipId.BOOK)
        assertFalse(log.canShow(TipId.PLUS))
        assertEquals(TipId.BOOK, log.open(TipId.PLUS).showing)
        log = log.close(TipId.BOOK)
        assertNull(log.showing)
        assertTrue(log.canShow(TipId.PLUS))
    }

    @Test fun `closing one tip leaves another that is up`() {
        val log = TipLog().open(TipId.TRIP).close(TipId.CHECK)
        assertEquals(TipId.TRIP, log.showing)
        assertEquals(setOf(TipId.CHECK), log.seen)
    }

    @Test fun `show tips again brings every tip back`() {
        val log = TipLog(setOf(TipId.PLUS, TipId.PLACE, TipId.BACKUP)).reset()
        assertTrue(log.canShow(TipId.PLUS))
        assertTrue(log.canShow(TipId.BACKUP))
    }
}
