package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetLayoutTest {
    @Test fun `tile size steps stay between three and six per row`() {
        assertEquals(5, SheetLayout.step(4, 1))
        assertEquals(3, SheetLayout.step(4, -1))
        assertEquals(3, SheetLayout.step(3, -1))
        assertEquals(6, SheetLayout.step(6, 1))
        assertEquals(6, SheetLayout.step(9, 0))
    }

    @Test fun `a long section shows the rows that fit and keeps the last slot for more`() {
        // Half of a 2400 px screen, rows of 300 px: four rows of four, one slot for more
        assertEquals(15, SheetLayout.sectionCap(total = 336, columns = 4, rowPx = 300, budgetPx = 1200))
        // Three per row: 12 slots, 11 tiles and more
        assertEquals(11, SheetLayout.sectionCap(total = 336, columns = 3, rowPx = 300, budgetPx = 1200))
    }

    @Test fun `a section that fits shows everything with no more tile`() {
        assertEquals(16, SheetLayout.sectionCap(total = 16, columns = 4, rowPx = 300, budgetPx = 1200))
        assertEquals(5, SheetLayout.sectionCap(total = 5, columns = 4, rowPx = 300, budgetPx = 1200))
    }

    @Test fun `at least one row, even when a row is taller than the budget`() {
        assertEquals(3, SheetLayout.sectionCap(total = 50, columns = 4, rowPx = 900, budgetPx = 600))
    }

    @Test fun `before the first measure three rows show`() {
        assertEquals(11, SheetLayout.sectionCap(total = 50, columns = 4, rowPx = 0, budgetPx = 1200))
        assertEquals(0, SheetLayout.sectionCap(total = 0, columns = 4, rowPx = 300, budgetPx = 1200))
    }
}
