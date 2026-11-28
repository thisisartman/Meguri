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

    @Test fun `the fan centres on the ring, not on the tile with its name`() {
        // A tile 96 x 140 px whose ring is its top 64 x 64 (plus padding): the centre is the ring's
        val ring = androidx.compose.ui.geometry.Rect(135f, 1668f, 199f, 1732f)
        assertEquals(androidx.compose.ui.geometry.Offset(167f, 1700f), Radial.centreOf(ring))
    }

    @Test fun `edge to edge, the status bar inset is taken off so the fan sits on the real ring`() {
        // The Poco: ring centred at (167, 1760) in the window, the page starting 90 px down under the status bar
        val ring = androidx.compose.ui.geometry.Rect(135f, 1728f, 199f, 1792f)
        val c = Radial.centreOf(ring, androidx.compose.ui.geometry.Offset(0f, 90f))
        assertEquals(androidx.compose.ui.geometry.Offset(167f, 1670f), c)
        // Drawn in the overlay at c, it lands back on the ring in the window
        assertEquals(1760f, c.y + 90f, 2f)
    }

    @Test fun `every button sits at the same distance, symmetric about the open direction`() {
        for (f in Fan.entries) {
            val angles = Radial.angles(f, 3)
            val dists = angles.map { a ->
                val (x, y) = Radial.position(540f, 1200f, r, a, 72f, w, 2400f)
                kotlin.math.hypot((x - 540f).toDouble(), (y - 1200f).toDouble())
            }
            dists.forEach { assertEquals(r.toDouble(), it, 0.5) }
            // The middle one points straight along the fan, the outer two the same step either side
            assertEquals(angles[1] - angles[0], angles[2] - angles[1], 1e-9)
        }
    }

    @Test fun `labels go outward when they fit`() {
        val screen = androidx.compose.ui.geometry.Size(w, 2400f)
        val tl = Radial.labelTopLeft(androidx.compose.ui.geometry.Offset(400f, 1200f), 72f, 0.0, androidx.compose.ui.geometry.Size(200f, 60f), 18f, emptyList(), screen)
        // Right of the button, centred on its height
        assertEquals(400f + 72f + 18f, tl.x, 0.01f)
        assertEquals(1170f, tl.y, 0.01f)
    }

    @Test fun `a label that would leave the screen or cover a neighbour moves across, and never leaves the screen`() {
        val screen = androidx.compose.ui.geometry.Size(w, 2400f)
        val size = androidx.compose.ui.geometry.Size(260f, 60f)
        // A button near the right edge pointing right, its neighbour above it
        val button = androidx.compose.ui.geometry.Offset(980f, 1200f)
        val neighbour = androidx.compose.ui.geometry.Offset(940f, 1000f)
        val tl = Radial.labelTopLeft(button, 72f, 0.0, size, 18f, listOf(neighbour), screen)
        val rect = androidx.compose.ui.geometry.Rect(tl, size)
        assert(rect.left >= 0f && rect.right <= w && rect.top >= 0f && rect.bottom <= 2400f) { "off screen: $rect" }
        assert(!rect.overlaps(androidx.compose.ui.geometry.Rect(neighbour, 72f))) { "over the neighbour: $rect" }
        // Moved below the button, away from the neighbour
        assert(rect.top > button.y) { "not below: $rect" }
    }
}
