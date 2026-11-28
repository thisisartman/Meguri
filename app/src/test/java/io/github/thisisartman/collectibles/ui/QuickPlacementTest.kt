package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPlacementTest {
    // A 1080 x 2400 phone, tiles 250 px wide, the buttons 400 x 300
    private val window = IntSize(1080, 2400)
    private val popup = IntSize(400, 300)
    private fun tile(left: Int, top: Int = 1200) = IntRect(left, top, left + 250, top + 300)

    @Test fun `a tile on the left edge opens to the right`() {
        assertEquals(QuickSide.RIGHT, QuickPlacement.side(tile(20), window, popup))
    }

    @Test fun `a tile on the right edge opens to the left`() {
        assertEquals(QuickSide.LEFT, QuickPlacement.side(tile(1080 - 20 - 250), window, popup))
    }

    @Test fun `a middle tile opens on the side with room`() {
        // 450 px of room on the left, 380 on the right: only the left fits
        assertEquals(QuickSide.LEFT, QuickPlacement.side(tile(450), window, popup))
        // 300 px on the left, 530 on the right
        assertEquals(QuickSide.RIGHT, QuickPlacement.side(tile(300), window, popup))
    }

    @Test fun `with no room on either side it opens below, or above near the bottom`() {
        val wide = IntSize(600, 300)
        assertEquals(QuickSide.BELOW, QuickPlacement.side(tile(415, top = 400), window, wide))
        assertEquals(QuickSide.ABOVE, QuickPlacement.side(tile(415, top = 2000), window, wide))
    }

    @Test fun `the buttons sit beside the tile, centred, and stay in the window`() {
        val t = tile(20)
        assertEquals(IntOffset(270 + 16, 1500 - 150 - 150), QuickPlacement.offset(QuickSide.RIGHT, t, window, popup, 16))
        // Near the top, kept inside
        val high = tile(20, top = 0)
        assertEquals(0, QuickPlacement.offset(QuickSide.RIGHT, high, window, popup, 16).y)
        // Above: over the tile, the gap between
        assertEquals(1200 - 16 - 300, QuickPlacement.offset(QuickSide.ABOVE, t, window, popup, 16).y)
    }
}
