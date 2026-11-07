package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The onboarding's region counts are kept between starts as a small string (#81). */
class RegionCountsTest {
    @Test fun countsSurviveTheRoundTrip() {
        val c = mapOf(
            "新潟県" to mapOf(ItemType.EKI_STAMP to 76, ItemType.MANHOLE_CARD to 48, ItemType.DAM_CARD to 32),
            "東京都" to mapOf(ItemType.MANHOLE_CARD to 101, ItemType.GOSHUIN to 1),
        )
        assertEquals(c, decodeCounts(encodeCounts(c)))
    }

    @Test fun nothingKeptReadsAsNothing() {
        assertEquals(emptyMap<String, Map<ItemType, Int>>(), decodeCounts(""))
    }
}
