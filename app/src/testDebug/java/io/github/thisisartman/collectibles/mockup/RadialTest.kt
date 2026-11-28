package io.github.thisisartman.collectibles.mockup

import org.junit.Assert.assertEquals
import org.junit.Test

/** The mockups live in dev builds only, so this test runs with the debug unit tests. */
class RadialTest {
    // A 1080 px wide screen, ring radius 240 px, buttons 144 px
    private val w = 1080f
    private val r = 240f
    private val b = 144f

    @Test fun `left edge fans right, right edge left, middle up, near the top down`() {
        assertEquals(Fan.RIGHT, Radial.fan(120f, 1200f, w, r, b))
        assertEquals(Fan.LEFT, Radial.fan(960f, 1200f, w, r, b))
        assertEquals(Fan.UP, Radial.fan(540f, 1200f, w, r, b))
        assertEquals(Fan.DOWN, Radial.fan(540f, 200f, w, r, b))
    }

    @Test fun `three buttons sit 45 degrees apart around the fan's direction`() {
        assertEquals(listOf(-45.0, 0.0, 45.0), Radial.angles(Fan.RIGHT, 3))
        assertEquals(listOf(135.0, 180.0, 225.0), Radial.angles(Fan.LEFT, 3))
        assertEquals(listOf(-135.0, -90.0, -45.0), Radial.angles(Fan.UP, 3))
        assertEquals(listOf(-112.5, -67.5), Radial.angles(Fan.UP, 2))
        assertEquals(listOf(90.0), Radial.angles(Fan.DOWN, 1))
    }

    @Test fun `positions stay inside the screen`() {
        val (x, y) = Radial.position(60f, 1200f, r, 180.0, 72f, w, 2400f)
        assertEquals(72f, x, 0.01f)
        assertEquals(1200f, y, 0.01f)
    }

    @Test fun `the finger picks the nearest button by angle within the ring`() {
        val angles = Radial.angles(Fan.UP, 3)
        // Straight up from the centre, on the ring: the middle one
        assertEquals(1, Radial.hit(540f, 960f, 540f, 1200f, angles, r, 100f))
        // Up and to the left, a little inside the ring: the first
        assertEquals(0, Radial.hit(540f - 150f, 1200f - 150f, 540f, 1200f, angles, r, 100f))
        // Up and to the right, a little outside: the last
        assertEquals(2, Radial.hit(540f + 210f, 1200f - 210f, 540f, 1200f, angles, r, 100f))
    }

    @Test fun `on the tile, beyond the ring or away from every button is none`() {
        val angles = Radial.angles(Fan.UP, 3)
        assertEquals(-1, Radial.hit(560f, 1190f, 540f, 1200f, angles, r, 100f))
        assertEquals(-1, Radial.hit(540f, 800f, 540f, 1200f, angles, r, 100f))
        // Straight down, on the ring, where no button is
        assertEquals(-1, Radial.hit(540f, 1440f, 540f, 1200f, angles, r, 100f))
    }

    @Test fun `angles wrap around`() {
        val angles = Radial.angles(Fan.LEFT, 3)
        // Up and to the left is -135 degrees, the same as the third button's 225
        assertEquals(2, Radial.hit(540f - 170f, 1200f - 170f, 540f, 1200f, angles, r, 100f))
    }
}
