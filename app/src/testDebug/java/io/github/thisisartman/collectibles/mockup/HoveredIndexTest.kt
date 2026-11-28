package io.github.thisisartman.collectibles.mockup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** The mockups live in dev builds only, so this test runs with the debug unit tests. */
class HoveredIndexTest {
    // Three buttons stacked beside a tile: Stamp, Ekitag, Card, 48 px tall with 6 px between
    private val bounds = mapOf(
        0 to Rect(300f, 100f, 400f, 148f),
        1 to Rect(300f, 154f, 400f, 202f),
        2 to Rect(300f, 208f, 400f, 256f),
    )

    @Test fun `the finger over a button picks it`() {
        assertEquals(0, hoveredIndex(Offset(350f, 120f), bounds))
        assertEquals(1, hoveredIndex(Offset(310f, 180f), bounds))
        assertEquals(2, hoveredIndex(Offset(399f, 255f), bounds))
    }

    @Test fun `between buttons or off the panel is none`() {
        assertEquals(-1, hoveredIndex(Offset(350f, 151f), bounds))
        assertEquals(-1, hoveredIndex(Offset(250f, 120f), bounds))
        assertEquals(-1, hoveredIndex(Offset(350f, 120f), emptyMap()))
    }
}
