package io.github.thisisartman.collectibles.ui

/** The Nearby sheet's grid sums (#225). Pure, so they are unit tested. */
object SheetLayout {
    const val MIN_COLUMNS = 3
    const val MAX_COLUMNS = 6
    /** About the old middle tile size on a phone. */
    const val DEFAULT_COLUMNS = 4

    /** − and + on Tile size: one column fewer or more, 3 to 6 per row. */
    fun step(columns: Int, delta: Int): Int = (columns + delta).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * How many of a section's [total] tiles show before its "more" tile: as many whole rows as fit in [budgetPx]
     * (about half the screen), so the next section's title stays in view. When some are left over, the last slot is
     * the more tile. At least one row; everything when it all fits. Before the first row is measured ([rowPx] 0),
     * three rows.
     */
    fun sectionCap(total: Int, columns: Int, rowPx: Int, budgetPx: Int): Int {
        if (total <= 0) return 0
        val cols = columns.coerceAtLeast(1)
        val rows = if (rowPx <= 0) 3 else (budgetPx / rowPx).coerceAtLeast(1)
        val slots = rows * cols
        return if (total <= slots) total else (slots - 1).coerceAtLeast(1)
    }
}
