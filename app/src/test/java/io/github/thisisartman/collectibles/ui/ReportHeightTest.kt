package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportHeightTest {
    @Test fun oneFindGivesAShortCardNotAFullStory() {
        val h = reportHeight(910f)
        assertEquals(REPORT_MIN_H, h)
        assertTrue(h < H)
    }

    @Test fun moreContentGrowsTheCardWithRoomForTheFooter() {
        assertEquals(1500 + 230, reportHeight(1500f))
    }

    @Test fun neverTallerThanAStory() {
        assertEquals(H, reportHeight(1900f))
    }
}
