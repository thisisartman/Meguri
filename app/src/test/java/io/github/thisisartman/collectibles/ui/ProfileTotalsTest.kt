package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** The profile page's totals (#72). */
class ProfileTotalsTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 7, 20, 0, 0) }.timeInMillis
    private val yesterday = now - 24 * 3_600_000L

    private fun row(id: String, type: ItemType, finds: Int, lastAt: Long? = null) = ItemRow(
        Item(id = id, type = type, nameJa = id, origin = Origin.USER, createdAt = 1, updatedAt = 1),
        findCount = finds, lastAt = lastAt, photoUri = null,
    )

    @Test fun findsCountDuplicatesKindsAreDistinctTodayIsToday() {
        val t = profileTotals(
            listOf(
                row("a", ItemType.MANHOLE_CARD, 2, now - 3_600_000L),
                row("b", ItemType.MANHOLE_CARD, 1, yesterday),
                row("c", ItemType.GOSHUIN, 1, now - 60_000L),
                row("d", ItemType.EKI_STAMP, 0),
            ),
            now,
        )
        assertEquals(4, t.finds)
        assertEquals(2, t.kinds)
        assertEquals(2, t.today)
    }
}
