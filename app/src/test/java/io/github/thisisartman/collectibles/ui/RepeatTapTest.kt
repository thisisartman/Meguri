package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatTapTest {
    @Test fun aSecondTapOnTheSameItemSoonAfterIsIgnored() {
        assertTrue(MainViewModel.isRepeatTap("a" to 10_000L, "a", 10_500L))
    }

    @Test fun anotherItemOrALaterTapCounts() {
        assertFalse(MainViewModel.isRepeatTap("a" to 10_000L, "b", 10_500L))
        assertFalse(MainViewModel.isRepeatTap("a" to 10_000L, "a", 12_000L))
        assertFalse(MainViewModel.isRepeatTap(null, "a", 10_500L))
    }
}
